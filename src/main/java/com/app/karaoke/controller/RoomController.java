package com.app.karaoke.controller;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Base64;
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
import com.app.karaoke.service.TokenService;
import com.google.zxing.BarcodeFormat;
import com.google.zxing.WriterException;
import com.google.zxing.client.j2se.MatrixToImageWriter;
import com.google.zxing.common.BitMatrix;
import com.google.zxing.qrcode.QRCodeWriter;

import jakarta.inject.Inject;
import jakarta.ws.rs.Consumes;
import jakarta.ws.rs.DELETE;
import jakarta.ws.rs.FormParam;
import jakarta.ws.rs.GET;
import jakarta.ws.rs.POST;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.QueryParam;
import jakarta.ws.rs.WebApplicationException;
import jakarta.ws.rs.core.Context;
import jakarta.ws.rs.core.HttpHeaders;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;
import jakarta.ws.rs.core.UriInfo;

@jakarta.ws.rs.Path("/rooms")
@Produces(MediaType.APPLICATION_JSON)
public class RoomController {

    private static final Logger LOG = LogManager.getLogger(RoomController.class);
    private static final String MEDIA_DIR_ENV = "KARAOKE_VIDEO_DIR";
    private static final String PUBLIC_BASE_URL_ENV = "KARA_PUBLIC_BASE_URL";
    private static final String BEARER = "Bearer ";
    private static final Path DEFAULT_MEDIA_ROOT = resolveMediaRoot();
    private static Path MEDIA_ROOT = DEFAULT_MEDIA_ROOT;
    private static final Set<String> VIDEO_EXTENSIONS = Set.of("mp4", "mkv", "avi", "mov", "webm");

    @Inject
    private RoomService roomService;

    @Inject
    private RequestUserContext requestUserContext;

    @Inject
    private Authorizer authorizer;

    @Inject
    private TokenService tokenService;

    @Context
    private HttpHeaders headers;

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
    public Response inviteGuest(
            @jakarta.ws.rs.PathParam("roomId") String roomId,
            @FormParam("guest") String guestEmail,
            @Context UriInfo uriInfo) {
        AppUser currentUser = getCurrentUser();
        LOG.info("Invite request for room {} by {}", roomId, currentUser.getUsername());
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

        if (guestEmail == null || guestEmail.isBlank()) {
            return Response.status(Response.Status.BAD_REQUEST)
                    .entity(Map.of("error", "Invite email is required")).build();
        }

        Optional<RoomDTO> updated = roomService.inviteGuest(roomId, guestEmail.trim());
        if (updated.isEmpty()) {
            LOG.warn("Invite guest {} failed for room {}", guestEmail, roomId);
            return Response.status(Response.Status.BAD_REQUEST)
                    .entity(Map.of("error", "Unable to prepare invite")).build();
        }

        Response inviteResponse = buildInvitePayloadResponse(roomId, uriInfo);
        if (inviteResponse.getStatus() != Response.Status.OK.getStatusCode()) {
            return inviteResponse;
        }

        @SuppressWarnings("unchecked")
        Map<String, Object> payload = (Map<String, Object>) inviteResponse.getEntity();

        if (!payload.containsKey("qrCodeDataUrl")) {
            return Response.ok(payload).build();
        }

        java.util.HashMap<String, Object> withEmail = new java.util.HashMap<>(payload);
        withEmail.put("inviteEmail", guestEmail.trim());
        withEmail.put("delivery", "pending-email-integration");
        return Response.ok(withEmail).build();
    }

    @GET
    @jakarta.ws.rs.Path("/{roomId}/invite")
    public Response getRoomInvite(
            @jakarta.ws.rs.PathParam("roomId") String roomId,
            @Context UriInfo uriInfo) {
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

        return buildInvitePayloadResponse(roomId, uriInfo);
    }

    // helper overload used by tests
    public Response inviteGuest(String roomId, String guestEmail) {
        return inviteGuest(roomId, guestEmail, null);
    }

