package com.app.karaoke.model;

public enum Role {
    USER,
    ADMIN,
    SUPER_ADMIN;

    public boolean atLeast(Role other) {
        return this.ordinal() >= other.ordinal();
    }
}
