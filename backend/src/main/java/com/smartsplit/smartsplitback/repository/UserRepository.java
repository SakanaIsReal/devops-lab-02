package com.smartsplit.smartsplitback.repository;

import com.smartsplit.smartsplitback.model.User;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.Optional;

public interface UserRepository extends JpaRepository<User, Long> {
    Optional<User> findByEmail(String email);  // ใช้ตอน register/login
}