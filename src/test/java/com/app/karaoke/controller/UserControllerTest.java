package com.app.karaoke.controller;

import java.lang.reflect.Field;
import java.util.Map;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mock;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import org.mockito.MockitoAnnotations;

import com.app.karaoke.model.AppUser;
import com.app.karaoke.model.Role;
import com.app.karaoke.security.RequestUserContext;
import com.app.karaoke.service.UserService;

import jakarta.ws.rs.core.Response;

class UserControllerTest {

    @Mock
    private RequestUserContext requestUserContext;

    @Mock
    private UserService userService;

    private UserController controller;

    private final AppUser superAdmin = new AppUser("super", Role.SUPER_ADMIN, "pw");
    private final AppUser admin = new AppUser("admin", Role.ADMIN, "pw");

    @BeforeEach
    void setUp() throws NoSuchFieldException, IllegalAccessException {
        MockitoAnnotations.openMocks(this);
        controller = new UserController();

        injectField("requestUserContext", requestUserContext);
        injectField("userService", userService);
    }

    @Test
    void createAdminForbiddenForNonSuperAdmin() {
        when(requestUserContext.getCurrentUser()).thenReturn(admin);

        Response response = controller.createAdmin("foo", "bar");

        assertThat(response.getStatus()).isEqualTo(Response.Status.FORBIDDEN.getStatusCode());
        assertThat(response.getEntity()).isEqualTo(Map.of("error", "Only super admins can create admin users"));
        verifyNoInteractions(userService);
    }

    @Test
    void createAdminRejectsMissingCredentials() {
        when(requestUserContext.getCurrentUser()).thenReturn(superAdmin);

        Response missingUsername = controller.createAdmin("", "pw");
        assertThat(missingUsername.getStatus()).isEqualTo(Response.Status.BAD_REQUEST.getStatusCode());

        Response missingPassword = controller.createAdmin("foo", " ");
        assertThat(missingPassword.getStatus()).isEqualTo(Response.Status.BAD_REQUEST.getStatusCode());

        verifyNoInteractions(userService);
    }

    @Test
    void createAdminHandlesConflict() {
        when(requestUserContext.getCurrentUser()).thenReturn(superAdmin);
        when(userService.createAdmin("foo", "pw")).thenReturn(Optional.empty());

        Response response = controller.createAdmin("foo", "pw");
        assertThat(response.getStatus()).isEqualTo(Response.Status.CONFLICT.getStatusCode());
        assertThat(response.getEntity()).isEqualTo(Map.of("error", "Username already in use"));
    }

    @Test
    void createAdminReturnsCreatedPayload() {
        when(requestUserContext.getCurrentUser()).thenReturn(superAdmin);
        AppUser created = new AppUser("new-admin", Role.ADMIN, "hash");
        when(userService.createAdmin("new-admin", "pw")).thenReturn(Optional.of(created));

        Response response = controller.createAdmin("new-admin", "pw");

        assertThat(response.getStatus()).isEqualTo(Response.Status.CREATED.getStatusCode());
        assertThat(response.getEntity()).isEqualTo(Map.of("username", "new-admin", "role", "ADMIN"));
    }

    private void injectField(String name, Object value) throws NoSuchFieldException, IllegalAccessException {
        Field field = UserController.class.getDeclaredField(name);
        field.setAccessible(true);
        field.set(controller, value);
    }
}
