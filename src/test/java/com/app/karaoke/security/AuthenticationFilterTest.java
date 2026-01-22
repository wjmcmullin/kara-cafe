package com.app.karaoke.security;

import static org.junit.jupiter.api.Assertions.assertEquals;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doNothing;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
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
        tokenService = new TokenService();
        requestUserContext = new RequestUserContext();

        filter.tokenService = tokenService;
        filter.requestUserContext = requestUserContext;

        context = mock(ContainerRequestContext.class);
        uriInfo = mock(UriInfo.class);
        when(context.getUriInfo()).thenReturn(uriInfo);
        doNothing().when(context).abortWith(any(Response.class));
    }

    @Test
    void abortsIfNoAuthorization() throws Exception {
        when(context.getHeaderString(HttpHeaders.AUTHORIZATION)).thenReturn(null);
        when(uriInfo.getPath()).thenReturn("rooms/list");

        filter.filter(context);

        verify(context).abortWith(any(Response.class));
    }

    @Test
    void allowsInviteTokenForRoomOnly() throws Exception {
        AppUser user = new AppUser("guest", Role.USER, "pw");
        String token = tokenService.issueInviteToken(user, "room123");

        when(context.getHeaderString(HttpHeaders.AUTHORIZATION)).thenReturn("Bearer " + token);
        when(uriInfo.getPath()).thenReturn("rooms/room123/enter");

        filter.filter(context);

        assertEquals(user, requestUserContext.getCurrentUser());
    }
}
