package com.smartsplit.smartsplitback.repository;

import com.smartsplit.smartsplitback.model.User;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.Optional;
import java.util.List;

public interface UserRepository extends JpaRepository<User, Long> {
    Optional<User> findByEmail(String email);  // ใช้ตอน register/login
    List<User> findTop20ByUserNameContainingIgnoreCase(String q);
}