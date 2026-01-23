package com.app.karaoke.persistence;

import java.sql.Connection;
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

    private DataSource ds;

    public UserDao() {
        // required for CDI proxying
    }

    @Inject
    public void setDataSource(DataSource ds) {
        this.ds = ds;
    }

    @PostConstruct
    void ensureTable() {
        try (Connection conn = ds.getConnection(); Statement stmt = conn.createStatement()) {
            stmt.execute("""
                    CREATE TABLE IF NOT EXISTS kara_users (
                      username TEXT PRIMARY KEY,
                      password_hash TEXT NOT NULL,
                      role TEXT NOT NULL,
                      super_admin BOOLEAN NOT NULL DEFAULT FALSE,
                      created_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT NOW()
                    )
                    """);
        } catch (SQLException e) {
            throw new IllegalStateException("Unable to initialize kara_users table", e);
        }
    }

    public Optional<AppUser> find(String username) {
        String sql = "SELECT username, password_hash, role, super_admin FROM kara_users WHERE username = ?";
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
        String sql = "SELECT username, password_hash, role, super_admin FROM kara_users ORDER BY created_at";
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
        String sql = "INSERT INTO kara_users (username, password_hash, role, super_admin) VALUES (?, ?, ?, ?)";
        try (Connection conn = ds.getConnection(); PreparedStatement stmt = conn.prepareStatement(sql)) {
            stmt.setString(1, user.getUsername());
            stmt.setString(2, user.getPassword());
            stmt.setString(3, user.getRole().name());
            stmt.setBoolean(4, user.isSuperAdmin());
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
        return new AppUser(username, role, password, superAdmin);
    }
}
