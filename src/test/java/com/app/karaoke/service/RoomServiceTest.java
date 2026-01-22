package com.app.karaoke.service;

import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import com.app.karaoke.model.AppUser;
import com.app.karaoke.model.Role;
import com.app.karaoke.model.RoomDTO;

class RoomServiceTest {

    private RoomService roomService;

    @BeforeEach
    void setUp() {
        roomService = new RoomService();
    }

    @Test
    void createRoomStoresData() {
        RoomDTO dto = roomService.createRoom("Test Room", "owner1");
        assertThat(dto).isNotNull();
        assertThat(dto.getName()).isEqualTo("Test Room");
        assertThat(dto.getOwner()).isEqualTo("owner1");
        assertThat(dto.getOccupants()).isZero();
    }

    @Test
    void listRoomsFiltersByOwner() {
        roomService.createRoom("Room A", "owner1");
        roomService.createRoom("Room B", "owner2");

        List<RoomDTO> owner1Rooms = roomService.listRoomsFor(Role.ADMIN, "owner1");
        assertThat(owner1Rooms).hasSize(1);
        assertThat(owner1Rooms.get(0).getName()).isEqualTo("Room A");

        List<RoomDTO> superAdmin = roomService.listRoomsFor(Role.SUPER_ADMIN, "owner1");
        assertThat(superAdmin).hasSize(2);
    }

    @Test
    void getRoomAndEnterWork() {
        RoomDTO dto = roomService.createRoom("Room A", "owner");
        Optional<RoomDTO> fetched = roomService.getRoom(dto.getId());
        assertThat(fetched).isPresent();

        Optional<RoomDTO> entered = roomService.enterRoom(dto.getId());
        assertThat(entered).isPresent();
        assertThat(entered.get().getOccupants()).isEqualTo(1);
    }

    @Test
    void addSongRequestRejectsBadInput() {
        assertThat(roomService.addSongRequest("missing", "", new AppUser("foo", Role.USER, "pw"))).isEmpty();
    }

    @Test
    void addSongRequestAddsEntry() {
        RoomDTO dto = roomService.createRoom("Room", "owner");
        AppUser user = new AppUser("foo", Role.USER, "pw");
        Optional<RoomDTO> updated = roomService.addSongRequest(dto.getId(), "Never Gonna", user);

        assertThat(updated).isPresent();
        assertThat(updated.get().getSongRequests()).hasSize(1);
        assertThat(updated.get().getSongRequests().get(0).getRequester()).isEqualTo("foo");
    }

    @Test
    void inviteGuestKeepsTrack() {
        RoomDTO dto = roomService.createRoom("Room", "owner");
        Optional<RoomDTO> invite = roomService.inviteGuest(dto.getId(), "guestId");
        assertThat(invite).isPresent();
    }

    @Test
    void deleteRoomRemovesData() {
        RoomDTO dto = roomService.createRoom("Room", "owner");
        Optional<RoomDTO> deleted = roomService.deleteRoom(dto.getId());
        assertThat(deleted).isPresent();

        assertThat(roomService.getRoom(dto.getId())).isEmpty();
    }
}
