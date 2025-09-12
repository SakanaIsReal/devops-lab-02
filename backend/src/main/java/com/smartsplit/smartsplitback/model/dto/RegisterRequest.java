package com.smartsplit.smartsplitback.model.dto;

public record RegisterRequest(
        String email,
        String password,
        String userName,
        String phone
) {}