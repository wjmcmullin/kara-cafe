package com.app.karaoke.security;

import java.io.IOException;
import java.util.Optional;

import com.app.karaoke.service.TokenService;
import com.app.karaoke.service.TokenService.TokenInfo;

import jakarta.annotation.Priority;
import jakarta.inject.Inject;
import jakarta.ws.rs.Priorities;
import jakarta.ws.rs.container.ContainerRequestContext;
import jakarta.ws.rs.container.ContainerRequestFilter;
import jakarta.ws.rs.core.HttpHeaders;
import jakarta.ws.rs.core.Response;
import jakarta.ws.rs.ext.Provider;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

@Provider
@Priority(Priorities.AUTHENTICATION)
public class AuthenticationFilter implements ContainerRequestFilter {

    private static final Logger LOG = LogManager.getLogger(AuthenticationFilter.class);
    private static final String BEARER = "Bearer ";

    @Inject
    TokenService tokenService;

    @Inject
    RequestUserContext requestUserContext;

    @Override
    public void filter(ContainerRequestContext requestContext) throws IOException {
        LOG.debug("AuthenticationFilter invoked for {}", requestContext.getUriInfo().getPath());
        if (isPublicPath(requestContext)) {
            LOG.debug("Public path, skipping auth");
            return;
        }

        String authorization = requestContext.getHeaderString(HttpHeaders.AUTHORIZATION);
        if (authorization == null || !authorization.startsWith(BEARER)) {
            LOG.warn("Missing or invalid Authorization header");
            requestContext.abortWith(Response.status(Response.Status.UNAUTHORIZED)
                    .entity("Authentication required").build());
            return;
        }

        String token = authorization.substring(BEARER.length());
        Optional<TokenInfo> info = tokenService.getTokenInfo(token);
        if (info.isEmpty()) {
            LOG.warn("Invalid token {}", token);
            requestContext.abortWith(Response.status(Response.Status.UNAUTHORIZED)
                    .entity("Invalid or expired token").build());
            return;
        }

        TokenInfo tokenInfo = info.get();
        if (tokenInfo.isInviteOnly() && !isAllowedInvitePath(requestContext, tokenInfo)) {
            LOG.warn("Invite token blocked from accessing {}", requestContext.getUriInfo().getPath());
            requestContext.abortWith(Response.status(Response.Status.FORBIDDEN)
                    .entity("Invite tokens can only access the invited room").build());
            return;
        }

        requestUserContext.setCurrentUser(tokenInfo.getUser());
        LOG.debug("Token validated for {}", tokenInfo.getUser().getUsername());
    }

    private boolean isPublicPath(ContainerRequestContext context) {
        String path = context.getUriInfo().getPath();
        return path.endsWith("auth/login")
                || path.endsWith("login.html")
                || path.startsWith("assets/")
                || path.isBlank();
    }

    private boolean isAllowedInvitePath(ContainerRequestContext context, TokenInfo tokenInfo) {
        String path = context.getUriInfo().getPath();
        return tokenInfo.getRoomId()
                .map(id -> path.endsWith("/" + id) || path.endsWith("/" + id + "/enter") || path.endsWith("/" + id + "/song-request"))
                .orElse(false);
    }
}
