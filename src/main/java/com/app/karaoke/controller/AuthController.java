package com.app.karaoke.controller;

import java.util.Map;

import com.app.karaoke.service.TokenService;
import com.app.karaoke.service.UserService;

import jakarta.inject.Inject;
import jakarta.ws.rs.Consumes;
import jakarta.ws.rs.FormParam;
import jakarta.ws.rs.POST;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;

@Path("/auth")
public class AuthController {

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

        return userService.authenticate(username, password)
                .map(user -> {
                    String token = tokenService.issueToken(user);
                    return Response.ok(Map.of("token", token)).build();
                })
                .orElse(Response.status(Response.Status.UNAUTHORIZED)
                        .entity(Map.of("error", "invalid credentials")).build());
    }
}
