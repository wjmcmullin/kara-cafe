package com.app.karaoke.service;

import com.app.karaoke.model.AppUser;
import com.app.karaoke.model.Role;
import com.app.karaoke.persistence.UserDao;
import org.h2.jdbcx.JdbcDataSource;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import javax.sql.DataSource;
import java.sql.Connection;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

class UserServiceTest {

    private UserService userService;

    @BeforeEach
    void setUp() throws SQLException {
        DataSource ds = createDataSource();

        UserDao dao = new UserDao();
        dao.setDataSource(ds);

        userService = new UserService();
        userService.setUserDao(dao);

        userService.init();
    }

    @Test
    void superAdminIsSeededAndCannotBeDeleted() {
        assertThat(userService.listUsers()).anyMatch(AppUser::isSuperAdmin);
        boolean deleted = userService.deleteUser("super-admin");
        assertThat(deleted).isFalse();
    }

    @Test
    void createAdminAddsAdminRoleUser() {
        String username = "admin-" + UUID.randomUUID();
        assertThat(userService.createAdmin(username, "pw")).isPresent();

        assertThat(userService.listUsers())
                .anyMatch(user -> user.getUsername().equals(username) && user.getRole() == Role.ADMIN);
    }

    @Test
    void changePasswordAllowsAuthentication() {
        String username = "admin-change-" + UUID.randomUUID();
        userService.createAdmin(username, "initial");

        assertThat(userService.changePassword(username, "new-pass")).isTrue();
        assertThat(userService.authenticate(username, "new-pass")).isPresent();
    }

    @Test
    void listUsersContainsSuperAdminAndNewAdmins() {
        String username = "admin-list-" + UUID.randomUUID();
        userService.createAdmin(username, "pw");

        assertThat(userService.listUsers()).anyMatch(user -> user.getUsername().equals("super-admin"));
        assertThat(userService.listUsers()).anyMatch(user -> user.getUsername().equals(username));
    }

    private DataSource createDataSource() throws SQLException {
        JdbcDataSource ds = new JdbcDataSource();
        ds.setURL("jdbc:h2:mem:kara-" + UUID.randomUUID() + ";DB_CLOSE_DELAY=-1");
        ds.setUser("sa");
        ds.setPassword("");

        try (Connection conn = ds.getConnection(); Statement stmt = conn.createStatement()) {
            stmt.execute("""
                    CREATE TABLE IF NOT EXISTS kara_users (
                      username TEXT PRIMARY KEY,
                      password_hash TEXT NOT NULL,
                      role TEXT NOT NULL,
                      super_admin BOOLEAN NOT NULL DEFAULT FALSE,
                      created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP
                    )
                    """);
        }
        return ds;
    }
}
