package com.app.karaoke.model;

import java.util.Map;
import java.util.Objects;

public class AppUser {

    private final String username;
    private final Role role;
    private final boolean superAdmin;
    private String password;

    public AppUser(String username, Role role, String password) {
        this(username, role, password, role == Role.SUPER_ADMIN);
    }

    public AppUser(String username, Role role, String password, boolean superAdmin) {
        this.username = username;
        this.role = role;
        this.password = password;
        this.superAdmin = superAdmin;
    }

    public String getUsername() {
        return username;
    }

    public Role getRole() {
        return role;
    }

    public boolean isSuperAdmin() {
        return superAdmin;
    }

    public String getPassword() {
        return password;
    }

    public boolean checkPassword(String candidate) {
        return Objects.equals(this.password, candidate);
    }

    public void setPassword(String password) {
        this.password = password;
    }

    public Map<String, Object> toPayload() {
        return Map.of(
                "username", username,
                "role", role.name(),
                "superAdmin", superAdmin
        );
    }
}
