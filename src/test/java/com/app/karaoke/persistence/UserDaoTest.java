package com.app.karaoke.persistence;

import java.sql.Connection;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.UUID;

import javax.sql.DataSource;

import static org.assertj.core.api.Assertions.assertThat;
import org.h2.jdbcx.JdbcDataSource;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import com.app.karaoke.model.AppUser;
import com.app.karaoke.model.Role;

class UserDaoTest {

    private UserDao userDao;

    @BeforeEach
    void setUp() throws SQLException {
        DataSource ds = createDataSource();

        userDao = new UserDao();
        userDao.setDataSource(ds);
    }

    @Test
    void insertAndFindRoundTrip() {
        assertThat(userDao.insert(new AppUser("foo", Role.ADMIN, "pw", false, null))).isTrue();
        assertThat(userDao.find("foo")).isPresent();
    }

    @Test
    void updatePasswordWorksAndReturnsFalseWhenMissing() {
        userDao.insert(new AppUser("foo", Role.ADMIN, "pw", false, null));
        assertThat(userDao.updatePassword("foo", "new")).isTrue();
        assertThat(userDao.updatePassword("missing", "new")).isFalse();
    }

    @Test
    void deleteRemovesRowAndReturnsFalseForAlreadyDeleted() {
        userDao.insert(new AppUser("foo", Role.ADMIN, "pw", false, null));
        assertThat(userDao.delete("foo")).isTrue();
        assertThat(userDao.delete("foo")).isFalse();
    }

    @Test
    void listAllReturnsInsertedUsers() {
        userDao.insert(new AppUser("foo", Role.ADMIN, "pw", false, null));
        userDao.insert(new AppUser("bar", Role.ADMIN, "pw", false, null));
        assertThat(userDao.listAll()).hasSize(2);
    }

    @Test
    void findReturnsEmptyForUnknownUser() {
        assertThat(userDao.find("missing")).isEmpty();
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
                      email TEXT,
                      created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP
                    )
                    """);
        }
        return ds;
    }
}
