package com.app.karaoke.controller;

import java.util.Map;

import com.app.karaoke.model.AppUser;
import com.app.karaoke.security.RequestUserContext;
import com.app.karaoke.service.UserService;

import jakarta.inject.Inject;
import jakarta.ws.rs.Consumes;
import jakarta.ws.rs.FormParam;
import jakarta.ws.rs.GET;
import jakarta.ws.rs.POST;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;

@Path("/users/profile")
@Produces(MediaType.APPLICATION_JSON)
public class ProfileController {

    @Inject
    private RequestUserContext userContext;

    @Inject
    private UserService userService;

    @GET
    public Response profile() {
        AppUser current = userContext.getCurrentUser();
        if (current == null) {
            return Response.status(Response.Status.UNAUTHORIZED)
                    .entity(Map.of("error", "Authentication required")).build();
        }
        return Response.ok(current.toPayload()).build();
    }

    @POST
    @Path("/password")
    @Consumes(MediaType.APPLICATION_FORM_URLENCODED)
    public Response changePassword(@FormParam("password") String newPassword) {
        AppUser current = userContext.getCurrentUser();
        if (current == null) {
            return Response.status(Response.Status.UNAUTHORIZED)
                    .entity(Map.of("error", "Authentication required")).build();
        }

        if (newPassword == null || newPassword.isBlank()) {
            return Response.status(Response.Status.BAD_REQUEST)
                    .entity(Map.of("error", "Password is required")).build();
        }

        boolean changed = userService.changePassword(current.getUsername(), newPassword);
        if (!changed) {
            return Response.status(Response.Status.NOT_FOUND)
                    .entity(Map.of("error", "Unable to update password")).build();
        }

        return Response.noContent().build();
    }

    @POST
    @Path("/username")
    @Consumes(MediaType.APPLICATION_FORM_URLENCODED)
    public Response changeUsername(@FormParam("username") String newUsername) {
        AppUser current = userContext.getCurrentUser();
        if (current == null) {
            return Response.status(Response.Status.UNAUTHORIZED)
                    .entity(Map.of("error", "Authentication required")).build();
        }

        if (newUsername == null || newUsername.isBlank()) {
            return Response.status(Response.Status.BAD_REQUEST)
                    .entity(Map.of("error", "Username is required")).build();
        }

        String trimmed = newUsername.trim();
        if (trimmed.equals(current.getUsername())) {
            return Response.noContent().build();
        }

        if (userService.usernameExists(trimmed)) {
            return Response.status(Response.Status.CONFLICT)
                    .entity(Map.of("error", "Username already in use")).build();
        }

        boolean updated = userService.changeUsername(current.getUsername(), trimmed);
        if (!updated) {
            return Response.status(Response.Status.INTERNAL_SERVER_ERROR)
                    .entity(Map.of("error", "Unable to update username")).build();
        }

        return Response.noContent().build();
    }

    @POST
    @Path("/email")
    @Consumes(MediaType.APPLICATION_FORM_URLENCODED)
    public Response changeEmail(@FormParam("email") String newEmail) {
        AppUser current = userContext.getCurrentUser();
        if (current == null) {
            return Response.status(Response.Status.UNAUTHORIZED)
                    .entity(Map.of("error", "Authentication required")).build();
        }

        if (newEmail == null || newEmail.isBlank()) {
            return Response.status(Response.Status.BAD_REQUEST)
                    .entity(Map.of("error", "Email is required")).build();
        }

        String trimmed = newEmail.trim();
        if (!userService.isValidEmail(trimmed)) {
            return Response.status(Response.Status.BAD_REQUEST)
                    .entity(Map.of("error", "Invalid email address")).build();
        }

        boolean updated = userService.changeEmail(current.getUsername(), trimmed);
        if (!updated) {
            return Response.status(Response.Status.INTERNAL_SERVER_ERROR)
                    .entity(Map.of("error", "Unable to update email")).build();
        }

        return Response.noContent().build();
    }
}
