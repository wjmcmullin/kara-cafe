package com.app.karaoke.persistence;

import java.sql.Connection;
import java.sql.DatabaseMetaData;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

import javax.sql.DataSource;

import com.app.karaoke.model.AppUser;
import com.app.karaoke.model.Role;

import jakarta.annotation.PostConstruct;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;

@ApplicationScoped
public class UserDao {

    private static final String TABLE_NAME = "kara_users";
    private static final String EMAIL_COLUMN = "email";

    private static final String SELECT_BASE = "SELECT username, password_hash, role, super_admin FROM kara_users WHERE username = ?";
    private static final String LIST_BASE = "SELECT username, password_hash, role, super_admin FROM kara_users ORDER BY created_at";
    private static final String SELECT_WITH_EMAIL = "SELECT username, password_hash, role, super_admin, email FROM kara_users WHERE username = ?";
    private static final String LIST_WITH_EMAIL = "SELECT username, password_hash, role, super_admin, email FROM kara_users ORDER BY created_at";

    private DataSource ds;
    private volatile boolean emailColumnPresent;

    public UserDao() {
        // required for CDI proxying
    }

    @Inject
    public void setDataSource(DataSource ds) {
        this.ds = ds;
        ensureTable();
    }

    @PostConstruct
    void initSchema() {
        ensureTable();
    }

    private void ensureTable() {
        if (ds == null) {
            return;
        }
        try (Connection conn = ds.getConnection(); Statement stmt = conn.createStatement()) {
            stmt.execute("""
                    CREATE TABLE IF NOT EXISTS kara_users (
                      username TEXT PRIMARY KEY,
                      password_hash TEXT NOT NULL,
                      role TEXT NOT NULL,
                      super_admin BOOLEAN NOT NULL DEFAULT FALSE,
                      email TEXT,
                      created_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT NOW()
                    )
                    """);
            ensureEmailColumn(conn);
        } catch (SQLException e) {
            throw new IllegalStateException("Unable to initialize kara_users table", e);
        }
    }

    private void ensureEmailColumn(Connection conn) throws SQLException {
        if (!hasEmailColumn(conn)) {
            try (Statement stmt = conn.createStatement()) {
                stmt.execute("ALTER TABLE kara_users ADD COLUMN email TEXT");
            }
        }
        emailColumnPresent = hasEmailColumn(conn);
    }

    private boolean hasEmailColumn(Connection conn) throws SQLException {
        DatabaseMetaData meta = conn.getMetaData();
        try (ResultSet columns = meta.getColumns(null, null, TABLE_NAME, EMAIL_COLUMN)) {
            if (columns.next()) {
                return true;
            }
        }
        try (ResultSet columns = meta.getColumns(null, null, TABLE_NAME.toUpperCase(), EMAIL_COLUMN.toUpperCase())) {
            return columns.next();
        }
    }

    public Optional<AppUser> find(String username) {
        String sql = emailColumnPresent ? SELECT_WITH_EMAIL : SELECT_BASE;
        try (Connection conn = ds.getConnection(); PreparedStatement stmt = conn.prepareStatement(sql)) {
            stmt.setString(1, username);
            try (ResultSet rs = stmt.executeQuery()) {
                if (rs.next()) {
                    return Optional.of(mapRow(rs));
                }
            }
        } catch (SQLException e) {
            throw new IllegalStateException("Error fetching user", e);
        }
        return Optional.empty();
    }

    public List<AppUser> listAll() {
        String sql = emailColumnPresent ? LIST_WITH_EMAIL : LIST_BASE;
        List<AppUser> users = new ArrayList<>();
        try (Connection conn = ds.getConnection(); PreparedStatement stmt = conn.prepareStatement(sql); ResultSet rs = stmt.executeQuery()) {
            while (rs.next()) {
                users.add(mapRow(rs));
            }
        } catch (SQLException e) {
            throw new IllegalStateException("Error listing users", e);
        }
        return users;
    }

    public boolean insert(AppUser user) {
        String sql = "INSERT INTO kara_users (username, password_hash, role, super_admin, email) VALUES (?, ?, ?, ?, ?)";
        try (Connection conn = ds.getConnection(); PreparedStatement stmt = conn.prepareStatement(sql)) {
            stmt.setString(1, user.getUsername());
            stmt.setString(2, user.getPassword());
            stmt.setString(3, user.getRole().name());
            stmt.setBoolean(4, user.isSuperAdmin());
            stmt.setString(5, user.getEmail());
            return stmt.executeUpdate() == 1;
        } catch (SQLException e) {
            throw new IllegalStateException("Error creating user", e);
        }
    }

    public boolean updatePassword(String username, String passwordHash) {
        String sql = "UPDATE kara_users SET password_hash = ? WHERE username = ?";
        try (Connection conn = ds.getConnection(); PreparedStatement stmt = conn.prepareStatement(sql)) {
            stmt.setString(1, passwordHash);
            stmt.setString(2, username);
            return stmt.executeUpdate() == 1;
        } catch (SQLException e) {
            throw new IllegalStateException("Error changing password", e);
        }
    }

    public boolean updateUsername(String currentUsername, String newUsername) {
        String sql = "UPDATE kara_users SET username = ? WHERE username = ?";
        try (Connection conn = ds.getConnection(); PreparedStatement stmt = conn.prepareStatement(sql)) {
            stmt.setString(1, newUsername);
            stmt.setString(2, currentUsername);
            return stmt.executeUpdate() == 1;
        } catch (SQLException e) {
            throw new IllegalStateException("Error changing username", e);
        }
    }

    public boolean updateEmail(String username, String email) {
        String sql = "UPDATE kara_users SET email = ? WHERE username = ?";
        try (Connection conn = ds.getConnection(); PreparedStatement stmt = conn.prepareStatement(sql)) {
            stmt.setString(1, email);
            stmt.setString(2, username);
            return stmt.executeUpdate() == 1;
        } catch (SQLException e) {
            throw new IllegalStateException("Error changing email", e);
        }
    }

    public boolean delete(String username) {
        String sql = "DELETE FROM kara_users WHERE username = ?";
        try (Connection conn = ds.getConnection(); PreparedStatement stmt = conn.prepareStatement(sql)) {
            stmt.setString(1, username);
            return stmt.executeUpdate() == 1;
        } catch (SQLException e) {
            throw new IllegalStateException("Error deleting user", e);
        }
    }

    private AppUser mapRow(ResultSet rs) throws SQLException {
        String username = rs.getString("username");
        String password = rs.getString("password_hash");
        Role role = Role.valueOf(rs.getString("role"));
        boolean superAdmin = rs.getBoolean("super_admin");
        String email = emailColumnPresent ? rs.getString("email") : null;
        return new AppUser(username, role, password, superAdmin, email);
    }
}
