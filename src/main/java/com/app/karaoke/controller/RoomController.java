package com.app.karaoke.controller;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

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
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.QueryParam;
import jakarta.ws.rs.WebApplicationException;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;

@jakarta.ws.rs.Path("/rooms")
@Produces(MediaType.APPLICATION_JSON)
public class RoomController {

    private static final Logger LOG = LogManager.getLogger(RoomController.class);
    private static final String MEDIA_DIR_ENV = "KARAOKE_VIDEO_DIR";
    private static final Path DEFAULT_MEDIA_ROOT = resolveMediaRoot();
    private static Path MEDIA_ROOT = DEFAULT_MEDIA_ROOT;
    private static final Set<String> VIDEO_EXTENSIONS = Set.of("mp4", "mkv", "avi", "mov", "webm");

    @Inject
    private RoomService roomService;

    @Inject
    private RequestUserContext requestUserContext;

    @Inject
    private Authorizer authorizer;

    @GET
    @jakarta.ws.rs.Path("/list")
    public Response listRooms() {
        AppUser currentUser = getCurrentUser();
        LOG.info("Listing rooms for {}", currentUser.getUsername());
        List<RoomDTO> rooms = roomService.listRoomsFor(currentUser.getRole(), currentUser.getUsername());
        return Response.ok(Map.of(
                "currentUser", toPayload(currentUser),
                "rooms", rooms
        )).build();
    }

    @POST
    @jakarta.ws.rs.Path("/create")
    @Consumes(MediaType.APPLICATION_FORM_URLENCODED)
    public Response createRoom(@FormParam("roomName") String roomName) {
        AppUser currentUser = getCurrentUser();
        LOG.info("Create room request by {}", currentUser.getUsername());

        if (!authorizer.canCreateRoom(currentUser)) {
            LOG.warn("Create room forbidden for {}", currentUser.getUsername());
            return Response.status(Response.Status.FORBIDDEN)
                    .entity(Map.of("error", "Only admins can create rooms")).build();
        }

        if (roomName == null || roomName.isBlank()) {
            LOG.warn("Create room missing name by {}", currentUser.getUsername());
            return Response.status(Response.Status.BAD_REQUEST)
                    .entity(Map.of("error", "Room name is required")).build();
        }

        if (currentUser.getRole() == Role.ADMIN && roomService.hasRoomOwnedBy(currentUser.getUsername())) {
            LOG.warn("Create room rejected because admin already owns one: {}", currentUser.getUsername());
            return Response.status(Response.Status.BAD_REQUEST)
                    .entity(Map.of("error", "Admin users can only own one room at a time")).build();
        }

        RoomDTO room = roomService.createRoom(roomName.trim(), currentUser.getUsername());
        LOG.info("Room {} created by {}", room.getId(), currentUser.getUsername());
        return Response.ok(room).build();
    }

    @DELETE
    @jakarta.ws.rs.Path("/{roomId}")
    public Response deleteRoom(@jakarta.ws.rs.PathParam("roomId") String roomId) {
        AppUser currentUser = getCurrentUser();
        LOG.info("Delete room {} requested by {}", roomId, currentUser.getUsername());
        Optional<RoomDTO> existing = roomService.getRoom(roomId);
        if (existing.isEmpty()) {
            LOG.warn("Delete room not found {}", roomId);
            return Response.status(Response.Status.NOT_FOUND)
                    .entity(Map.of("error", "Room not found")).build();
        }

        if (!authorizer.canModifyRoom(currentUser, existing.get().getOwner())) {
            LOG.warn("Delete room forbidden for {} on {}", currentUser.getUsername(), roomId);
            return Response.status(Response.Status.FORBIDDEN)
                    .entity(Map.of("error", "Not allowed to delete this room")).build();
        }

        roomService.deleteRoom(roomId);
        LOG.info("Room {} deleted by {}", roomId, currentUser.getUsername());
        return Response.noContent().build();
    }

