package com.app.karaoke.config;

import javax.sql.DataSource;

import com.zaxxer.hikari.HikariConfig;
import com.zaxxer.hikari.HikariDataSource;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.enterprise.inject.Produces;

@ApplicationScoped
public class DataSourceProducer {

    private final EnvProvider envProvider;

    public DataSourceProducer() {
        this(System::getenv);
    }

    DataSourceProducer(EnvProvider envProvider) {
        this.envProvider = envProvider;
    }

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
        String value = envProvider.getEnv(key);
        if (value == null || value.isBlank()) {
            throw new IllegalStateException(key + " must be set");
        }
        return value;
    }

    @FunctionalInterface
    interface EnvProvider {

        String getEnv(String key);
    }
}
