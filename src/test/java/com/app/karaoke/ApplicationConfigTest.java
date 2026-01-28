package com.app.karaoke;

import static org.assertj.core.api.Assertions.assertThat;
import org.junit.jupiter.api.Test;

import jakarta.ws.rs.ApplicationPath;
import jakarta.ws.rs.core.Application;

class ApplicationConfigTest {

    @Test
    void applicationPathIsConfigured() {
        ApplicationPath annotation = ApplicationConfig.class.getAnnotation(ApplicationPath.class);
        assertThat(annotation).isNotNull();
        assertThat(annotation.value()).isEqualTo("/api");
    }

    @Test
    void extendsJakartaApplication() {
        ApplicationConfig config = new ApplicationConfig();
        assertThat(config).isInstanceOf(Application.class);
    }
}
