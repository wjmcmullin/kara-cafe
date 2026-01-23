package com.app.karaoke.controller;

import java.util.List;
import java.util.Map;
import java.util.Optional;

import com.app.karaoke.model.AppUser;
import com.app.karaoke.model.Role;
import com.app.karaoke.model.RoomDTO;
import com.app.karaoke.security.Authorizer;
import com.app.karaoke.security.RequestUserContext;
import com.app.karaoke.service.RoomService;

import jakarta.inject.Inject;
import jakarta.ws.rs.Consumes;
import jakarta.ws.rs.DELETE;
import jakarta.ws.rs.FormParam;
import jakarta.ws.rs.GET;
import jakarta.ws.rs.POST;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.PathParam;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.WebApplicationException;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;

@Path("/rooms")
@Produces(MediaType.APPLICATION_JSON)
public class RoomController {

    @Inject
    private RoomService roomService;

    @Inject
    private RequestUserContext requestUserContext;

    @Inject
    private Authorizer authorizer;

    @GET
    @Path("/list")
    public Response listRooms() {
        AppUser currentUser = getCurrentUser();
        List<RoomDTO> rooms = roomService.listRoomsFor(currentUser.getRole(), currentUser.getUsername());
        return Response.ok(Map.of(
                "currentUser", toPayload(currentUser),
                "rooms", rooms
        )).build();
    }

    @POST
    @Path("/create")
    @Consumes(MediaType.APPLICATION_FORM_URLENCODED)
    public Response createRoom(@FormParam("roomName") String roomName) {
        AppUser currentUser = getCurrentUser();

        if (!authorizer.canCreateRoom(currentUser)) {
            return Response.status(Response.Status.FORBIDDEN)
                    .entity(Map.of("error", "Only admins can create rooms")).build();
        }

        if (roomName == null || roomName.isBlank()) {
            return Response.status(Response.Status.BAD_REQUEST)
                    .entity(Map.of("error", "Room name is required")).build();
        }

        if (currentUser.getRole() == Role.ADMIN && roomService.hasRoomOwnedBy(currentUser.getUsername())) {
            return Response.status(Response.Status.BAD_REQUEST)
                    .entity(Map.of("error", "Admin users can only own one room at a time")).build();
        }

        RoomDTO room = roomService.createRoom(roomName.trim(), currentUser.getUsername());
        return Response.ok(room).build();
    }

    @DELETE
    @Path("/{roomId}")
    public Response deleteRoom(@PathParam("roomId") String roomId) {
        AppUser currentUser = getCurrentUser();
        Optional<RoomDTO> existing = roomService.getRoom(roomId);
        if (existing.isEmpty()) {
            return Response.status(Response.Status.NOT_FOUND)
                    .entity(Map.of("error", "Room not found")).build();
        }

        if (!authorizer.canModifyRoom(currentUser, existing.get().getOwner())) {
            return Response.status(Response.Status.FORBIDDEN)
                    .entity(Map.of("error", "Not allowed to delete this room")).build();
        }

        roomService.deleteRoom(roomId);
        return Response.noContent().build();
    }

    @POST
    @Path("/{roomId}/invite")
    @Consumes(MediaType.APPLICATION_FORM_URLENCODED)
    public Response inviteGuest(@PathParam("roomId") String roomId, @FormParam("guest") String guest) {
        AppUser currentUser = getCurrentUser();
        Optional<RoomDTO> existing = roomService.getRoom(roomId);

        if (existing.isEmpty()) {
            return Response.status(Response.Status.NOT_FOUND)
                    .entity(Map.of("error", "Room not found")).build();
        }

        if (!authorizer.canModifyRoom(currentUser, existing.get().getOwner())) {
            return Response.status(Response.Status.FORBIDDEN)
                    .entity(Map.of("error", "Not allowed to invite for this room")).build();
        }

        if (guest == null || guest.isBlank()) {
            return Response.status(Response.Status.BAD_REQUEST)
                    .entity(Map.of("error", "Guest username is required")).build();
        }

        Optional<RoomDTO> updated = roomService.inviteGuest(roomId, guest.trim());
        if (updated.isEmpty()) {
            return Response.status(Response.Status.BAD_REQUEST)
                    .entity(Map.of("error", "Unable to invite guest")).build();
        }

        String entryUrl = String.format("/kara-cafe/karaoke-room/room.html?id=%s", roomId);
        return Response.ok(Map.of("entryUrl", entryUrl)).build();
    }

    @POST
    @Path("/{roomId}/enter")
    public Response enterRoom(@PathParam("roomId") String roomId) {
        getCurrentUser(); // ensure authentication
        Optional<RoomDTO> room = roomService.enterRoom(roomId);
        if (room.isEmpty()) {
            return Response.status(Response.Status.NOT_FOUND)
                    .entity(Map.of("error", "Room not found")).build();
        }
        return Response.ok(room.get()).build();
    }

    @POST
    @Path("/{roomId}/song-request")
    @Consumes(MediaType.APPLICATION_FORM_URLENCODED)
    public Response requestSong(@PathParam("roomId") String roomId, @FormParam("song") String song) {
        AppUser currentUser = getCurrentUser();

        if (!authorizer.canRequestSong(currentUser)) {
            return Response.status(Response.Status.FORBIDDEN)
                    .entity(Map.of("error", "User cannot request songs")).build();
        }

        Optional<RoomDTO> room = roomService.addSongRequest(roomId, song, currentUser);
        if (room.isEmpty()) {
            return Response.status(Response.Status.BAD_REQUEST)
                    .entity(Map.of("error", "Unable to request song")).build();
        }

        return Response.ok(room.get()).build();
    }

    private AppUser getCurrentUser() {
        AppUser user = requestUserContext.getCurrentUser();
        if (user == null) {
            throw new WebApplicationException(Response.status(Response.Status.UNAUTHORIZED)
                    .entity(Map.of("error", "Authentication required")).build());
        }
        return user;
    }

    private Map<String, Object> toPayload(AppUser user) {
        return Map.of(
                "username", user.getUsername(),
                "role", user.getRole().name()
        );
    }

    public Response invite(String roomId, String guest) {
        return inviteGuest(roomId, guest);
    }

}
