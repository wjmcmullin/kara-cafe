package com.app.karaoke.model;

import java.util.Objects;

public class AppUser {

    private final String username;
    private final Role role;
    private final String password;
    private String roomAccess; // roomId this user is allowed to enter (temp users only)

    public AppUser(String username, Role role, String password) {
        this.username = Objects.requireNonNull(username);
        this.role = Objects.requireNonNull(role);
        this.password = Objects.requireNonNull(password);
    }

    public String getUsername() {
        return username;
    }

    public Role getRole() {
        return role;
    }

    public boolean checkPassword(String candidate) {
        return password.equals(candidate);
    }

    public boolean isAdmin() {
        return role.atLeast(Role.ADMIN);
    }

    public boolean isSuperAdmin() {
        return role == Role.SUPER_ADMIN;
    }

    public String getRoomAccess() {
        return roomAccess;
    }

    public void setRoomAccess(String roomAccess) {
        this.roomAccess = roomAccess;
    }
}
