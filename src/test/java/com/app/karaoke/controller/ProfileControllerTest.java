package com.app.karaoke.controller;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import static org.mockito.ArgumentMatchers.anyString;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import org.mockito.junit.jupiter.MockitoExtension;

import com.app.karaoke.model.AppUser;
import com.app.karaoke.model.Role;
import com.app.karaoke.security.RequestUserContext;
import com.app.karaoke.service.UserService;

import jakarta.ws.rs.core.Response;

@ExtendWith(MockitoExtension.class)
class ProfileControllerTest {

    @Mock
    private RequestUserContext userContext;

    @Mock
    private UserService userService;

    @InjectMocks
    private ProfileController controller;

    private AppUser adminUser;

    @BeforeEach
    void setup() {
        adminUser = new AppUser("admin", Role.ADMIN, "hash", false);
    }

    @Test
    void profileReturnsUnauthorizedWhenNotAuthenticated() {
        when(userContext.getCurrentUser()).thenReturn(null);

        Response response = controller.profile();

        assertEquals(Response.Status.UNAUTHORIZED.getStatusCode(), response.getStatus());
        Map<?, ?> entity = (Map<?, ?>) response.getEntity();
        assertEquals("Authentication required", entity.get("error"));
    }

    @Test
    void profileReturnsCurrentUserPayload() {
        when(userContext.getCurrentUser()).thenReturn(adminUser);

        Response response = controller.profile();

        assertEquals(Response.Status.OK.getStatusCode(), response.getStatus());
        Map<?, ?> payload = (Map<?, ?>) response.getEntity();
        assertEquals("admin", payload.get("username"));
        assertEquals("ADMIN", payload.get("role"));
    }

    @Test
    void changePasswordReturnsUnauthorizedWhenNotAuthenticated() {
        when(userContext.getCurrentUser()).thenReturn(null);

        Response response = controller.changePassword("new-password");

        assertEquals(Response.Status.UNAUTHORIZED.getStatusCode(), response.getStatus());
    }

    @Test
    void changePasswordValidatesInput() {
        when(userContext.getCurrentUser()).thenReturn(adminUser);

        Response response = controller.changePassword("   ");

        assertEquals(Response.Status.BAD_REQUEST.getStatusCode(), response.getStatus());
        Map<?, ?> entity = (Map<?, ?>) response.getEntity();
        assertEquals("Password is required", entity.get("error"));
    }

    @Test
    void changePasswordHandlesServiceFailure() {
        when(userContext.getCurrentUser()).thenReturn(adminUser);
        when(userService.changePassword("admin", "new-password")).thenReturn(false);

        Response response = controller.changePassword("new-password");

        assertEquals(Response.Status.NOT_FOUND.getStatusCode(), response.getStatus());
        Map<?, ?> entity = (Map<?, ?>) response.getEntity();
        assertEquals("Unable to update password", entity.get("error"));
    }

    @Test
    void changePasswordReturnsNoContentWhenSuccessful() {
        when(userContext.getCurrentUser()).thenReturn(adminUser);
        when(userService.changePassword("admin", "new-password")).thenReturn(true);

        Response response = controller.changePassword("new-password");

        assertEquals(Response.Status.NO_CONTENT.getStatusCode(), response.getStatus());
    }

    @Test
    void changeUsernameUnauthorized() {
        when(userContext.getCurrentUser()).thenReturn(null);

        Response response = controller.changeUsername("new-user");

        assertEquals(Response.Status.UNAUTHORIZED.getStatusCode(), response.getStatus());
    }

    @Test
    void changeUsernameValidatesInput() {
        when(userContext.getCurrentUser()).thenReturn(adminUser);

        Response response = controller.changeUsername("   ");

        assertEquals(Response.Status.BAD_REQUEST.getStatusCode(), response.getStatus());
        Map<?, ?> entity = (Map<?, ?>) response.getEntity();
        assertEquals("Username is required", entity.get("error"));
    }

    @Test
    void changeUsernameReturnsNoContentWhenAlreadySame() {
        when(userContext.getCurrentUser()).thenReturn(adminUser);

        Response response = controller.changeUsername("admin");

        assertEquals(Response.Status.NO_CONTENT.getStatusCode(), response.getStatus());
        verify(userService, never()).usernameExists(anyString());
    }

    @Test
    void changeUsernameDetectsConflict() {
        when(userContext.getCurrentUser()).thenReturn(adminUser);
        when(userService.usernameExists("other")).thenReturn(true);

        Response response = controller.changeUsername("other");

        assertEquals(Response.Status.CONFLICT.getStatusCode(), response.getStatus());
        Map<?, ?> entity = (Map<?, ?>) response.getEntity();
        assertEquals("Username already in use", entity.get("error"));
        verify(userService, never()).changeUsername(anyString(), anyString());
    }

    @Test
    void changeUsernameHandlesServiceFailure() {
        when(userContext.getCurrentUser()).thenReturn(adminUser);
        when(userService.usernameExists("new-admin")).thenReturn(false);
        when(userService.changeUsername("admin", "new-admin")).thenReturn(false);

        Response response = controller.changeUsername(" new-admin ");

        assertEquals(Response.Status.INTERNAL_SERVER_ERROR.getStatusCode(), response.getStatus());
        Map<?, ?> entity = (Map<?, ?>) response.getEntity();
        assertEquals("Unable to update username", entity.get("error"));
    }

    @Test
    void changeUsernameSucceeds() {
        when(userContext.getCurrentUser()).thenReturn(adminUser);
        when(userService.usernameExists("new-admin")).thenReturn(false);
        when(userService.changeUsername("admin", "new-admin")).thenReturn(true);

        Response response = controller.changeUsername(" new-admin ");

        assertEquals(Response.Status.NO_CONTENT.getStatusCode(), response.getStatus());
    }
}