    @POST
    @jakarta.ws.rs.Path("/{roomId}/invite")
    @Consumes(MediaType.APPLICATION_FORM_URLENCODED)
    public Response inviteGuest(@jakarta.ws.rs.PathParam("roomId") String roomId, @FormParam("guest") String guest) {
        AppUser currentUser = getCurrentUser();
        LOG.info("Invite guest {} to room {} by {}", guest, roomId, currentUser.getUsername());
        Optional<RoomDTO> existing = roomService.getRoom(roomId);

        if (existing.isEmpty()) {
            LOG.warn("Invite guest failing, room {} not found", roomId);
            return Response.status(Response.Status.NOT_FOUND)
                    .entity(Map.of("error", "Room not found")).build();
        }

        if (!authorizer.canModifyRoom(currentUser, existing.get().getOwner())) {
            LOG.warn("Invite guest forbidden for {} on {}", currentUser.getUsername(), roomId);
            return Response.status(Response.Status.FORBIDDEN)
                    .entity(Map.of("error", "Not allowed to invite for this room")).build();
        }

        if (guest == null || guest.isBlank()) {
            LOG.warn("Invite guest missing username for room {}", roomId);
            return Response.status(Response.Status.BAD_REQUEST)
                    .entity(Map.of("error", "Guest username is required")).build();
        }

        Optional<RoomDTO> updated = roomService.inviteGuest(roomId, guest.trim());
        if (updated.isEmpty()) {
            LOG.warn("Invite guest {} failed for room {}", guest, roomId);
            return Response.status(Response.Status.BAD_REQUEST)
                    .entity(Map.of("error", "Unable to invite guest")).build();
        }

        String entryUrl = String.format("/kara-cafe/karaoke-room/room?id=%s", roomId);
        LOG.info("Invite guest {} success for room {}", guest, roomId);
        return Response.ok(Map.of("entryUrl", entryUrl)).build();
    }

    @POST
    @jakarta.ws.rs.Path("/{roomId}/enter")
    public Response enterRoom(@jakarta.ws.rs.PathParam("roomId") String roomId) {
        AppUser currentUser = getCurrentUser();
        LOG.info("Enter room {}", roomId);
        Optional<RoomDTO> room = roomService.enterRoom(roomId, currentUser.getUsername());
        if (room.isEmpty()) {
            LOG.warn("Enter room not found {}", roomId);
            return Response.status(Response.Status.NOT_FOUND)
                    .entity(Map.of("error", "Room not found")).build();
        }
        return Response.ok(room.get()).build();
    }

    @POST
    @jakarta.ws.rs.Path("/{roomId}/leave")
    public Response leaveRoom(@jakarta.ws.rs.PathParam("roomId") String roomId) {
        AppUser currentUser = getCurrentUser();
        LOG.info("Leave room {}", roomId);
        Optional<RoomDTO> room = roomService.leaveRoom(roomId, currentUser.getUsername());
        if (room.isEmpty()) {
            LOG.warn("Leave room not found {}", roomId);
            return Response.status(Response.Status.NOT_FOUND)
                    .entity(Map.of("error", "Room not found")).build();
        }
        return Response.ok(room.get()).build();
    }

    @GET
    @jakarta.ws.rs.Path("/{roomId}/search")
    public Response searchRoomVideos(@jakarta.ws.rs.PathParam("roomId") String roomId, @QueryParam("dir") String directory) {
        getCurrentUser();
        LOG.info("Search videos for room {} in directory {}", roomId, directory);
        Optional<RoomDTO> existing = roomService.getRoom(roomId);
        if (existing.isEmpty()) {
            LOG.warn("Search videos room {} not found", roomId);
            return Response.status(Response.Status.NOT_FOUND)
                    .entity(Map.of("error", "Room not found")).build();
        }

        Path targetDir = resolveDirectory(directory);
        try (Stream<Path> stream = Files.list(targetDir)) {
            List<Map<String, Object>> entries = stream
                    .filter(Files::isRegularFile)
                    .filter(this::isSupportedVideo)
                    .map(path -> toEntry(targetDir, path))
                    .filter(Objects::nonNull)
                    .sorted(Comparator.comparing(entry -> entry.get("name").toString()))
                    .collect(Collectors.toList());

            LOG.info("Found {} video(s) for room {}", entries.size(), roomId);
            return Response.ok(Map.of(
                    "directory", targetDir.toString(),
                    "entries", entries
            )).build();
        } catch (IOException e) {
            LOG.warn("Unable to list karaoke videos in {}: {}", targetDir, e.getMessage());
            return Response.status(Response.Status.INTERNAL_SERVER_ERROR)
                    .entity(Map.of("error", "Unable to list karaoke videos")).build();
        }
    }

