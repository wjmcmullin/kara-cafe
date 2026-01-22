package com.app.karaoke.service;

import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

import com.app.karaoke.model.AppUser;

import jakarta.enterprise.context.ApplicationScoped;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

@ApplicationScoped
public class TokenService {

    private static final Logger LOG = LogManager.getLogger(TokenService.class);

    private final Map<String, TokenInfo> tokens = new ConcurrentHashMap<>();

    public String issueToken(AppUser user) {
        LOG.debug("issueToken for {}", user.getUsername());
        String token = UUID.randomUUID().toString();
        tokens.put(token, new TokenInfo(user, null, false));
        LOG.info("Token issued for {}", user.getUsername());
        return token;
    }

    public String issueInviteToken(AppUser user, String roomId) {
        LOG.debug("issueInviteToken for {} room {}", user.getUsername(), roomId);
        String token = UUID.randomUUID().toString();
        tokens.put(token, new TokenInfo(user, roomId, true));
        LOG.info("Invite token issued for {} room {}", user.getUsername(), roomId);
        return token;
    }

    public Optional<TokenInfo> getTokenInfo(String token) {
        LOG.debug("getTokenInfo {}", token);
        Optional<TokenInfo> info = Optional.ofNullable(tokens.get(token));
        if (info.isEmpty()) {
            LOG.warn("Token {} not found", token);
        }
        return info;
    }

    public static final class TokenInfo {

        private final AppUser user;
        private final String roomId;
        private final boolean inviteOnly;

        public TokenInfo(AppUser user, String roomId, boolean inviteOnly) {
            this.user = user;
            this.roomId = roomId;
            this.inviteOnly = inviteOnly;
        }

        public AppUser getUser() {
            return user;
        }

        public Optional<String> getRoomId() {
            return Optional.ofNullable(roomId);
        }

        public boolean isInviteOnly() {
            return inviteOnly;
        }
    }
}
