package com.smartsplit.smartsplitback.service;

import com.smartsplit.smartsplitback.model.User;
import com.smartsplit.smartsplitback.repository.UserRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import com.smartsplit.smartsplitback.model.dto.UserPublicDto;
import java.util.List;

@Service
@Transactional
public class UserService {
    private final UserRepository repo;
    public UserService(UserRepository repo){ this.repo = repo; }

    public List<User> list(){ return repo.findAll(); }
    public User get(Long id){ return repo.findById(id).orElse(null); }
    public User create(User u){ return repo.save(u); }
    public User update(User u){ return repo.save(u); }
    public void delete(Long id){ repo.deleteById(id); }

    public List<User> searchByName(String q) {
        return repo.findTop20ByUserNameContainingIgnoreCase(q == null ? "" : q.trim());
    }

    public static UserPublicDto toPublicDto(User u) {
        return new UserPublicDto(u.getId(), u.getEmail(), u.getUserName(), u.getPhone(), u.getAvatarUrl());
    }
}
