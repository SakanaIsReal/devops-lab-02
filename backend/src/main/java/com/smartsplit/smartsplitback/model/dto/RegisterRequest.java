package com.smartsplit.smartsplitback.model.dto;

public record RegisterRequest(
        String email,
        String userName,
        String phone,
        String password,
        String firstName,
        String lastName    
) {}