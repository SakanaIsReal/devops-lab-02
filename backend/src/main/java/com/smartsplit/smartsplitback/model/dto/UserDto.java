// UserDto.java
package com.smartsplit.smartsplitback.model.dto;

public record UserDto(
        Long id,
        String email,
        String userName,
        String phone,
        String avatarUrl,
        String qrCodeUrl,
        Integer roleCode,
        String firstName,
        String lastName
) {
    public UserDto(Long id,
                   String email,
                   String userName,
                   String phone,
                   String avatarUrl,
                   String qrCodeUrl,
                   Integer roleCode) {
        this(id, email, userName, phone, avatarUrl, qrCodeUrl, roleCode, null, null);
    }

    public UserDto(Long id, String email, String userName, String phone) {
        this(id, email, userName, phone, null, null, null, null, null);
    }
}
