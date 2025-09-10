package com.smartsplit.smartsplitback.model;

import jakarta.persistence.*;
import java.util.Objects;

@Entity
@Table(name = "users",
        indexes = @Index(name = "idx_user_email", columnList = "email", unique = true))
public class User {

    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "user_id")
    private Long id;

    @Column(length = 120, nullable = false, unique = true)
    private String email;

    @Column(name = "user_name", length = 80)
    private String userName;

    @Column(name = "password_hash", nullable = false, length = 100)
    private String passwordHash;

    @Column(length = 30)
    private String phone;

    @Column(name = "avatar_url", length = 500)
    private String avatarUrl;

    @Column(name = "qr_code_url", length = 500)
    private String qrCodeUrl;

    @Column(name = "role", nullable = false)
    private Role role = Role.USER;

    // getters/setters/equals/hashCode
    public Long getId() { return id; } public void setId(Long id) { this.id = id; }
    public String getEmail() { return email; } public void setEmail(String email) { this.email = email; }
    public String getUserName() { return userName; } public void setUserName(String userName) { this.userName = userName; }
    public String getPhone() { return phone; } public void setPhone(String phone) { this.phone = phone; }
    public String getAvatarUrl() { return avatarUrl; } public void setAvatarUrl(String avatarUrl) { this.avatarUrl = avatarUrl; }
    public String getQrCodeUrl() { return qrCodeUrl; } public void setQrCodeUrl(String qrCodeUrl) { this.qrCodeUrl = qrCodeUrl; }

    public String getPasswordHash() { return passwordHash; }
    public void setPasswordHash(String passwordHash) { this.passwordHash = passwordHash; }

    public Role getRole() { return role; }
    public void setRole(Role role) { this.role = role; }

    @Override public boolean equals(Object o){ return o instanceof User u && Objects.equals(id,u.id); }
    @Override public int hashCode(){ return Objects.hashCode(id); }
}
