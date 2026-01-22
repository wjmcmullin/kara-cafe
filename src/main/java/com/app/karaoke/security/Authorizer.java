package com.app.karaoke.security;

import com.app.karaoke.model.AppUser;
import com.app.karaoke.model.Role;

import jakarta.enterprise.context.ApplicationScoped;

@ApplicationScoped
public class Authorizer {

    public boolean canCreateRoom(AppUser user) {
        return user != null && user.getRole().atLeast(Role.ADMIN);
    }

    public boolean canModifyRoom(AppUser user, String owner) {
        return user != null && (user.isSuperAdmin() || owner.equals(user.getUsername()));
    }

    public boolean canRequestSong(AppUser user) {
        return user != null && user.getRole().atLeast(Role.USER);
    }
}
