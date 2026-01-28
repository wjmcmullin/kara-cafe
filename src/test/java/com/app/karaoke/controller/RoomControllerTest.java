package com.app.karaoke.controller;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.catchThrowable;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import static org.mockito.ArgumentMatchers.anyString;
import org.mockito.Mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import org.mockito.MockitoAnnotations;

import com.app.karaoke.model.AppUser;
import com.app.karaoke.model.Role;
import com.app.karaoke.model.Room;
import com.app.karaoke.model.RoomDTO;
import com.app.karaoke.security.Authorizer;
import com.app.karaoke.security.RequestUserContext;
import com.app.karaoke.service.RoomService;

import jakarta.ws.rs.WebApplicationException;
import jakarta.ws.rs.core.Response;

class RoomControllerTest {

    @Mock
    private RoomService roomService;

    @Mock
    private RequestUserContext requestUserContext;

    @Mock
    private Authorizer authorizer;

    private RoomController controller;

    private final AppUser adminUser = new AppUser("admin", Role.ADMIN, "pw");
    private final AppUser superAdminUser = new AppUser("super", Role.SUPER_ADMIN, "pw");

    private Path originalMediaRoot;

    @BeforeEach
    void setUp() {
        MockitoAnnotations.openMocks(this);
        controller = new RoomController();

        originalMediaRoot = RoomController.getMediaRoot();

        injectField("roomService", roomService);
        injectField("requestUserContext", requestUserContext);
        injectField("authorizer", authorizer);

        when(requestUserContext.getCurrentUser()).thenReturn(adminUser);
    }

    @AfterEach
    void restoreMediaRoot() {
        RoomController.overrideMediaRoot(originalMediaRoot);
    }

    @Test
    void listRoomsReturnsCurrentUserAndRooms() {
        when(requestUserContext.getCurrentUser()).thenReturn(superAdminUser);
        RoomDTO room = buildRoomDto("room-1", "Room", "owner");
        when(roomService.listRoomsFor(Role.SUPER_ADMIN, "super")).thenReturn(List.of(room));

        Response response = controller.listRooms();

        assertThat(response.getStatus()).isEqualTo(Response.Status.OK.getStatusCode());
        Map<String, Object> entity = cast(response.getEntity());
        assertThat(entity.get("currentUser")).isEqualTo(Map.of("username", "super", "role", "SUPER_ADMIN"));
        assertThat(entity.get("rooms")).asList().contains(room);
    }

    @Test
    void createRoomForbiddenIfNotAllowed() {
        when(authorizer.canCreateRoom(adminUser)).thenReturn(false);

        Response response = controller.createRoom("New");

        assertThat(response.getStatus()).isEqualTo(Response.Status.FORBIDDEN.getStatusCode());
        assertThat(response.getEntity()).isEqualTo(Map.of("error", "Only admins can create rooms"));
        verify(roomService, never()).createRoom(anyString(), anyString());
    }

    @Test
    void createRoomValidatesNameAndSingleRoomLimit() {
        when(authorizer.canCreateRoom(adminUser)).thenReturn(true);

        Response blank = controller.createRoom(" ");
        assertThat(blank.getStatus()).isEqualTo(Response.Status.BAD_REQUEST.getStatusCode());

        when(roomService.hasRoomOwnedBy("admin")).thenReturn(true);
        Response limit = controller.createRoom("Party");
        assertThat(limit.getStatus()).isEqualTo(Response.Status.BAD_REQUEST.getStatusCode());
        assertThat(limit.getEntity()).isEqualTo(Map.of("error", "Admin users can only own one room at a time"));
    }

    @Test
    void createRoomReturnsRoomWhenAllowed() {
        when(requestUserContext.getCurrentUser()).thenReturn(superAdminUser);
        when(authorizer.canCreateRoom(superAdminUser)).thenReturn(true);
        RoomDTO created = buildRoomDto("room-1", "Room", "super");
        when(roomService.createRoom("Room", "super")).thenReturn(created);

        Response response = controller.createRoom("  Room  ");

        assertThat(response.getStatus()).isEqualTo(Response.Status.OK.getStatusCode());
        assertThat(response.getEntity()).isEqualTo(created);
    }

    @Test
    void deleteRoomHandlesMissingRoom() {
        when(roomService.getRoom("missing")).thenReturn(Optional.empty());

        Response response = controller.deleteRoom("missing");
        assertThat(response.getStatus()).isEqualTo(Response.Status.NOT_FOUND.getStatusCode());
    }

