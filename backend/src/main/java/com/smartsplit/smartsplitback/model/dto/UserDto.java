// com.smartsplit.smartsplitback.model.dto.UserDto
package com.smartsplit.smartsplitback.model.dto;

public record UserDto(
        Long id,
        String email,
        String userName,
        String phone,
        String avatarUrl,
        String qrCodeUrl,
        Integer roleCode 
) {}
