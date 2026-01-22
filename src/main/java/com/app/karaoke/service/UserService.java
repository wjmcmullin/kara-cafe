package com.app.karaoke.service;

import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

import com.app.karaoke.model.AppUser;
import com.app.karaoke.model.Role;

import jakarta.enterprise.context.ApplicationScoped;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

@ApplicationScoped
public class UserService {

    private static final Logger LOG = LogManager.getLogger(UserService.class);

    private final Map<String, AppUser> users = new ConcurrentHashMap<>();

    public UserService() {
        users.put("admin", new AppUser("admin", Role.SUPER_ADMIN, "admin"));
        users.put("admin-user", new AppUser("admin-user", Role.ADMIN, "admin"));
        users.put("guest", new AppUser("guest", Role.USER, "guest"));
    }

    public Optional<AppUser> findByUsername(String username) {
        LOG.debug("findByUsername {}", username);
        return Optional.ofNullable(users.get(username));
    }

    public Optional<AppUser> authenticate(String username, String password) {
        LOG.debug("authenticate {}", username);
        return findByUsername(username)
                .filter(user -> {
                    boolean match = user.checkPassword(password);
                    if (match) {
                        LOG.info("User {} authenticated", username);
                    } else {
                        LOG.warn("Failed authentication for {}", username);
                    }
                    return match;
                });
    }

    public AppUser createTemporaryUser(String roomId) {
        LOG.debug("createTemporaryUser for room {}", roomId);
        String username = "guest-" + UUID.randomUUID().toString().substring(0, 8);
        AppUser guest = new AppUser(username, Role.USER, UUID.randomUUID().toString());
        guest.setRoomAccess(roomId);
        users.put(username, guest);
        LOG.info("Temporary user {} created for room {}", username, roomId);
        return guest;
    }
}
