package com.app.karaoke.security;

import static org.assertj.core.api.Assertions.assertThat;
import org.junit.jupiter.api.Test;

import com.app.karaoke.model.AppUser;
import com.app.karaoke.model.Role;

class AuthorizerTest {

    private final Authorizer authorizer = new Authorizer();

    @Test
    void canCreateRoomOnlyForAdminOrAbove() {
        assertThat(authorizer.canCreateRoom(new AppUser("alice", Role.SUPER_ADMIN, "pw"))).isTrue();
        assertThat(authorizer.canCreateRoom(new AppUser("bob", Role.ADMIN, "pw"))).isTrue();
        assertThat(authorizer.canCreateRoom(new AppUser("carol", Role.USER, "pw"))).isFalse();
        assertThat(authorizer.canCreateRoom(null)).isFalse();
    }

    @Test
    void canModifyRoomForSuperAdminOrOwner() {
        AppUser owner = new AppUser("owner", Role.ADMIN, "pw");
        AppUser other = new AppUser("other", Role.ADMIN, "pw");
        AppUser superAdmin = new AppUser("super", Role.SUPER_ADMIN, "pw");

        assertThat(authorizer.canModifyRoom(owner, "owner")).isTrue();
        assertThat(authorizer.canModifyRoom(other, "owner")).isFalse();
        assertThat(authorizer.canModifyRoom(superAdmin, "owner")).isTrue();
        assertThat(authorizer.canModifyRoom(null, "owner")).isFalse();
    }

    @Test
    void canRequestSongForAnyLoggedInUserWithAtLeastUserRole() {
        assertThat(authorizer.canRequestSong(new AppUser("user", Role.USER, "pw"))).isTrue();
        assertThat(authorizer.canRequestSong(new AppUser("admin", Role.ADMIN, "pw"))).isTrue();
        //GUEST role is not defined yet.
        //assertThat(authorizer.canRequestSong(new AppUser("guest", Role.GUEST, "pw"))).isFalse();
        assertThat(authorizer.canRequestSong(null)).isFalse();
    }
}
