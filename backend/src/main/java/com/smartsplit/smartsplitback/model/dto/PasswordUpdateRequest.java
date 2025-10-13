
package com.smartsplit.smartsplitback.model.dto;

public record PasswordUpdateRequest(
        String currentPassword,
        String newPassword
) {}
