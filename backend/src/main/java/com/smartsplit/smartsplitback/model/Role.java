package com.smartsplit.smartsplitback.model;

public enum Role {
    ADMIN(0),
    USER(1);

    private final int code;
    Role(int code) { this.code = code; }
    public int code() { return code; }

    public static Role fromCode(int code) {
        for (Role r : values()) if (r.code == code) return r;
        throw new IllegalArgumentException("Unknown role code: " + code);
    }

    /** สำหรับ Spring Security (ต้องขึ้นต้นด้วย "ROLE_") */
    public String asSpringRole() {
        return switch (this) {
            case ADMIN -> "ROLE_ADMIN";
            case USER -> "ROLE_USER";
        };
    }
}
