package com.app.karaoke.service;

import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import com.app.karaoke.model.AppUser;
import com.app.karaoke.model.Role;

class UserServiceTest {

    private UserService userService;

    @BeforeEach
    void setup() {
        userService = new UserService();
    }

    @Test
    void authenticateValidUser() {
        Optional<AppUser> user = userService.authenticate("admin", "admin");
        assertThat(user).isPresent();
        assertThat(user.get().getRole()).isEqualTo(Role.SUPER_ADMIN);
    }

    @Test
    void authenticateInvalidPassword() {
        assertThat(userService.authenticate("admin", "wrong")).isEmpty();
    }

    @Test
    void createTemporaryUserSetsRoomAccess() {
        AppUser guest = userService.createTemporaryUser("room-123");
        assertThat(guest.getRoomAccess()).isEqualTo("room-123");
        assertThat(guest.getRole()).isEqualTo(Role.USER);
    }
}
