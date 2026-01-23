package com.app.karaoke.config;

import java.util.Map;

import javax.sql.DataSource;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import org.junit.jupiter.api.Test;

import com.zaxxer.hikari.HikariDataSource;

class DataSourceProducerTest {

    @Test
    void createDataSourceUsesEnvValues() {
        DataSourceProducer producer = new DataSourceProducer(key -> Map.of(
                "DB_URL", "jdbc:h2:mem:test-db;DB_CLOSE_DELAY=-1",
                "DB_USER", "sa",
                "DB_PASSWORD", "test"
        ).get(key));

        DataSource ds = producer.createDataSource();
        assertThat(ds).isInstanceOf(HikariDataSource.class);

        HikariDataSource hikari = (HikariDataSource) ds;
        assertThat(hikari.getJdbcUrl()).contains("jdbc:h2:mem:test-db");
        assertThat(hikari.getMaximumPoolSize()).isEqualTo(5);
        hikari.close();
    }

    @Test
    void createDataSourceFailsWhenMissingEnv() {
        DataSourceProducer producer = new DataSourceProducer(key -> Map.of(
                "DB_USER", "sa",
                "DB_PASSWORD", "test"
        ).get(key));

        assertThatThrownBy(producer::createDataSource)
                .isInstanceOf(IllegalStateException.class)
                .hasMessage("DB_URL must be set");
    }
}
