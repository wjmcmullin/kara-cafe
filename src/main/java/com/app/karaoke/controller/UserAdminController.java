package com.app.karaoke.controller;

import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

import com.app.karaoke.model.AppUser;
import com.app.karaoke.security.Authorizer;
import com.app.karaoke.security.RequestUserContext;
import com.app.karaoke.service.UserService;

import jakarta.inject.Inject;
import jakarta.ws.rs.Consumes;
import jakarta.ws.rs.DELETE;
import jakarta.ws.rs.FormParam;
import jakarta.ws.rs.GET;
import jakarta.ws.rs.POST;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.PathParam;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;

@Path("/admin/users")
@Produces(MediaType.APPLICATION_JSON)
public class UserAdminController {

    @Inject
    private RequestUserContext userContext;

    @Inject
    private UserService userService;

    @Inject
    private Authorizer authorizer;

    @GET
    public Response listUsers() {
        AppUser current = userContext.getCurrentUser();
        if (current == null || !current.isSuperAdmin()) {
            return forbidden();
        }

        List<Map<String, Object>> payload = userService.listUsers().stream()
                .map(AppUser::toPayload)
                .collect(Collectors.toList());

        return Response.ok(payload).build();
    }

    @POST
    @Path("/{username}/password")
    @Consumes(MediaType.APPLICATION_FORM_URLENCODED)
    public Response changePassword(@PathParam("username") String username,
            @FormParam("password") String newPassword) {
        AppUser current = userContext.getCurrentUser();
        if (current == null) {
            return Response.status(Response.Status.UNAUTHORIZED).build();
        }

        boolean allowed = current.isSuperAdmin() || current.getUsername().equals(username);
        if (!allowed) {
            return forbidden();
        }

        if (newPassword == null || newPassword.isBlank()) {
            return Response.status(Response.Status.BAD_REQUEST)
                    .entity(Map.of("error", "Password is required")).build();
        }

        boolean updated = userService.changePassword(username, newPassword);
        if (!updated) {
            return Response.status(Response.Status.NOT_FOUND)
                    .entity(Map.of("error", "User not found")).build();
        }

        return Response.noContent().build();
    }

    @DELETE
    @Path("/{username}")
    public Response deleteUser(@PathParam("username") String username) {
        AppUser current = userContext.getCurrentUser();
        if (current == null || !current.isSuperAdmin()) {
            return forbidden();
        }

        boolean deleted = userService.deleteUser(username);
        if (!deleted) {
            return Response.status(Response.Status.BAD_REQUEST)
                    .entity(Map.of("error", "Cannot delete this user")).build();
        }

        return Response.noContent().build();
    }

    private Response forbidden() {
        return Response.status(Response.Status.FORBIDDEN)
                .entity(Map.of("error", "Access denied")).build();
    }
}
