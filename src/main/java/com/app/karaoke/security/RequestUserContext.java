package com.app.karaoke.security;

import com.app.karaoke.model.AppUser;

import jakarta.enterprise.context.RequestScoped;

@RequestScoped
public class RequestUserContext {

    private AppUser currentUser;

    public AppUser getCurrentUser() {
        return currentUser;
    }

    public void setCurrentUser(AppUser currentUser) {
        this.currentUser = currentUser;
    }
}
