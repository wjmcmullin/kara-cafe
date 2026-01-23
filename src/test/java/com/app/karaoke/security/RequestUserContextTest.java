package com.app.karaoke.security;

import static org.assertj.core.api.Assertions.assertThat;
import org.junit.jupiter.api.Test;

import com.app.karaoke.model.AppUser;
import com.app.karaoke.model.Role;

class RequestUserContextTest {

    private final RequestUserContext context = new RequestUserContext();

    @Test
    void currentUserCanBeSetAndRead() {
        AppUser user = new AppUser("admin", Role.SUPER_ADMIN, "pw");
        assertThat(context.getCurrentUser()).isNull();

        context.setCurrentUser(user);
        assertThat(context.getCurrentUser()).isSameAs(user);
    }

    @Test
    void setCurrentUserCanClear() {
        context.setCurrentUser(new AppUser("admin", Role.ADMIN, "pw"));
        context.setCurrentUser(null);
        assertThat(context.getCurrentUser()).isNull();
    }
}
