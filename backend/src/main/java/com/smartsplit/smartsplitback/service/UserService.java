package com.smartsplit.smartsplitback.service;

import com.smartsplit.smartsplitback.model.User;
import com.smartsplit.smartsplitback.repository.UserRepository;
import org.springframework.stereotype.Service;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.transaction.annotation.Transactional;
import com.smartsplit.smartsplitback.model.dto.UserPublicDto;
import org.apache.commons.text.similarity.LevenshteinDistance;

import java.util.AbstractMap;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.Map;

@Service
@Transactional
public class UserService {
    private final UserRepository repo;
    private final PasswordEncoder passwordEncoder;
    private final LevenshteinDistance distance = new LevenshteinDistance();

    public UserService(UserRepository repo, PasswordEncoder passwordEncoder) { 
        this.repo = repo;
        this.passwordEncoder = passwordEncoder;
    }

    public List<User> list(){ return repo.findAll(); }
    public User get(Long id){ return repo.findById(id).orElse(null); }
    public User create(User u){ return repo.save(u); }
    public User update(User u){ return repo.save(u); }
    public void delete(Long id){ repo.deleteById(id); }

    public List<User> searchByName(String q) {
        String query = q == null ? "" : q.trim();
        if (query.isBlank()) {
            return repo.findTop20ByUserNameContainingIgnoreCase("");
        }

        String qLower = query.toLowerCase(Locale.ROOT);
        int qLen = qLower.length();

        if (qLen <= 2) {
            return repo.findTop20ByUserNameContainingIgnoreCase(qLower);
        }

        String rough = qLower.substring(0, 1);
        List<User> candidates = repo.findTop100ByUserNameContainingIgnoreCase(rough);

        int maxAllowed;
        if (qLen <= 4) {
            maxAllowed = 1;
        } else if (qLen <= 8) {
            maxAllowed = 2;
        } else {
            maxAllowed = Math.max(3, (int) Math.round(qLen * 0.4));
        }

        return candidates.stream()
                .map(u -> {
                    String nameLower = u.getUserName().toLowerCase(Locale.ROOT);
                    int d;
                    if (nameLower.contains(qLower) || qLower.contains(nameLower)) {
                        d = 0;
                    } else {
                        d = distanceToName(qLower, nameLower);
                    }
                    return new AbstractMap.SimpleEntry<>(u, d);
                })
                .filter(e -> e.getValue() <= maxAllowed)
                .sorted(Comparator.comparingInt(Map.Entry::getValue))
                .map(Map.Entry::getKey)
                .limit(20)
                .toList();
    }

    private int distanceToName(String query, String name) {
        int qLen = query.length();
        int nLen = name.length();

        int best = distance.apply(query, name);

        int minWindow = Math.max(1, qLen - 2);
        int maxWindow = Math.min(nLen, qLen + 2);

        for (int len = minWindow; len <= maxWindow; len++) {
            for (int i = 0; i + len <= nLen; i++) {
                int d = distance.apply(query, name.substring(i, i + len));
                if (d < best) {
                    best = d;
                    if (best == 0) {
                        return 0;
                    }
                }
            }
        }

        return best;
    }


    public static UserPublicDto toPublicDto(User u) {
        return new UserPublicDto(u.getId(), u.getEmail(), u.getUserName(), u.getPhone(), u.getAvatarUrl());
    }
    public boolean passwordMatches(String raw, String encoded) { return passwordEncoder.matches(raw, encoded); }
    public String encodePassword(String raw) { return passwordEncoder.encode(raw); }
}
