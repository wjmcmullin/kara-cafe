package com.app.karaoke.security;

import java.io.IOException;
import java.util.Optional;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.Mockito.doNothing;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import com.app.karaoke.model.AppUser;
import com.app.karaoke.model.Role;
import com.app.karaoke.service.TokenService;

import jakarta.ws.rs.container.ContainerRequestContext;
import jakarta.ws.rs.core.HttpHeaders;
import jakarta.ws.rs.core.Response;
import jakarta.ws.rs.core.UriInfo;

class AuthenticationFilterTest {

    private AuthenticationFilter filter;
    private TokenService tokenService;
    private RequestUserContext requestUserContext;
    private ContainerRequestContext context;
    private UriInfo uriInfo;

    @BeforeEach
    void setUp() {
        filter = new AuthenticationFilter();
        tokenService = mock(TokenService.class);
        requestUserContext = mock(RequestUserContext.class);

        filter.tokenService = tokenService;
        filter.requestUserContext = requestUserContext;

        context = mock(ContainerRequestContext.class);
        uriInfo = mock(UriInfo.class);
        when(context.getUriInfo()).thenReturn(uriInfo);
        doNothing().when(context).abortWith(any(Response.class));
    }

    @Test
    void skipsPublicPaths() throws IOException {
        when(uriInfo.getPath()).thenReturn("auth/login");

        filter.filter(context);

        verifyNoInteractions(tokenService, requestUserContext);
        verify(context, never()).abortWith(any());
    }

    @Test
    void rejectsMissingAuthorization() throws IOException {
        when(uriInfo.getPath()).thenReturn("rooms/list");
        when(context.getHeaderString(HttpHeaders.AUTHORIZATION)).thenReturn(null);

        filter.filter(context);

        verify(context).abortWith(argThat(resp -> resp.getStatus() == Response.Status.UNAUTHORIZED.getStatusCode()));
        verifyNoInteractions(tokenService, requestUserContext);
    }

    @Test
    void rejectsInvalidAuthorizationScheme() throws IOException {
        when(uriInfo.getPath()).thenReturn("rooms/list");
        when(context.getHeaderString(HttpHeaders.AUTHORIZATION)).thenReturn("Token abc");

        filter.filter(context);

        verify(context).abortWith(argThat(resp -> resp.getStatus() == Response.Status.UNAUTHORIZED.getStatusCode()));
        verifyNoInteractions(tokenService, requestUserContext);
    }

    @Test
    void rejectsUnknownToken() throws IOException {
        when(uriInfo.getPath()).thenReturn("rooms/list");
        when(context.getHeaderString(HttpHeaders.AUTHORIZATION)).thenReturn("Bearer bad");
        when(tokenService.getTokenInfo("bad")).thenReturn(Optional.empty());

        filter.filter(context);

        verify(context).abortWith(argThat(resp
                -> resp.getStatus() == Response.Status.UNAUTHORIZED.getStatusCode()
                && "Invalid or expired token".equals(resp.getEntity())));
        verify(requestUserContext, never()).setCurrentUser(any());
    }

    @Test
    void rejectsInviteTokenOnWrongPath() throws IOException {
        AppUser guest = new AppUser("guest", Role.USER, "pw");
        TokenService.TokenInfo info = mock(TokenService.TokenInfo.class);
        when(info.getUser()).thenReturn(guest);
        when(info.isInviteOnly()).thenReturn(true);
        when(info.getRoomId()).thenReturn(Optional.of("room123"));

        when(uriInfo.getPath()).thenReturn("rooms/list");
        when(context.getHeaderString(HttpHeaders.AUTHORIZATION)).thenReturn("Bearer token");
        when(tokenService.getTokenInfo("token")).thenReturn(Optional.of(info));

        filter.filter(context);

        verify(context).abortWith(argThat(resp
                -> resp.getStatus() == Response.Status.FORBIDDEN.getStatusCode()
                && "Invite tokens can only access the invited room".equals(resp.getEntity())));
        verify(requestUserContext, never()).setCurrentUser(any());
    }

    @Test
    void acceptsInviteTokenWhenPathMatches() throws IOException {
        AppUser guest = new AppUser("guest", Role.USER, "pw");
        TokenService.TokenInfo info = mock(TokenService.TokenInfo.class);
        when(info.getUser()).thenReturn(guest);
        when(info.isInviteOnly()).thenReturn(true);
        when(info.getRoomId()).thenReturn(Optional.of("room123"));

        when(uriInfo.getPath()).thenReturn("rooms/room123/enter");
        when(context.getHeaderString(HttpHeaders.AUTHORIZATION)).thenReturn("Bearer token");
        when(tokenService.getTokenInfo("token")).thenReturn(Optional.of(info));

        filter.filter(context);

        verify(requestUserContext).setCurrentUser(guest);
        verify(context, never()).abortWith(any());
    }

    @Test
    void acceptsAdminToken() throws IOException {
        AppUser admin = new AppUser("admin", Role.ADMIN, "pw");
        TokenService.TokenInfo info = mock(TokenService.TokenInfo.class);
        when(info.getUser()).thenReturn(admin);
        when(info.isInviteOnly()).thenReturn(false);
        when(info.getRoomId()).thenReturn(Optional.empty());

        when(uriInfo.getPath()).thenReturn("rooms/list");
        when(context.getHeaderString(HttpHeaders.AUTHORIZATION)).thenReturn("Bearer token");
        when(tokenService.getTokenInfo("token")).thenReturn(Optional.of(info));

        filter.filter(context);

        verify(requestUserContext).setCurrentUser(admin);
        verify(context, never()).abortWith(any());
    }
}
