package com.app.karaoke.service;

import java.util.List;
import java.util.Optional;

import org.mindrot.jbcrypt.BCrypt;

import com.app.karaoke.model.AppUser;
import com.app.karaoke.model.Role;
import com.app.karaoke.persistence.UserDao;

import jakarta.annotation.PostConstruct;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;

@ApplicationScoped
public class UserService {

    private UserDao userDao;

    private final String superAdminUsername;
    private final String superAdminPassword;

    public UserService() {
        this.superAdminUsername = pick("kara.super-admin.username", "KARA_SUPER_ADMIN_USERNAME", "super-admin");
        this.superAdminPassword = pick("kara.super-admin.password", "KARA_SUPER_ADMIN_PASSWORD", "admin");
    }

    @Inject
    public void setUserDao(UserDao userDao) {
        this.userDao = userDao;
    }

    @PostConstruct
    void init() {
        if (userDao.find(superAdminUsername).isEmpty()) {
            AppUser superUser = new AppUser(
                    superAdminUsername,
                    Role.SUPER_ADMIN,
                    hash(superAdminPassword),
                    true
            );
            userDao.insert(superUser);
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
        if (userDao.find(username).isPresent()) {
            return Optional.empty();
        }
        AppUser user = new AppUser(username, Role.ADMIN, hash(password), false);
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

    public boolean deleteUser(String username) {
        return userDao.find(username)
                .filter(user -> !user.isSuperAdmin())
                .map(user -> userDao.delete(username))
                .orElse(false);
    }

    private static String hash(String password) {
        return BCrypt.hashpw(password, BCrypt.gensalt());
    }

    private static String pick(String prop, String env, String fallback) {
        String value = System.getProperty(prop);
        if (value != null && !value.isBlank()) {
            return value;
        }
        value = System.getenv(env);
        return (value != null && !value.isBlank()) ? value : fallback;
    }
}