    @Test
    void deleteRoomChecksPermissions() {
        RoomDTO dto = buildRoomDto("room", "Room", "owner");
        when(roomService.getRoom("room")).thenReturn(Optional.of(dto));
        when(authorizer.canModifyRoom(adminUser, "owner")).thenReturn(false);

        Response response = controller.deleteRoom("room");
        assertThat(response.getStatus()).isEqualTo(Response.Status.FORBIDDEN.getStatusCode());
    }

    @Test
    void deleteRoomDeletesWhenAllowed() {
        RoomDTO dto = buildRoomDto("room", "Room", "owner");
        when(roomService.getRoom("room")).thenReturn(Optional.of(dto));
        when(authorizer.canModifyRoom(adminUser, "owner")).thenReturn(true);

        Response response = controller.deleteRoom("room");
        assertThat(response.getStatus()).isEqualTo(Response.Status.NO_CONTENT.getStatusCode());
        verify(roomService).deleteRoom("room");
    }

    @Test
    void inviteGuestHandlesAllValidation() {
        when(roomService.getRoom("room")).thenReturn(Optional.empty());
        Response missing = controller.inviteGuest("room", "guest");
        assertThat(missing.getStatus()).isEqualTo(Response.Status.NOT_FOUND.getStatusCode());

        RoomDTO dto = buildRoomDto("room", "Room", "owner");
        when(roomService.getRoom("room")).thenReturn(Optional.of(dto));
        when(authorizer.canModifyRoom(adminUser, "owner")).thenReturn(false);
        Response forbidden = controller.inviteGuest("room", "guest");
        assertThat(forbidden.getStatus()).isEqualTo(Response.Status.FORBIDDEN.getStatusCode());

        when(authorizer.canModifyRoom(adminUser, "owner")).thenReturn(true);
        Response badGuest = controller.inviteGuest("room", " ");
        assertThat(badGuest.getStatus()).isEqualTo(Response.Status.BAD_REQUEST.getStatusCode());
    }

    @Test
    void inviteGuestHandlesInviteFailureAndSuccess() {
        RoomDTO dto = buildRoomDto("room", "Room", "owner");
        when(roomService.getRoom("room")).thenReturn(Optional.of(dto));
        when(authorizer.canModifyRoom(adminUser, "owner")).thenReturn(true);

        when(roomService.inviteGuest("room", "guest")).thenReturn(Optional.empty());
        Response failure = controller.inviteGuest("room", "guest");
        assertThat(failure.getStatus()).isEqualTo(Response.Status.BAD_REQUEST.getStatusCode());

        when(roomService.inviteGuest("room", "guest")).thenReturn(Optional.of(dto));
        Response success = controller.inviteGuest("room", "guest");
        assertThat(success.getStatus()).isEqualTo(Response.Status.OK.getStatusCode());
        assertThat(success.getEntity()).isEqualTo(Map.of("entryUrl", "/kara-cafe/karaoke-room/room?id=room"));
    }

    @Test
    void enterRoomHandlesMissingAndSuccess() {
        when(roomService.enterRoom("missing", "admin")).thenReturn(Optional.empty());
        Response missing = controller.enterRoom("missing");
        assertThat(missing.getStatus()).isEqualTo(Response.Status.NOT_FOUND.getStatusCode());

        RoomDTO dto = buildRoomDto("room", "Room", "owner");
        when(roomService.enterRoom("room", "admin")).thenReturn(Optional.of(dto));
        Response response = controller.enterRoom("room");
        assertThat(response.getStatus()).isEqualTo(Response.Status.OK.getStatusCode());
        assertThat(response.getEntity()).isEqualTo(dto);
    }

    @Test
    void leaveRoomHandlesMissingAndSuccess() {
        when(roomService.leaveRoom("missing", "admin")).thenReturn(Optional.empty());
        Response missing = controller.leaveRoom("missing");
        assertThat(missing.getStatus()).isEqualTo(Response.Status.NOT_FOUND.getStatusCode());

        RoomDTO dto = buildRoomDto("room", "Room", "owner");
        when(roomService.leaveRoom("room", "admin")).thenReturn(Optional.of(dto));
        Response response = controller.leaveRoom("room");
        assertThat(response.getStatus()).isEqualTo(Response.Status.OK.getStatusCode());
        assertThat(response.getEntity()).isEqualTo(dto);
    }

