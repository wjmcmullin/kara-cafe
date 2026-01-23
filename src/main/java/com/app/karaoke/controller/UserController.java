package com.app.karaoke.controller;

import java.util.Map;
import java.util.Optional;

import com.app.karaoke.model.AppUser;
import com.app.karaoke.security.RequestUserContext;
import com.app.karaoke.service.UserService;

import jakarta.inject.Inject;
import jakarta.ws.rs.Consumes;
import jakarta.ws.rs.FormParam;
import jakarta.ws.rs.POST;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;

@Path("/users")
@Produces(MediaType.APPLICATION_JSON)
public class UserController {

    @Inject
    private RequestUserContext requestUserContext;

    @Inject
    private UserService userService;

    @POST
    @Path("/admin")
    @Consumes(MediaType.APPLICATION_FORM_URLENCODED)
    public Response createAdmin(@FormParam("username") String username,
            @FormParam("password") String password) {
        AppUser currentUser = requestUserContext.getCurrentUser();
        if (currentUser == null || !currentUser.isSuperAdmin()) {
            return Response.status(Response.Status.FORBIDDEN)
                    .entity(Map.of("error", "Only super admins can create admin users")).build();
        }

        if (username == null || username.isBlank() || password == null || password.isBlank()) {
            return Response.status(Response.Status.BAD_REQUEST)
                    .entity(Map.of("error", "Username and password are required")).build();
        }

        Optional<AppUser> created = userService.createAdmin(username.trim(), password);
        if (created.isEmpty()) {
            return Response.status(Response.Status.CONFLICT)
                    .entity(Map.of("error", "Username already in use")).build();
        }

        AppUser admin = created.get();
        return Response.status(Response.Status.CREATED)
                .entity(Map.of(
                        "username", admin.getUsername(),
                        "role", admin.getRole().name()
                ))
                .build();
    }
}
