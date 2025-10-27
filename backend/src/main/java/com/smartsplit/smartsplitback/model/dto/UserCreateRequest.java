package com.smartsplit.smartsplitback.model.dto;


public record UserCreateRequest(
        String email,
        String userName,
        String phone,
        String avatarUrl,
        String qrCodeUrl,
        String password,
        Integer role,
        String firstName,
        String lastName
) {}