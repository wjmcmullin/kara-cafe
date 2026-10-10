package com.app.karaoke.service;

import java.time.Duration;
import java.time.Instant;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.regex.Pattern;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import com.app.karaoke.model.AppUser;

import jakarta.enterprise.context.ApplicationScoped;

@ApplicationScoped
public class TokenService {

    private static final Logger LOG = LogManager.getLogger(TokenService.class);

    private static final long DEFAULT_TOKEN_TTL_SECONDS = Duration.ofHours(8).toSeconds();
    private static final Pattern PLACEHOLDER_GUEST_PATTERN = Pattern.compile("^(guest-\\d+|pending-guest-\\d+)$", Pattern.CASE_INSENSITIVE);

    private final Map<String, TokenInfo> tokens = new ConcurrentHashMap<>();
    private final long tokenTtlSeconds;

    public TokenService() {
        this.tokenTtlSeconds = resolveTokenTtlSeconds();
    }

    public String issueToken(AppUser user) {
        String token = UUID.randomUUID().toString();
        tokens.put(token, new TokenInfo(user, null, false, tokenTtlSeconds));
        LOG.info("Token issued for {}", user.getUsername());
        return token;
    }

    public String issueInviteToken(AppUser user, String roomId) {
        String token = UUID.randomUUID().toString();
        tokens.put(token, new TokenInfo(user, roomId, true, tokenTtlSeconds));
        LOG.info("Invite token issued for {} room {}", user.getUsername(), roomId);
        return token;
    }

    public Optional<TokenInfo> getTokenInfo(String token) {
        TokenInfo info = tokens.get(token);
        if (info == null) {
            return Optional.empty();
        }

        if (info.isExpired()) {
            tokens.remove(token);
            LOG.info("Expired token removed during validation");
            return Optional.empty();
        }

        return Optional.of(info);
    }

    public String issueGuestInviteToken(String roomId, String guestDisplayName) {
        AppUser guestUser = new AppUser(guestDisplayName, com.app.karaoke.model.Role.USER, "", false, null);
        String token = UUID.randomUUID().toString();
        tokens.put(token, new TokenInfo(guestUser, roomId, true, tokenTtlSeconds));
        LOG.info("Invite token issued for guest {} room {}", guestDisplayName, roomId);
        return token;
    }

    public Optional<Map<String, Object>> renameGuestAndRotateToken(String token, String nickname) {
        Optional<TokenInfo> info = getTokenInfo(token);
        if (info.isEmpty()) {
            return Optional.empty();
        }

        TokenInfo tokenInfo = info.get();
        if (!tokenInfo.isInviteOnly() || tokenInfo.getRoomId().isEmpty()) {
            return Optional.empty();
        }

        String newToken = issueGuestInviteToken(tokenInfo.getRoomId().get(), nickname);
        tokens.remove(token);
        return Optional.of(Map.of(
                "token", newToken,
                "username", nickname,
                "roomId", tokenInfo.getRoomId().get()
        ));
    }

    public boolean isGuestNicknameTakenInRoom(String roomId, String nickname, String currentToken) {
        String requested = nickname == null ? "" : nickname.trim();
        if (requested.isBlank()) {
            return false;
        }

        for (Map.Entry<String, TokenInfo> entry : tokens.entrySet()) {
            if (entry.getKey().equals(currentToken)) {
                continue;
            }

            TokenInfo info = entry.getValue();
            if (info == null || info.isExpired() || !info.isInviteOnly()) {
                continue;
            }

            if (info.getRoomId().isEmpty() || !roomId.equals(info.getRoomId().get())) {
                continue;
            }

            String existingName = info.getUser().getUsername();
            if (isPlaceholderGuestNickname(existingName)) {
                continue;
            }

            if (requested.equalsIgnoreCase(existingName)) {
                return true;
            }
        }

        return false;
    }

    public boolean isPlaceholderGuestNickname(String username) {
        if (username == null || username.isBlank()) {
            return true;
        }
        return PLACEHOLDER_GUEST_PATTERN.matcher(username.trim()).matches();
    }

    public boolean revokeToken(String token) {
        TokenInfo removed = tokens.remove(token);
        if (removed != null) {
            LOG.info("Token revoked for {}", removed.getUser().getUsername());
            return true;
        }
        return false;
    }

    public static final class TokenInfo {

        private final AppUser user;
        private final String roomId;
        private final boolean inviteOnly;
        private final Instant expiresAt;

        public TokenInfo(AppUser user, String roomId, boolean inviteOnly, long tokenTtlSeconds) {
            this.user = user;
            this.roomId = roomId;
            this.inviteOnly = inviteOnly;
            this.expiresAt = Instant.now().plusSeconds(tokenTtlSeconds);
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

        public boolean isExpired() {
            return Instant.now().isAfter(expiresAt);
        }
    }

    private static long resolveTokenTtlSeconds() {
        String ttl = System.getProperty("kara.token.ttl-seconds");
        if (ttl == null || ttl.isBlank()) {
            ttl = System.getenv("KARA_TOKEN_TTL_SECONDS");
        }

        if (ttl == null || ttl.isBlank()) {
            return DEFAULT_TOKEN_TTL_SECONDS;
        }

        try {
            long parsed = Long.parseLong(ttl.trim());
            return parsed > 0 ? parsed : DEFAULT_TOKEN_TTL_SECONDS;
        } catch (NumberFormatException ex) {
            return DEFAULT_TOKEN_TTL_SECONDS;
        }
    }
}
