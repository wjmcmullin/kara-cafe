package com.app.karaoke.controller;

import com.app.karaoke.model.AppUser;
import com.app.karaoke.model.Role;
import com.app.karaoke.model.RoomDTO;
import com.app.karaoke.security.Authorizer;
import com.app.karaoke.security.RequestUserContext;
import com.app.karaoke.service.RoomService;
import com.app.karaoke.service.TokenService;
import com.app.karaoke.service.UserService;
import jakarta.ws.rs.core.Response;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.Spy;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class RoomControllerTest {

    @InjectMocks
    private RoomController controller;

    @Mock
    private RequestUserContext userContext;

    @Spy
    private RoomService roomService = new RoomService();

    @Mock
    private Authorizer authorizer;

    @Mock
    private UserService userService;

    @Mock
    private TokenService tokenService;

    @Test
    void createRoomSucceedsForAdmin() {
        AppUser admin = new AppUser("admin", Role.ADMIN, "pw");
        when(userContext.getCurrentUser()).thenReturn(admin);
        when(authorizer.canCreateRoom(admin)).thenReturn(true);

        Response response = controller.createRoom("My Room");

        assertThat(response.getStatus()).isEqualTo(Response.Status.OK.getStatusCode());
        RoomDTO created = (RoomDTO) response.getEntity();
        assertThat(created).isNotNull();
        assertThat(created.getOwner()).isEqualTo("admin");
    }

    @Test
    void enterRoomNotFound() {
        AppUser user = new AppUser("bob", Role.USER, "pw");
        when(userContext.getCurrentUser()).thenReturn(user);

        Response response = controller.enterRoom("missing");

        assertThat(response.getStatus()).isEqualTo(Response.Status.NOT_FOUND.getStatusCode());
    }

    @Test
    void inviteRequiresPermission() {
        AppUser admin = new AppUser("admin", Role.ADMIN, "pw");
        when(userContext.getCurrentUser()).thenReturn(admin);

        RoomDTO room = roomService.createRoom("Invited Room", "owner");
        when(authorizer.canModifyRoom(admin, room.getOwner())).thenReturn(false);

        Response response = controller.invite(room.getId(), "guest");

        assertThat(response.getStatus()).isEqualTo(Response.Status.FORBIDDEN.getStatusCode());
    }

    @Test
    void listRoomsReturnsPayload() {
        AppUser user = new AppUser("user", Role.USER, "pw");
        when(userContext.getCurrentUser()).thenReturn(user);
        roomService.createRoom("Owned Room", "user");

        Response response = controller.listRooms();

        assertThat(response.getStatus()).isEqualTo(Response.Status.OK.getStatusCode());

        @SuppressWarnings("unchecked")
        Map<String, Object> payload = (Map<String, Object>) response.getEntity();

        Object roomsObj = payload.get("rooms");
        assertThat(roomsObj).isInstanceOf(List.class);

        @SuppressWarnings("unchecked")
        List<?> rooms = (List<?>) roomsObj;
        assertThat(rooms).isNotEmpty();
    }

    @Test
    void deleteRoomRemoves() {
        AppUser admin = new AppUser("admin", Role.ADMIN, "pw");
        when(userContext.getCurrentUser()).thenReturn(admin);

        RoomDTO room = roomService.createRoom("Disposable Room", "admin");
        when(authorizer.canModifyRoom(admin, room.getOwner())).thenReturn(true);

        Response response = controller.deleteRoom(room.getId());

        assertThat(response.getStatus()).isEqualTo(Response.Status.NO_CONTENT.getStatusCode());
        assertThat(roomService.getRoom(room.getId())).isEmpty();
    }
}