    @Test
    void searchRoomVideosListsAvailableEntries() throws Exception {
        Path tempDir = Files.createTempDirectory("karaoke");
        Path video = Files.createFile(tempDir.resolve("song.mp4"));
        RoomController.overrideMediaRoot(tempDir);

        RoomDTO dto = buildRoomDto("room", "Room", "owner");
        when(roomService.getRoom("room")).thenReturn(Optional.of(dto));

        Response response = controller.searchRoomVideos("room", tempDir.toString());

        assertThat(response.getStatus()).isEqualTo(Response.Status.OK.getStatusCode());
        Map<String, Object> entity = cast(response.getEntity());
        assertThat(entity.get("directory")).isEqualTo(tempDir.toString());

        @SuppressWarnings("unchecked")
        List<Map<String, Object>> entries = (List<Map<String, Object>>) entity.get("entries");
        assertThat(entries).hasSize(1);
        Map<String, Object> entry = entries.get(0);
        assertThat(entry.get("name")).isEqualTo("song.mp4");
        assertThat(entry.get("relativePath")).isEqualTo("song.mp4");
        assertThat(entry.get("sizeBytes")).isEqualTo(Files.size(video));

        Files.deleteIfExists(video);
        Files.deleteIfExists(tempDir);
    }

    @Test
    void searchRoomVideosReturnsServerErrorWhenListFails() throws Exception {
        Path tempDir = Files.createTempDirectory("karaoke");
        Path notAFile = Files.createFile(tempDir.resolve("notdir"));
        RoomController.overrideMediaRoot(tempDir);

        RoomDTO dto = buildRoomDto("room", "Room", "owner");
        when(roomService.getRoom("room")).thenReturn(Optional.of(dto));

        Response response = controller.searchRoomVideos("room", notAFile.toString());

        assertThat(response.getStatus()).isEqualTo(Response.Status.INTERNAL_SERVER_ERROR.getStatusCode());

        Files.deleteIfExists(notAFile);
        Files.deleteIfExists(tempDir);
    }

    @Test
    void getVideoDirectoryReturnsMappedRoot() throws Exception {
        Path tempDir = Files.createTempDirectory("karaoke");
        RoomController.overrideMediaRoot(tempDir);

        Response response = controller.getVideoDirectory();

        assertThat(response.getStatus()).isEqualTo(Response.Status.OK.getStatusCode());
        Map<String, Object> entity = cast(response.getEntity());
        assertThat(entity.get("directory")).isEqualTo(tempDir.toString());

        Files.deleteIfExists(tempDir);
    }

    @Test
    void requestSongValidatesPermissionAndResult() {
        when(authorizer.canRequestSong(adminUser)).thenReturn(false);
        Response forbidden = controller.requestSong("room", "song");
        assertThat(forbidden.getStatus()).isEqualTo(Response.Status.FORBIDDEN.getStatusCode());

        when(authorizer.canRequestSong(adminUser)).thenReturn(true);
        when(roomService.addSongRequest("room", "song", adminUser)).thenReturn(Optional.empty());
        Response failure = controller.requestSong("room", "song");
        assertThat(failure.getStatus()).isEqualTo(Response.Status.BAD_REQUEST.getStatusCode());

        RoomDTO dto = buildRoomDto("room", "Room", "owner");
        when(roomService.addSongRequest("room", "song", adminUser)).thenReturn(Optional.of(dto));
        Response success = controller.requestSong("room", "song");
        assertThat(success.getStatus()).isEqualTo(Response.Status.OK.getStatusCode());
        assertThat(success.getEntity()).isEqualTo(dto);
    }

    @Test
    void getCurrentUserThrowsWhenMissing() {
        when(requestUserContext.getCurrentUser()).thenReturn(null);
        assertThat(catchThrowable(() -> controller.listRooms()))
                .isInstanceOf(WebApplicationException.class);
    }

    @Test
    void inviteDelegatesToInviteGuest() {
        RoomDTO dto = buildRoomDto("room", "Room", "owner");
        when(roomService.getRoom("room")).thenReturn(Optional.of(dto));
        when(authorizer.canModifyRoom(adminUser, "owner")).thenReturn(true);
        when(roomService.inviteGuest("room", "guest")).thenReturn(Optional.of(dto));

        Response response = controller.invite("room", "guest");
        assertThat(response.getStatus()).isEqualTo(Response.Status.OK.getStatusCode());
    }

    @SuppressWarnings("unchecked")
    private Map<String, Object> cast(Object entity) {
        return (Map<String, Object>) entity;
    }

    private RoomDTO buildRoomDto(String id, String name, String owner) {
        return new RoomDTO(new Room(id, name, owner));
    }

    private void injectField(String name, Object value) {
        try {
            var field = RoomController.class.getDeclaredField(name);
            field.setAccessible(true);
            field.set(controller, value);
        } catch (ReflectiveOperationException e) {
            throw new RuntimeException(e);
        }
    }
}
