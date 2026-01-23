package com.app.karaoke.controller;

import java.lang.reflect.Field;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.catchThrowable;
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

    @BeforeEach
    void setUp() throws NoSuchFieldException, IllegalAccessException {
        MockitoAnnotations.openMocks(this);
        controller = new RoomController();

        injectField("roomService", roomService);
        injectField("requestUserContext", requestUserContext);
        injectField("authorizer", authorizer);
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
        when(requestUserContext.getCurrentUser()).thenReturn(adminUser);
        when(authorizer.canCreateRoom(adminUser)).thenReturn(false);

        Response response = controller.createRoom("New");

        assertThat(response.getStatus()).isEqualTo(Response.Status.FORBIDDEN.getStatusCode());
        assertThat(response.getEntity()).isEqualTo(Map.of("error", "Only admins can create rooms"));
        verify(roomService, never()).createRoom(anyString(), anyString());
    }

    @Test
    void createRoomValidatesNameAndSingleRoomLimit() {
        when(requestUserContext.getCurrentUser()).thenReturn(adminUser);
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
        when(requestUserContext.getCurrentUser()).thenReturn(superAdminUser);
        when(roomService.getRoom("missing")).thenReturn(Optional.empty());

        Response response = controller.deleteRoom("missing");
        assertThat(response.getStatus()).isEqualTo(Response.Status.NOT_FOUND.getStatusCode());
    }

    @Test
    void deleteRoomChecksPermissions() {
        RoomDTO dto = buildRoomDto("room", "Room", "owner");
        when(requestUserContext.getCurrentUser()).thenReturn(superAdminUser);
        when(roomService.getRoom("room")).thenReturn(Optional.of(dto));
        when(authorizer.canModifyRoom(superAdminUser, "owner")).thenReturn(false);

        Response response = controller.deleteRoom("room");
        assertThat(response.getStatus()).isEqualTo(Response.Status.FORBIDDEN.getStatusCode());
    }

    @Test
    void deleteRoomDeletesWhenAllowed() {
        RoomDTO dto = buildRoomDto("room", "Room", "owner");
        when(requestUserContext.getCurrentUser()).thenReturn(superAdminUser);
        when(roomService.getRoom("room")).thenReturn(Optional.of(dto));
        when(authorizer.canModifyRoom(superAdminUser, "owner")).thenReturn(true);

        Response response = controller.deleteRoom("room");
        assertThat(response.getStatus()).isEqualTo(Response.Status.NO_CONTENT.getStatusCode());
        verify(roomService).deleteRoom("room");
    }

    @Test
    void inviteGuestHandlesAllValidation() {
        when(requestUserContext.getCurrentUser()).thenReturn(adminUser);
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
        when(requestUserContext.getCurrentUser()).thenReturn(adminUser);
        RoomDTO dto = buildRoomDto("room", "Room", "owner");
        when(roomService.getRoom("room")).thenReturn(Optional.of(dto));
        when(authorizer.canModifyRoom(adminUser, "owner")).thenReturn(true);

        when(roomService.inviteGuest("room", "guest")).thenReturn(Optional.empty());
        Response failure = controller.inviteGuest("room", "guest");
        assertThat(failure.getStatus()).isEqualTo(Response.Status.BAD_REQUEST.getStatusCode());

        when(roomService.inviteGuest("room", "guest")).thenReturn(Optional.of(dto));
        Response success = controller.inviteGuest("room", "guest");
        assertThat(success.getStatus()).isEqualTo(Response.Status.OK.getStatusCode());
        assertThat(success.getEntity()).isEqualTo(Map.of("entryUrl", "/kara-cafe/karaoke-room/room.html?id=room"));
    }

    @Test
    void enterRoomHandlesMissingAndSuccess() {
        when(requestUserContext.getCurrentUser()).thenReturn(adminUser);
        when(roomService.enterRoom("missing")).thenReturn(Optional.empty());
        Response missing = controller.enterRoom("missing");
        assertThat(missing.getStatus()).isEqualTo(Response.Status.NOT_FOUND.getStatusCode());

        RoomDTO dto = buildRoomDto("room", "Room", "owner");
        when(roomService.enterRoom("room")).thenReturn(Optional.of(dto));
        Response response = controller.enterRoom("room");
        assertThat(response.getStatus()).isEqualTo(Response.Status.OK.getStatusCode());
        assertThat(response.getEntity()).isEqualTo(dto);
    }

    @Test
    void requestSongValidatesPermissionAndResult() {
        when(requestUserContext.getCurrentUser()).thenReturn(adminUser);
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
        when(requestUserContext.getCurrentUser()).thenReturn(adminUser);
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

    private void injectField(String name, Object value) throws NoSuchFieldException, IllegalAccessException {
        Field field = RoomController.class.getDeclaredField(name);
        field.setAccessible(true);
        field.set(controller, value);
    }
}
