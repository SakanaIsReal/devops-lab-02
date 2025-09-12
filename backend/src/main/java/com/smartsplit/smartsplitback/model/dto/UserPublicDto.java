package com.smartsplit.smartsplitback.model.dto;

public record UserPublicDto(
        Long id,
        String email,
        String userName,
        String phone,
        String avatarUrl
) {}
