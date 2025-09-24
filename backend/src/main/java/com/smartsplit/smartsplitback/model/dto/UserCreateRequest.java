package com.smartsplit.smartsplitback.model.dto;


public record UserCreateRequest(
        String email,
        String userName,
        String phone,
        String avatarUrl,
        String qrCodeUrl,
        String password,   // << ต้องมี
        Integer role       // 0=ADMIN, 1=USER (ถ้าต้องการให้กำหนด)
) {}