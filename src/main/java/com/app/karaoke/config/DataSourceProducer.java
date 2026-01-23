package com.app.karaoke.config;

import com.zaxxer.hikari.HikariConfig;
import com.zaxxer.hikari.HikariDataSource;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.enterprise.inject.Produces;

import javax.sql.DataSource;

@ApplicationScoped
public class DataSourceProducer {

    @Produces
    @ApplicationScoped
    public DataSource createDataSource() {
        HikariConfig config = new HikariConfig();
        config.setJdbcUrl(requireEnv("DB_URL"));
        config.setUsername(requireEnv("DB_USER"));
        config.setPassword(requireEnv("DB_PASSWORD"));
        config.setMaximumPoolSize(5);
        config.setPoolName("kara-pool");
        return new HikariDataSource(config);
    }

    private String requireEnv(String key) {
        String value = System.getenv(key);
        if (value == null || value.isBlank()) {
            throw new IllegalStateException(key + " must be set");
        }
        return value;
    }
}
