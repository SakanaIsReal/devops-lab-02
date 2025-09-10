package com.smartsplit.smartsplitback.model.dto;

public record GroupDto(
        Long id,
        Long ownerUserId,
        String name,
        String coverImageUrl
) {}
