package com.app.karaoke.controller;

import java.lang.reflect.Field;
import java.util.Map;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import org.mockito.MockitoAnnotations;

import com.app.karaoke.model.AppUser;
import com.app.karaoke.model.Role;
import com.app.karaoke.service.TokenService;
import com.app.karaoke.service.UserService;

import jakarta.ws.rs.core.HttpHeaders;
import jakarta.ws.rs.core.Response;

class AuthControllerTest {

    @Mock
    private UserService userService;

    @Mock
    private TokenService tokenService;

    @Mock
    private HttpHeaders headers;

    private AuthController controller;

    @BeforeEach
    void setUp() throws NoSuchFieldException, IllegalAccessException {
        MockitoAnnotations.openMocks(this);
        controller = new AuthController();

        Field userServiceField = AuthController.class.getDeclaredField("userService");
        userServiceField.setAccessible(true);
        userServiceField.set(controller, userService);

        Field tokenServiceField = AuthController.class.getDeclaredField("tokenService");
        tokenServiceField.setAccessible(true);
        tokenServiceField.set(controller, tokenService);
    }

    @Test
    void loginReturnsBadRequestWhenCredentialsMissing() {
        Response response = controller.login(null, "pass");
        assertThat(response.getStatus()).isEqualTo(Response.Status.BAD_REQUEST.getStatusCode());
        assertThat(response.getEntity()).isEqualTo(Map.of("error", "username and password are required"));

        response = controller.login("user", null);
        assertThat(response.getStatus()).isEqualTo(Response.Status.BAD_REQUEST.getStatusCode());
    }

    @Test
    void loginReturnsUnauthorizedForInvalidCredentials() {
        when(userService.authenticate("user", "wrong")).thenReturn(Optional.empty());

        Response response = controller.login("user", "wrong");
        assertThat(response.getStatus()).isEqualTo(Response.Status.UNAUTHORIZED.getStatusCode());
        assertThat(response.getEntity()).isEqualTo(Map.of("error", "invalid credentials"));

        verify(userService).authenticate("user", "wrong");
        verifyNoInteractions(tokenService);
    }

    @Test
    void loginIssuesTokenWhenAuthenticationSucceeds() {
        AppUser user = new AppUser("super-admin", Role.SUPER_ADMIN, "hash");
        when(userService.authenticate("super-admin", "admin")).thenReturn(Optional.of(user));
        when(tokenService.issueToken(user)).thenReturn("jwt-token");

        Response response = controller.login("super-admin", "admin");
        assertThat(response.getStatus()).isEqualTo(Response.Status.OK.getStatusCode());
        assertThat(response.getEntity()).isEqualTo(Map.of("token", "jwt-token"));

        verify(tokenService).issueToken(user);
    }

    @Test
    void logoutRequiresBearerHeader() {
        when(headers.getHeaderString(HttpHeaders.AUTHORIZATION)).thenReturn(null);
        Response response = controller.logout(headers);
        assertThat(response.getStatus()).isEqualTo(Response.Status.UNAUTHORIZED.getStatusCode());
        assertThat(response.getEntity()).isEqualTo(Map.of("error", "Authentication required"));

        when(headers.getHeaderString(HttpHeaders.AUTHORIZATION)).thenReturn("NotBearer token");
        response = controller.logout(headers);
        assertThat(response.getStatus()).isEqualTo(Response.Status.UNAUTHORIZED.getStatusCode());

        verifyNoInteractions(tokenService);
    }

    @Test
    void logoutRevokesTokenWhenBearerHeaderPresent() {
        when(headers.getHeaderString(HttpHeaders.AUTHORIZATION)).thenReturn("Bearer abc.def.ghi");

        Response response = controller.logout(headers);
        assertThat(response.getStatus()).isEqualTo(Response.Status.NO_CONTENT.getStatusCode());

        ArgumentCaptor<String> captor = ArgumentCaptor.forClass(String.class);
        verify(tokenService).revokeToken(captor.capture());
        assertThat(captor.getValue()).isEqualTo("abc.def.ghi");
    }
}
