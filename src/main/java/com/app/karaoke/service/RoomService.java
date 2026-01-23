package com.app.karaoke.service;

import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import java.util.stream.Collectors;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import com.app.karaoke.model.AppUser;
import com.app.karaoke.model.Role;
import com.app.karaoke.model.Room;
import com.app.karaoke.model.RoomDTO;
import com.app.karaoke.model.RoomSongRequest;

import jakarta.enterprise.context.ApplicationScoped;

@ApplicationScoped
public class RoomService {

    private static final Logger LOG = LogManager.getLogger(RoomService.class);

    private final ConcurrentMap<String, Room> rooms = new ConcurrentHashMap<>();

    public RoomDTO createRoom(String name, String owner) {
        LOG.debug("createRoom called name={} owner={}", name, owner);
        String id = UUID.randomUUID().toString();
        Room room = new Room(id, name, owner);
        rooms.put(id, room);
        LOG.info("Room {} created by {}", id, owner);
        return new RoomDTO(room);
    }

    public List<RoomDTO> listRoomsFor(Role role, String username) {
        LOG.debug("listRoomsFor called role={} username={}", role, username);
        Collection<Room> source = rooms.values();
        return source.stream()
                .filter(room -> role == Role.SUPER_ADMIN || room.getOwner().equals(username))
                .map(RoomDTO::new)
                .collect(Collectors.toList());
    }

    public Optional<RoomDTO> getRoom(String roomId) {
        LOG.debug("getRoom {}", roomId);
        return Optional.ofNullable(rooms.get(roomId)).map(RoomDTO::new);
    }

    public Optional<RoomDTO> enterRoom(String roomId) {
        LOG.debug("enterRoom {}", roomId);
        return Optional.ofNullable(rooms.get(roomId))
                .map(room -> {
                    room.incrementOccupants();
                    return new RoomDTO(room);
                });
    }

    public Optional<RoomDTO> addSongRequest(String roomId, String song, AppUser requester) {
        LOG.debug("addSongRequest called for room={} song={} user={}", roomId, song, requester);
        if (song == null || song.isBlank() || requester == null) {
            LOG.warn("Invalid song request for room {}", roomId);
            return Optional.empty();
        }

        Room room = rooms.get(roomId);
        if (room == null) {
            LOG.warn("Song request for unknown room {}", roomId);
            return Optional.empty();
        }

        RoomSongRequest request = new RoomSongRequest(UUID.randomUUID().toString(), requester.getUsername(), song.trim());
        room.addSongRequest(request);
        LOG.info("Song {} requested by {} in room {}", song, requester.getUsername(), roomId);
        return Optional.of(new RoomDTO(room));
    }

    public Optional<RoomDTO> inviteGuest(String roomId, String guest) {
        LOG.debug("inviteGuest room={} guest={}", roomId, guest);
        if (guest == null || guest.isBlank()) {
            LOG.warn("Empty guest invite for room {}", roomId);
            return Optional.empty();
        }

        Room room = rooms.get(roomId);
        if (room == null) {
            LOG.warn("Invite for missing room {}", roomId);
            return Optional.empty();
        }

        room.invite(guest);
        LOG.info("Guest {} invited to {}", guest, roomId);
        return Optional.of(new RoomDTO(room));
    }

    public Optional<RoomDTO> deleteRoom(String roomId) {
        LOG.debug("deleteRoom {}", roomId);
        Room removed = rooms.remove(roomId);
        if (removed == null) {
            LOG.warn("Attempted delete of missing room {}", roomId);
            return Optional.empty();
        }
        LOG.info("Room {} deleted", roomId);
        return Optional.of(new RoomDTO(removed));
    }

    public boolean hasRoomOwnedBy(String owner) {
        LOG.debug("hasRoomOwnedBy {}", owner);
        return rooms.values().stream().anyMatch(room -> room.getOwner().equals(owner));
    }
}
