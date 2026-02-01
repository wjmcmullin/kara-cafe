package com.app.karaoke.model;

import java.util.HashMap;
import java.util.Map;
import java.util.Objects;

public class AppUser {

    private final String username;
    private final Role role;
    private final boolean superAdmin;
    private String password;
    private final String email;

    public AppUser(String username, Role role, String password) {
        this(username, role, password, role == Role.SUPER_ADMIN, null);
    }

    public AppUser(String username, Role role, String password, boolean superAdmin) {
        this(username, role, password, superAdmin, null);
    }

    public AppUser(String username, Role role, String password, boolean superAdmin, String email) {
        this.username = username;
        this.role = role;
        this.password = password;
        this.superAdmin = superAdmin;
        this.email = email;
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

    public String getEmail() {
        return email;
    }

    public boolean checkPassword(String candidate) {
        return Objects.equals(this.password, candidate);
    }

    public void setPassword(String password) {
        this.password = password;
    }

    public Map<String, Object> toPayload() {
        Map<String, Object> payload = new HashMap<>();
        payload.put("username", username);
        payload.put("role", role.name());
        payload.put("superAdmin", superAdmin);
        payload.put("email", email);
        return payload;
    }
}
