package com.app.karaoke.controller;

import com.app.karaoke.model.AppUser;
import com.app.karaoke.model.RoomDTO;
import com.app.karaoke.security.Authorizer;
import com.app.karaoke.security.RequestUserContext;
import com.app.karaoke.service.RoomService;
import com.app.karaoke.service.TokenService;
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

import java.util.List;
import java.util.Map;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

@Path("/rooms")
@Produces(MediaType.APPLICATION_JSON)
public class RoomController {

    private static final Logger LOG = LogManager.getLogger(RoomController.class);

    @Inject
    RequestUserContext userContext;

    @Inject
    RoomService roomService;

    @Inject
    Authorizer authorizer;

    @Inject
    UserService userService;

    @Inject
    TokenService tokenService;

    @POST
    @Path("/create")
    @Consumes(MediaType.APPLICATION_FORM_URLENCODED)
    public Response createRoom(@FormParam("roomName") String roomName) {
        LOG.debug("createRoom called with roomName={}", roomName);
        AppUser user = userContext.getCurrentUser();
        if (user == null || !authorizer.canCreateRoom(user)) {
            LOG.warn("Unauthorized room creation attempt");
            return Response.status(Response.Status.FORBIDDEN).entity("Admins only").build();
        }

        String safeName = roomName == null || roomName.isBlank() ? "Unnamed Room" : roomName.trim();
        RoomDTO created = roomService.createRoom(safeName, user.getUsername());
        LOG.info("Room created {} by {}", safeName, user.getUsername());
        return Response.ok(created).build();
    }

    @POST
    @Path("/{roomId}/enter")
    public Response enterRoom(@PathParam("roomId") String roomId) {
        LOG.debug("enterRoom called for {}", roomId);
        AppUser user = userContext.getCurrentUser();
        if (user == null) {
            LOG.warn("Unauthorized enter attempt for room {}", roomId);
            return Response.status(Response.Status.UNAUTHORIZED).entity("Authentication required").build();
        }

        return roomService.enterRoom(roomId)
                .map(Response::ok)
                .orElseGet(() -> {
                    LOG.warn("Room {} not found", roomId);
                    return Response.status(Response.Status.NOT_FOUND).entity("Room not found");
                })
                .build();
    }

    @POST
    @Path("/{roomId}/song-request")
    @Consumes(MediaType.APPLICATION_FORM_URLENCODED)
    public Response songRequest(@PathParam("roomId") String roomId,
            @FormParam("song") String song) {
        LOG.debug("songRequest in room {}: {}", roomId, song);
        AppUser user = userContext.getCurrentUser();
        if (user == null || !authorizer.canRequestSong(user)) {
            LOG.warn("Song request denied for user {}", user);
            return Response.status(Response.Status.FORBIDDEN).entity("Song requests require at least USER role").build();
        }

        return roomService.addSongRequest(roomId, song, user)
                .map(Response::ok)
                .orElseGet(() -> {
                    LOG.warn("Song request failed for room {}", roomId);
                    return Response.status(Response.Status.BAD_REQUEST).entity("Song is required or room missing");
                })
                .build();
    }

    @POST
    @Path("/{roomId}/invite")
    @Consumes(MediaType.APPLICATION_FORM_URLENCODED)
    public Response invite(@PathParam("roomId") String roomId,
            @FormParam("guest") String guest) {
        LOG.debug("invite called for room {} guest {}", roomId, guest);
        AppUser user = userContext.getCurrentUser();
        if (user == null) {
            LOG.warn("Unauthorized invite attempt for room {}", roomId);
            return Response.status(Response.Status.UNAUTHORIZED).entity("Authentication required").build();
        }

        RoomDTO room = roomService.getRoom(roomId).orElse(null);
        if (room == null) {
            LOG.warn("Invite failed; room {} not found", roomId);
            return Response.status(Response.Status.NOT_FOUND).entity("Room not found").build();
        }

        if (!authorizer.canModifyRoom(user, room.getOwner())) {
            LOG.warn("User {} cannot invite for room {}", user.getUsername(), roomId);
            return Response.status(Response.Status.FORBIDDEN).entity("Not permitted").build();
        }

        AppUser tempUser = userService.createTemporaryUser(roomId);
        String token = tokenService.issueInviteToken(tempUser, roomId);
        roomService.inviteGuest(roomId, guest);

        Map<String, Object> inviteResponse = Map.of(
                "roomId", roomId,
                "guestName", tempUser.getUsername(),
                "token", token,
                "entryUrl", String.format("/kara-cafe/karaoke-room/room.html?id=%s&token=%s", roomId, token)
        );

        LOG.info("Invite issued for {} to room {} by {}", guest, roomId, user.getUsername());
        return Response.ok(inviteResponse).build();
    }

    @GET
    @Path("/list")
    public Response listRooms() {
        LOG.debug("listRooms called");
        AppUser user = userContext.getCurrentUser();
        if (user == null) {
            LOG.warn("Unauthorized room list request");
            return Response.status(Response.Status.UNAUTHORIZED).entity("Authentication required").build();
        }

        List<RoomDTO> rooms = roomService.listRoomsFor(user.getRole(), user.getUsername());
        LOG.info("Rooms listed for {}", user.getUsername());
        Map<String, Object> payload = Map.of(
                "rooms", rooms,
                "currentUser", Map.of(
                        "username", user.getUsername(),
                        "role", user.getRole().name()
                )
        );

        return Response.ok(payload).build();
    }

    @DELETE
    @Path("/{roomId}")
    public Response deleteRoom(@PathParam("roomId") String roomId) {
        LOG.debug("deleteRoom called for {}", roomId);
        AppUser user = userContext.getCurrentUser();
        if (user == null) {
            LOG.warn("Unauthorized delete attempt for {}", roomId);
            return Response.status(Response.Status.UNAUTHORIZED).entity("Authentication required").build();
        }

        RoomDTO room = roomService.getRoom(roomId).orElse(null);
        if (room == null) {
            LOG.warn("Room {} not found for deletion", roomId);
            return Response.status(Response.Status.NOT_FOUND).entity("Room not found").build();
        }

        if (!authorizer.canModifyRoom(user, room.getOwner())) {
            LOG.warn("User {} forbidden from deleting {}", user.getUsername(), roomId);
            return Response.status(Response.Status.FORBIDDEN).entity("Not permitted").build();
        }

        roomService.deleteRoom(roomId);
        LOG.info("Room {} deleted by {}", roomId, user.getUsername());
        return Response.ok(Map.of("status", "deleted")).build();
    }
}
