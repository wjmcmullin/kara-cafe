package com.app.karaoke.persistence;

import java.sql.Connection;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import javax.sql.DataSource;

import static org.assertj.core.api.Assertions.assertThat;
import org.h2.jdbcx.JdbcDataSource;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import com.app.karaoke.model.AppUser;
import com.app.karaoke.model.Role;

class UserDaoTest {

    private UserDao userDao;
    private DataSource dataSource;

    @BeforeEach
    void setUp() throws SQLException {
        dataSource = createDataSource();
        userDao = new UserDao();
        userDao.setDataSource(dataSource);
        userDao.ensureTable();
    }

    @AfterEach
    void tearDown() throws SQLException {
        try (Connection conn = dataSource.getConnection(); Statement stmt = conn.createStatement()) {
            stmt.execute("DROP TABLE IF EXISTS kara_users");
        }
    }

    @Test
    void insertAndFindRoundTrip() {
        AppUser user = new AppUser("john", Role.ADMIN, "pw", false);

        assertThat(userDao.insert(user)).isTrue();

        Optional<AppUser> found = userDao.find("john");

        assertThat(found).isPresent();
        assertThat(found.get().getUsername()).isEqualTo("john");
        assertThat(found.get().getRole()).isEqualTo(Role.ADMIN);
        assertThat(found.get().isSuperAdmin()).isFalse();
    }

    @Test
    void listAllReturnsInsertedUsers() {
        userDao.insert(new AppUser("one", Role.ADMIN, "pw", false));
        userDao.insert(new AppUser("two", Role.SUPER_ADMIN, "pw", true));

        List<AppUser> users = userDao.listAll();

        assertThat(users).hasSize(2);
        assertThat(users).extracting(AppUser::getUsername).containsExactly("one", "two");
    }

    @Test
    void updatePasswordWorksAndReturnsFalseWhenMissing() {
        userDao.insert(new AppUser("jane", Role.ADMIN, "old", false));

        assertThat(userDao.updatePassword("jane", "new")).isTrue();
        Optional<AppUser> updated = userDao.find("jane");
        assertThat(updated).isPresent();
        assertThat(updated.get().getPassword()).isEqualTo("new");

        assertThat(userDao.updatePassword("missing", "pw")).isFalse();
    }

    @Test
    void deleteRemovesRowAndReturnsFalseForAlreadyDeleted() {
        userDao.insert(new AppUser("alice", Role.ADMIN, "pw", false));

        assertThat(userDao.delete("alice")).isTrue();
        assertThat(userDao.find("alice")).isEmpty();

        assertThat(userDao.delete("alice")).isFalse();
    }

    @Test
    void findReturnsEmptyForUnknownUser() {
        assertThat(userDao.find("ghost")).isEmpty();
    }

    private static DataSource createDataSource() {
        JdbcDataSource ds = new JdbcDataSource();
        ds.setURL("jdbc:h2:mem:user-dao-" + UUID.randomUUID() + ";DB_CLOSE_DELAY=-1");
        ds.setUser("sa");
        ds.setPassword("");
        return ds;
    }
}
