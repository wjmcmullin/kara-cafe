package com.app.karaoke.service;

import java.util.List;
import java.util.Optional;
import java.util.regex.Pattern;

import org.mindrot.jbcrypt.BCrypt;

import com.app.karaoke.model.AppUser;
import com.app.karaoke.model.Role;
import com.app.karaoke.persistence.UserDao;

import jakarta.annotation.PostConstruct;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;

@ApplicationScoped
public class UserService {

    private static final Pattern EMAIL_PATTERN = Pattern.compile("^[^\\s@]+@[^\\s@]+\\.[^\\s@]+$");
    private static final Pattern USERNAME_PATTERN = Pattern.compile("^[A-Za-z0-9._-]{3,64}$");

    private UserDao userDao;

    private final String configuredSuperAdminUsername;
    private final String configuredSuperAdminPassword;

    public UserService() {
        this.configuredSuperAdminUsername = pickOptional("kara.super-admin.username", "KARA_SUPER_ADMIN_USERNAME");
        this.configuredSuperAdminPassword = pickOptional("kara.super-admin.password", "KARA_SUPER_ADMIN_PASSWORD");
    }

    @Inject
    public void setUserDao(UserDao userDao) {
        this.userDao = userDao;
    }

    @PostConstruct
    void init() {
        boolean hasConfiguredCredentials = configuredSuperAdminUsername != null && configuredSuperAdminPassword != null;

        if (hasConfiguredCredentials) {
            Optional<AppUser> configuredUser = userDao.find(configuredSuperAdminUsername);
            if (configuredUser.isPresent()) {
                AppUser existing = configuredUser.get();
                if (!existing.isSuperAdmin()) {
                    throw new IllegalStateException(
                            "Configured bootstrap user exists but is not SUPER_ADMIN: " + configuredSuperAdminUsername
                    );
                }

                if (!BCrypt.checkpw(configuredSuperAdminPassword, existing.getPassword())) {
                    userDao.updatePassword(configuredSuperAdminUsername, hash(configuredSuperAdminPassword));
                }
                return;
            }

            AppUser superUser = new AppUser(
                    configuredSuperAdminUsername,
                    Role.SUPER_ADMIN,
                    hash(configuredSuperAdminPassword),
                    true,
                    null
            );
            userDao.insert(superUser);
            return;
        }

        boolean hasExistingSuperAdmin = userDao.listAll().stream().anyMatch(AppUser::isSuperAdmin);
        if (!hasExistingSuperAdmin) {
            throw new IllegalStateException(
                    "Missing required super-admin bootstrap credential for first-time setup. "
                            + "Set system properties 'kara.super-admin.username' and 'kara.super-admin.password' "
                            + "or environment variables 'KARA_SUPER_ADMIN_USERNAME' and 'KARA_SUPER_ADMIN_PASSWORD'."
            );
        }
    }

    public Optional<AppUser> authenticate(String username, String password) {
        return userDao.find(username)
                .filter(user -> BCrypt.checkpw(password, user.getPassword()));
    }

    public Optional<AppUser> createAdmin(String username, String password) {
        if (username == null || username.isBlank() || password == null || password.isBlank()) {
            return Optional.empty();
        }

        String normalizedUsername = username.trim();
        if (!isValidUsername(normalizedUsername)) {
            return Optional.empty();
        }

        if (userDao.find(normalizedUsername).isPresent()) {
            return Optional.empty();
        }

        AppUser user = new AppUser(normalizedUsername, Role.ADMIN, hash(password), false, null);
        return userDao.insert(user) ? Optional.of(user) : Optional.empty();
    }

    public Optional<AppUser> createUser(String username, String password, String email) {
        if (username == null || username.isBlank() || password == null || password.isBlank()) {
            return Optional.empty();
        }

        String normalizedUsername = username.trim();
        if (!isValidUsername(normalizedUsername)) {
            return Optional.empty();
        }

        if (userDao.find(normalizedUsername).isPresent()) {
            return Optional.empty();
        }

        String normalizedEmail = (email == null || email.isBlank()) ? null : email.trim();
        if (normalizedEmail != null && !isValidEmail(normalizedEmail)) {
            return Optional.empty();
        }

        AppUser user = new AppUser(normalizedUsername, Role.USER, hash(password), false, normalizedEmail);
        return userDao.insert(user) ? Optional.of(user) : Optional.empty();
    }

    public List<AppUser> listUsers() {
        return userDao.listAll();
    }

    public boolean changePassword(String username, String newPassword) {
        if (newPassword == null || newPassword.isBlank()) {
            return false;
        }
        return userDao.updatePassword(username, hash(newPassword));
    }

    public boolean changeUsername(String currentUsername, String newUsername) {
        if (currentUsername == null || currentUsername.isBlank() || newUsername == null || newUsername.isBlank()) {
            return false;
        }

        String normalizedUsername = newUsername.trim();
        if (!isValidUsername(normalizedUsername)) {
            return false;
        }

        return userDao.updateUsername(currentUsername, normalizedUsername);
    }

    public boolean changeEmail(String username, String email) {
        if (username == null || username.isBlank() || email == null || email.isBlank()) {
            return false;
        }
        if (!isValidEmail(email)) {
            return false;
        }
        return userDao.updateEmail(username, email);
    }

    public boolean usernameExists(String username) {
        if (username == null || username.isBlank()) {
            return false;
        }
        return userDao.find(username).isPresent();
    }

    public boolean isValidEmail(String email) {
        return email != null && EMAIL_PATTERN.matcher(email).matches();
    }

    public boolean isValidUsername(String username) {
        return username != null && USERNAME_PATTERN.matcher(username).matches();
    }

    public boolean deleteUser(String username) {
        return userDao.find(username)
                .filter(user -> !user.isSuperAdmin())
                .map(user -> userDao.delete(username))
                .orElse(false);
    }

    private static String hash(String password) {
        return BCrypt.hashpw(password, BCrypt.gensalt());
    }

    private static String pickOptional(String prop, String env) {
        String value = System.getProperty(prop);
        if (value != null && !value.isBlank()) {
            return value;
        }

        value = System.getenv(env);
        if (value != null && !value.isBlank()) {
            return value;
        }

        return null;
    }
}
