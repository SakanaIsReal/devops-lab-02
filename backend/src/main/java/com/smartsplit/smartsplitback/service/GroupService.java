package com.smartsplit.smartsplitback.service;

import com.smartsplit.smartsplitback.model.Group;
import com.smartsplit.smartsplitback.repository.GroupRepository;
import org.apache.commons.text.similarity.LevenshteinDistance;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.AbstractMap;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

@Service
@Transactional
public class GroupService {

    private final GroupRepository repo;
    private final LevenshteinDistance distance = new LevenshteinDistance();

    public GroupService(GroupRepository repo) {
        this.repo = repo;
    }

    public List<Group> list() { return repo.findAll(); }

    public List<Group> listByOwner(Long ownerId) { return repo.findByOwner_Id(ownerId); }

    public List<Group> listByMember(Long userId) { return repo.findAllByMemberUserId(userId); }

    public List<Group> searchByMemberAndName(Long userId, String name) {
        return repo.findAllByMemberUserIdAndNameContainingIgnoreCase(userId, name);
    }

    public Group get(Long id) { return repo.findById(id).orElse(null); }
    public Group save(Group g) { return repo.save(g); }
    public void delete(Long id) { repo.deleteById(id); }

    public List<Group> searchMyGroups(Long me, String q) {
        String query = q == null ? "" : q.trim();
        if (query.isEmpty()) {
            return List.of();
        }

        String qLower = query.toLowerCase(Locale.ROOT);
        int qLen = qLower.length();

        List<Group> owned = repo.findByOwner_Id(me);
        List<Group> member = repo.findAllByMemberUserId(me);

        Map<Long, Group> merged = new LinkedHashMap<>();
        for (Group g : owned) {
            merged.put(g.getId(), g);
        }
        for (Group g : member) {
            merged.putIfAbsent(g.getId(), g);
        }
        List<Group> candidates = List.copyOf(merged.values());

        if (candidates.isEmpty()) {
            return List.of();
        }

        if (qLen <= 2) {
            return candidates.stream()
                    .filter(g -> g.getName() != null &&
                            g.getName().toLowerCase(Locale.ROOT).contains(qLower))
                    .limit(20)
                    .toList();
        }

        int maxAllowed;
        if (qLen <= 4) {
            maxAllowed = 1;
        } else if (qLen <= 8) {
            maxAllowed = 2;
        } else {
            maxAllowed = Math.max(3, (int) Math.round(qLen * 0.4));
        }

        return candidates.stream()
                .map(g -> {
                    String nameLower = g.getName() == null
                            ? ""
                            : g.getName().toLowerCase(Locale.ROOT);
                    int d;
                    if (nameLower.contains(qLower) || qLower.contains(nameLower)) {
                        d = 0;
                    } else {
                        d = distanceToName(qLower, nameLower);
                    }
                    return new AbstractMap.SimpleEntry<>(g, d);
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
}
