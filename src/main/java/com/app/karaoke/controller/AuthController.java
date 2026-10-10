package com.app.karaoke.controller;

import java.util.Map;
import java.util.Optional;

import com.app.karaoke.model.AppUser;
import com.app.karaoke.service.TokenService;
import com.app.karaoke.service.TokenService.TokenInfo;
import com.app.karaoke.service.UserService;

import jakarta.inject.Inject;
import jakarta.ws.rs.Consumes;
import jakarta.ws.rs.DELETE;
import jakarta.ws.rs.FormParam;
import jakarta.ws.rs.GET;
import jakarta.ws.rs.POST;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.core.Context;
import jakarta.ws.rs.core.HttpHeaders;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;

@Path("/auth")
public class AuthController {

    private static final String BEARER = "Bearer ";

    @Inject
    private UserService userService;

    @Inject
    private TokenService tokenService;

    @POST
    @Path("/login")
    @Consumes(MediaType.APPLICATION_FORM_URLENCODED)
    @Produces(MediaType.APPLICATION_JSON)
    public Response login(@FormParam("username") String username,
            @FormParam("password") String password) {
        if (username == null || password == null) {
            return Response.status(Response.Status.BAD_REQUEST)
                    .entity(Map.of("error", "username and password are required"))
                    .build();
        }

        return userService.authenticate(username.trim(), password)
                .map(user -> {
                    String token = tokenService.issueToken(user);
                    return Response.ok(Map.of("token", token)).build();
                })
                .orElse(Response.status(Response.Status.UNAUTHORIZED)
                        .entity(Map.of("error", "invalid credentials")).build());
    }

    @POST
    @Path("/register")
    @Consumes(MediaType.APPLICATION_FORM_URLENCODED)
    @Produces(MediaType.APPLICATION_JSON)
    public Response register(
            @FormParam("username") String username,
            @FormParam("password") String password,
            @FormParam("email") String email) {
        if (username == null || username.isBlank() || password == null || password.isBlank()) {
            return Response.status(Response.Status.BAD_REQUEST)
                    .entity(Map.of("error", "username and password are required"))
                    .build();
        }

        Optional<AppUser> created = userService.createUser(username, password, email);
        if (created.isEmpty()) {
            return Response.status(Response.Status.BAD_REQUEST)
                    .entity(Map.of("error", "Unable to create account. Use a unique username (3-64 chars: letters, numbers, . _ -)."))
                    .build();
        }

        return Response.status(Response.Status.CREATED)
                .entity(Map.of("username", created.get().getUsername(), "role", created.get().getRole().name()))
                .build();
    }

    @GET
    @Path("/session")
    @Produces(MediaType.APPLICATION_JSON)
    public Response session(@Context HttpHeaders headers) {
        Optional<Map.Entry<String, TokenInfo>> tokenInfo = requireToken(headers);
        if (tokenInfo.isEmpty()) {
            return Response.status(Response.Status.UNAUTHORIZED)
                    .entity(Map.of("error", "Authentication required"))
                    .build();
        }

        TokenInfo info = tokenInfo.get().getValue();
        java.util.HashMap<String, Object> payload = new java.util.HashMap<>();
        payload.put("username", info.getUser().getUsername());
        payload.put("role", info.getUser().getRole().name());
        payload.put("inviteOnly", info.isInviteOnly());
        info.getRoomId().ifPresent(id -> payload.put("roomId", id));
        return Response.ok(payload).build();
    }

    @POST
    @Path("/guest-nickname")
    @Consumes(MediaType.APPLICATION_FORM_URLENCODED)
    @Produces(MediaType.APPLICATION_JSON)
    public Response updateGuestNickname(
            @Context HttpHeaders headers,
            @FormParam("nickname") String nickname) {
        if (nickname == null || nickname.isBlank() || !userService.isValidUsername(nickname.trim())) {
            return Response.status(Response.Status.BAD_REQUEST)
                    .entity(Map.of("error", "Nickname must be 3-64 characters and use letters, numbers, . _ -"))
                    .build();
        }

        Optional<Map.Entry<String, TokenInfo>> tokenInfo = requireToken(headers);
        if (tokenInfo.isEmpty()) {
            return Response.status(Response.Status.UNAUTHORIZED)
                    .entity(Map.of("error", "Authentication required"))
                    .build();
        }

        TokenInfo info = tokenInfo.get().getValue();
        String token = tokenInfo.get().getKey();
        String requestedNickname = nickname.trim();

        if (info.getRoomId().isPresent()
                && tokenService.isGuestNicknameTakenInRoom(info.getRoomId().get(), requestedNickname, token)) {
            return Response.status(Response.Status.CONFLICT)
                    .entity(Map.of("error", "That guest nickname is already in use in this room"))
                    .build();
        }

        Optional<Map<String, Object>> rotated = tokenService.renameGuestAndRotateToken(
                token,
                requestedNickname
        );

        if (rotated.isEmpty()) {
            return Response.status(Response.Status.FORBIDDEN)
                    .entity(Map.of("error", "Only invited guest sessions can change nickname"))
                    .build();
        }

        return Response.ok(rotated.get()).build();
    }

    @DELETE
    @Path("/logout")
    @Produces(MediaType.APPLICATION_JSON)
    public Response logout(@Context HttpHeaders headers) {
        Optional<String> token = extractBearerToken(headers);
        if (token.isEmpty()) {
            return Response.status(Response.Status.UNAUTHORIZED)
                    .entity(Map.of("error", "Authentication required")).build();
        }

        tokenService.revokeToken(token.get());
        return Response.noContent().build();
    }

    private Optional<Map.Entry<String, TokenInfo>> requireToken(HttpHeaders headers) {
        Optional<String> token = extractBearerToken(headers);
        if (token.isEmpty()) {
            return Optional.empty();
        }

        Optional<TokenInfo> info = tokenService.getTokenInfo(token.get());
        if (info.isEmpty()) {
            return Optional.empty();
        }

        return Optional.of(Map.entry(token.get(), info.get()));
    }

    private Optional<String> extractBearerToken(HttpHeaders headers) {
        String authorization = headers.getHeaderString(HttpHeaders.AUTHORIZATION);
        if (authorization == null || !authorization.startsWith(BEARER)) {
            return Optional.empty();
        }

        String token = authorization.substring(BEARER.length()).trim();
        if (token.isEmpty()) {
            return Optional.empty();
        }

        return Optional.of(token);
    }
}
