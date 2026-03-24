package com.ech.backend.common.rbac;

public enum AppRole {
    MEMBER(1),
    MANAGER(2),
    ADMIN(3);

    private final int level;

    AppRole(int level) {
        this.level = level;
    }

    public boolean atLeast(AppRole required) {
        return this.level >= required.level;
    }

    public static AppRole parse(String raw) {
        if (raw == null || raw.isBlank()) {
            return null;
        }
        try {
            return AppRole.valueOf(raw.trim().toUpperCase());
        } catch (IllegalArgumentException ignored) {
            return null;
        }
    }
}
