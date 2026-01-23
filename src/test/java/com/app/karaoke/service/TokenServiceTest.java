package com.app.karaoke.service;

import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import com.app.karaoke.model.AppUser;
import com.app.karaoke.model.Role;

class TokenServiceTest {

    private TokenService tokenService;
    private AppUser user;

    @BeforeEach
    void setup() {
        tokenService = new TokenService();
        user = new AppUser("alice", Role.USER, "pw");
    }

    @Test
    void issueTokenReturnsInfo() {
        String token = tokenService.issueToken(user);

        Optional<TokenService.TokenInfo> info = tokenService.getTokenInfo(token);

        assertThat(info).isPresent();
        assertThat(info.get().getUser().getUsername()).isEqualTo("alice");
        assertThat(info.get().isInviteOnly()).isFalse();
        assertThat(info.get().getRoomId()).isEmpty();
    }

    @Test
    void inviteTokenRestrictsRoom() {
        String token = tokenService.issueInviteToken(user, "room-1");

        Optional<TokenService.TokenInfo> info = tokenService.getTokenInfo(token);

        assertThat(info).isPresent();
        assertThat(info.get().isInviteOnly()).isTrue();
        assertThat(info.get().getRoomId()).hasValue("room-1");
    }

    @Test
    void getTokenInfoReturnsEmptyForUnknownToken() {
        Optional<TokenService.TokenInfo> missing = tokenService.getTokenInfo("nope");
        assertThat(missing).isEmpty();
    }

    @Test
    void revokeTokenRemovesToken() {
        String token = tokenService.issueToken(user);

        assertThat(tokenService.revokeToken(token)).isTrue();
        assertThat(tokenService.getTokenInfo(token)).isEmpty();
    }

    @Test
    void revokeTokenReturnsFalseWhenUnknown() {
        assertThat(tokenService.revokeToken("missing")).isFalse();
    }
}