    @GET
    @jakarta.ws.rs.Path("/config/video-dir")
    public Response getVideoDirectory() {
        AppUser caller = getCurrentUser();
        LOG.info("Video directory requested by {}", caller.getUsername());
        return Response.ok(Map.of("directory", MEDIA_ROOT.toString())).build();
    }

    @POST
    @jakarta.ws.rs.Path("/{roomId}/song-request")
    @Consumes(MediaType.APPLICATION_FORM_URLENCODED)
    public Response requestSong(@jakarta.ws.rs.PathParam("roomId") String roomId, @FormParam("song") String song) {
        AppUser currentUser = getCurrentUser();
        LOG.info("Song request for room {} by {}", roomId, currentUser.getUsername());

        if (!authorizer.canRequestSong(currentUser)) {
            LOG.warn("Song request forbidden for {}", currentUser.getUsername());
            return Response.status(Response.Status.FORBIDDEN)
                    .entity(Map.of("error", "User cannot request songs")).build();
        }

        Optional<RoomDTO> room = roomService.addSongRequest(roomId, song, currentUser);
        if (room.isEmpty()) {
            LOG.warn("Song request failed for room {} song {}", roomId, song);
            return Response.status(Response.Status.BAD_REQUEST)
                    .entity(Map.of("error", "Unable to request song")).build();
        }

        LOG.info("Song {} requested by {} in room {}", song, currentUser.getUsername(), roomId);
        return Response.ok(room.get()).build();
    }

    private static Path resolveMediaRoot() {
        String overridePath = System.getenv(MEDIA_DIR_ENV);
        if (overridePath != null && !overridePath.isBlank()) {
            LOG.info("Using configured media directory: {}", overridePath);
            return Paths.get(overridePath).toAbsolutePath().normalize();
        }
        Path defaultPath = Paths.get("/karaoke-videos").toAbsolutePath().normalize();
        LOG.info("Using default media directory: {}", defaultPath);
        return defaultPath;
    }

    private Path resolveDirectory(String directory) {
        if (directory == null || directory.isBlank()) {
            return MEDIA_ROOT;
        }
        Path candidate = Paths.get(directory).toAbsolutePath().normalize();
        if (!candidate.startsWith(MEDIA_ROOT)) {
            LOG.warn("Requested directory {} is outside media root, falling back to {}", directory, MEDIA_ROOT);
            return MEDIA_ROOT;
        }
        return candidate;
    }

    private boolean isSupportedVideo(Path path) {
        String filename = path.getFileName().toString();
        int dotIndex = filename.lastIndexOf('.');
        if (dotIndex < 0 || dotIndex == filename.length() - 1) {
            return false;
        }
        String extension = filename.substring(dotIndex + 1).toLowerCase();
        return VIDEO_EXTENSIONS.contains(extension);
    }

    private Map<String, Object> toEntry(Path root, Path path) {
        try {
            return Map.of(
                    "name", path.getFileName().toString(),
                    "relativePath", root.relativize(path).toString(),
                    "sizeBytes", Files.size(path)
            );
        } catch (IOException e) {
            LOG.warn("Unable to read file size for {}: {}", path, e.getMessage());
            return null;
        }
    }

    private AppUser getCurrentUser() {
        AppUser user = requestUserContext.getCurrentUser();
        if (user == null) {
            LOG.warn("Request made without authentication");
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

    // helpers for tests
    static void overrideMediaRoot(Path path) {
        MEDIA_ROOT = (path != null) ? path : DEFAULT_MEDIA_ROOT;
    }

    static Path getMediaRoot() {
        return MEDIA_ROOT;
    }
}
