package com.smartsplit.smartsplitback.model.dto;

public record AuthResponse(
        String accessToken,
        String tokenType,
        Long   userId,
        String email,
        String userName,
        int    role
) {}
