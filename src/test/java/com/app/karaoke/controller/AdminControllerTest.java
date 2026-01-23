package com.app.karaoke.controller;

import com.app.karaoke.model.AppUser;
import com.app.karaoke.model.Role;
import com.app.karaoke.security.Authorizer;
import com.app.karaoke.security.RequestUserContext;
import com.app.karaoke.service.UserService;
import jakarta.ws.rs.core.Response;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;

import java.lang.reflect.Field;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.*;

class UserAdminControllerTest {

    @Mock
    private RequestUserContext userContext;

    @Mock
    private UserService userService;

    @Mock
    private Authorizer authorizer;

    private UserAdminController controller;

    private final AppUser superAdmin = new AppUser("super", Role.SUPER_ADMIN, "hash");
    private final AppUser admin = new AppUser("admin", Role.ADMIN, "hash");

    @BeforeEach
    void setUp() throws NoSuchFieldException, IllegalAccessException {
        MockitoAnnotations.openMocks(this);
        controller = new UserAdminController();
        injectField("userContext", userContext);
        injectField("userService", userService);
        injectField("authorizer", authorizer);
    }

    @Test
    void listUsersReturnsForbiddenWhenNotSuperAdmin() {
        when(userContext.getCurrentUser()).thenReturn(admin);

        Response response = controller.listUsers();

        assertThat(response.getStatus()).isEqualTo(Response.Status.FORBIDDEN.getStatusCode());
        assertThat(response.getEntity()).isEqualTo(Map.of("error", "Access denied"));
    }

    @Test
    void listUsersReturnsPayloadWhenSuperAdmin() {
        when(userContext.getCurrentUser()).thenReturn(superAdmin);
        when(userService.listUsers()).thenReturn(List.of(admin, superAdmin));

        Response response = controller.listUsers();

        assertThat(response.getStatus()).isEqualTo(Response.Status.OK.getStatusCode());
        List<?> payload = cast(response.getEntity());
        assertThat(payload).hasSize(2);
        assertThat(payload).anyMatch(item -> ((Map<?, ?>) item).get("username").equals("super"));
    }

    @Test
    void changePasswordHandlesAuthAndValidation() {
        when(userContext.getCurrentUser()).thenReturn(null);
        Response missing = controller.changePassword("admin", "pw");
        assertThat(missing.getStatus()).isEqualTo(Response.Status.UNAUTHORIZED.getStatusCode());

        when(userContext.getCurrentUser()).thenReturn(admin);
        Response forbidden = controller.changePassword("other", "pw");
        assertThat(forbidden.getStatus()).isEqualTo(Response.Status.FORBIDDEN.getStatusCode());

        when(userContext.getCurrentUser()).thenReturn(superAdmin);
        Response bad = controller.changePassword("any", "");
        assertThat(bad.getStatus()).isEqualTo(Response.Status.BAD_REQUEST.getStatusCode());
        assertThat(bad.getEntity()).isEqualTo(Map.of("error", "Password is required"));

        when(userService.changePassword("any", "pw")).thenReturn(false);
        Response notFound = controller.changePassword("any", "pw");
        assertThat(notFound.getStatus()).isEqualTo(Response.Status.NOT_FOUND.getStatusCode());
        assertThat(notFound.getEntity()).isEqualTo(Map.of("error", "User not found"));

        when(userService.changePassword("any", "pw")).thenReturn(true);
        Response success = controller.changePassword("any", "pw");
        assertThat(success.getStatus()).isEqualTo(Response.Status.NO_CONTENT.getStatusCode());
    }

    @Test
    void deleteUserRespectsSuperAdminOnlyAndResult() {
        when(userContext.getCurrentUser()).thenReturn(admin);
        Response forbidden = controller.deleteUser("admin");
        assertThat(forbidden.getStatus()).isEqualTo(Response.Status.FORBIDDEN.getStatusCode());

        when(userContext.getCurrentUser()).thenReturn(superAdmin);
        when(userService.deleteUser("admin")).thenReturn(false);
        Response bad = controller.deleteUser("admin");
        assertThat(bad.getStatus()).isEqualTo(Response.Status.BAD_REQUEST.getStatusCode());
        assertThat(bad.getEntity()).isEqualTo(Map.of("error", "Cannot delete this user"));

        when(userService.deleteUser("admin")).thenReturn(true);
        Response ok = controller.deleteUser("admin");
        assertThat(ok.getStatus()).isEqualTo(Response.Status.NO_CONTENT.getStatusCode());
    }

    @SuppressWarnings("unchecked")
    private <T> List<T> cast(Object entity) {
        return (List<T>) entity;
    }

    private void injectField(String name, Object value) throws NoSuchFieldException, IllegalAccessException {
        Field field = UserAdminController.class.getDeclaredField(name);
        field.setAccessible(true);
        field.set(controller, value);
    }
}