    @POST
    @jakarta.ws.rs.Path("/{roomId}/enter")
    public Response enterRoom(@jakarta.ws.rs.PathParam("roomId") String roomId) {
        AppUser currentUser = getCurrentUser();
        if (requiresGuestNickname(currentUser)) {
            return Response.status(Response.Status.BAD_REQUEST)
                    .entity(Map.of("error", "Please enter a nickname before joining the room"))
                    .build();
        }

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
    @jakarta.ws.rs.Path("/{roomId}")
    public Response getRoom(@jakarta.ws.rs.PathParam("roomId") String roomId) {
        getCurrentUser();
        Optional<RoomDTO> room = roomService.getRoom(roomId);
        if (room.isEmpty()) {
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
        if (requiresGuestNickname(currentUser)) {
            return Response.status(Response.Status.BAD_REQUEST)
                    .entity(Map.of("error", "Please enter a nickname before requesting songs"))
                    .build();
        }

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

    @POST
    @jakarta.ws.rs.Path("/{roomId}/queue/start-next")
    public Response startNextSong(@jakarta.ws.rs.PathParam("roomId") String roomId) {
        AppUser currentUser = getCurrentUser();
        Optional<RoomDTO> existing = roomService.getRoom(roomId);
        if (existing.isEmpty()) {
            return Response.status(Response.Status.NOT_FOUND)
                    .entity(Map.of("error", "Room not found")).build();
        }

        if (!authorizer.canModifyRoom(currentUser, existing.get().getOwner())) {
            return Response.status(Response.Status.FORBIDDEN)
                    .entity(Map.of("error", "Not allowed to control queue for this room")).build();
        }

        Optional<RoomDTO> room = roomService.startNextSong(roomId);
        return room.map(Response::ok)
                .orElseGet(() -> Response.status(Response.Status.BAD_REQUEST)
                        .entity(Map.of("error", "Unable to start next song")))
                .build();
    }

    @POST
    @jakarta.ws.rs.Path("/{roomId}/queue/finish-current")
    public Response finishCurrentSong(@jakarta.ws.rs.PathParam("roomId") String roomId) {
        AppUser currentUser = getCurrentUser();
        Optional<RoomDTO> existing = roomService.getRoom(roomId);
        if (existing.isEmpty()) {
            return Response.status(Response.Status.NOT_FOUND)
                    .entity(Map.of("error", "Room not found")).build();
        }

        if (!authorizer.canModifyRoom(currentUser, existing.get().getOwner())) {
            return Response.status(Response.Status.FORBIDDEN)
                    .entity(Map.of("error", "Not allowed to control queue for this room")).build();
        }

        Optional<RoomDTO> room = roomService.finishCurrentSong(roomId);
        return room.map(Response::ok)
                .orElseGet(() -> Response.status(Response.Status.BAD_REQUEST)
                        .entity(Map.of("error", "Unable to finish current song")))
                .build();
    }

    @GET
    @jakarta.ws.rs.Path("/{roomId}/media")
    @Produces("video/mp4")
    public Response streamRoomMedia(
            @jakarta.ws.rs.PathParam("roomId") String roomId,
            @QueryParam("path") String relativePath) {
        getCurrentUser();
        Optional<RoomDTO> existing = roomService.getRoom(roomId);
        if (existing.isEmpty()) {
            return Response.status(Response.Status.NOT_FOUND)
                    .entity(Map.of("error", "Room not found")).build();
        }

        Path mediaPath = resolveVideoPath(relativePath);
        if (mediaPath == null) {
            return Response.status(Response.Status.BAD_REQUEST)
                    .entity(Map.of("error", "Invalid media path")).build();
        }

        if (!Files.exists(mediaPath) || !Files.isRegularFile(mediaPath)) {
            return Response.status(Response.Status.NOT_FOUND)
                    .entity(Map.of("error", "Media file not found")).build();
        }

        if (!isSupportedVideo(mediaPath)) {
            return Response.status(Response.Status.BAD_REQUEST)
                    .entity(Map.of("error", "Unsupported media type")).build();
        }

        try {
            String mediaType = Files.probeContentType(mediaPath);
            if (mediaType == null) {
                mediaType = "application/octet-stream";
            }

            jakarta.ws.rs.core.StreamingOutput stream = output -> {
                try (InputStream input = Files.newInputStream(mediaPath)) {
                    byte[] buffer = new byte[16 * 1024];
                    int read;
                    while ((read = input.read(buffer)) != -1) {
                        output.write(buffer, 0, read);
                    }
                    output.flush();
                } catch (IOException ioException) {
                    if (!isClientAbort(ioException)) {
                        throw ioException;
                    }
                    LOG.debug("Client disconnected while streaming {}", mediaPath);
                }
            };

            return Response.ok(stream, mediaType)
                    .header("Content-Length", Files.size(mediaPath))
                    .build();
        } catch (IOException e) {
            return Response.status(Response.Status.INTERNAL_SERVER_ERROR)
                    .entity(Map.of("error", "Unable to read media file")).build();
        }
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

    private Path resolveVideoPath(String relativePath) {
        if (relativePath == null || relativePath.isBlank()) {
            return null;
        }

        Path candidate = MEDIA_ROOT.resolve(relativePath).normalize();
        if (!candidate.startsWith(MEDIA_ROOT)) {
            LOG.warn("Rejected media path outside root: {}", relativePath);
            return null;
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

    private boolean isClientAbort(IOException e) {
        String message = e.getMessage();
        if (message == null) {
            return false;
        }
        String normalized = message.toLowerCase();
        return normalized.contains("broken pipe") || normalized.contains("connection reset");
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

    private Response buildInvitePayloadResponse(String roomId, UriInfo uriInfo) {
        if (tokenService == null) {
            return Response.ok(Map.of("entryUrl", "/kara-cafe/karaoke-room/room?id=" + roomId)).build();
        }

        String guestName = roomService.nextGuestDisplayName(roomId);
        String inviteToken = tokenService.issueGuestInviteToken(roomId, guestName);
        String entryPath = String.format("/kara-cafe/karaoke-room/join.html?id=%s&token=%s", roomId, inviteToken);
        String inviteUrl = resolvePublicBaseUrl(uriInfo) + entryPath;

        String qrCodeDataUrl;
        try {
            qrCodeDataUrl = generateQrCodeDataUrl(inviteUrl);
        } catch (IOException | WriterException e) {
            LOG.error("Unable to generate QR code for room invite", e);
            return Response.status(Response.Status.INTERNAL_SERVER_ERROR)
                    .entity(Map.of("error", "Unable to generate invite QR code")).build();
        }

        return Response.ok(Map.of(
                "entryUrl", entryPath,
                "inviteUrl", inviteUrl,
                "qrCodeDataUrl", qrCodeDataUrl
        )).build();
    }

    private String resolvePublicBaseUrl(UriInfo uriInfo) {
        String configured = System.getenv(PUBLIC_BASE_URL_ENV);
        if (configured != null && !configured.isBlank()) {
            return configured.replaceAll("/$", "");
        }

        if (uriInfo == null || uriInfo.getBaseUri() == null) {
            return "";
        }

        String requestBase = uriInfo.getBaseUri().toString();
        return requestBase.endsWith("/") ? requestBase.substring(0, requestBase.length() - 1) : requestBase;
    }

    private String generateQrCodeDataUrl(String value) throws WriterException, IOException {
        BitMatrix matrix = new QRCodeWriter().encode(value, BarcodeFormat.QR_CODE, 300, 300);
        try (ByteArrayOutputStream out = new ByteArrayOutputStream()) {
            MatrixToImageWriter.writeToStream(matrix, "PNG", out);
            String encoded = Base64.getEncoder().encodeToString(out.toByteArray());
            return "data:image/png;base64," + encoded;
        }
    }

    private boolean requiresGuestNickname(AppUser currentUser) {
        Optional<TokenService.TokenInfo> tokenInfo = currentTokenInfo();
        if (tokenInfo.isEmpty() || !tokenInfo.get().isInviteOnly()) {
            return false;
        }

        return tokenService.isPlaceholderGuestNickname(currentUser.getUsername());
    }

    private Optional<TokenService.TokenInfo> currentTokenInfo() {
        if (headers == null) {
            return Optional.empty();
        }

        String authorization = headers.getHeaderString(HttpHeaders.AUTHORIZATION);
        if (authorization == null || !authorization.startsWith(BEARER)) {
            return Optional.empty();
        }

        String token = authorization.substring(BEARER.length()).trim();
        if (token.isEmpty()) {
            return Optional.empty();
        }

        return tokenService.getTokenInfo(token);
    }

    private Map<String, Object> toPayload(AppUser user) {
        return Map.of(
                "username", user.getUsername(),
                "role", user.getRole().name()
        );
    }

    public Response invite(String roomId, String guest) {
        return inviteGuest(roomId, guest, null);
    }

    // helpers for tests
    static void overrideMediaRoot(Path path) {
        MEDIA_ROOT = (path != null) ? path : DEFAULT_MEDIA_ROOT;
    }

    static Path getMediaRoot() {
        return MEDIA_ROOT;
    }
}
