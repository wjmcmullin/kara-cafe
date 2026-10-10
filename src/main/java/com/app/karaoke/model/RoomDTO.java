package com.app.karaoke.model;

import java.util.List;
import java.util.Objects;

public class RoomDTO {

    private final String id;
    private final String name;
    private final String owner;
    private final String uptime;
    private final int occupants;
    private final List<String> invitedUsers;
    private final List<RoomSongRequest> songRequests;
    private final RoomSongRequest currentSong;

    public RoomDTO(Room room) {
        this.id = Objects.requireNonNull(room.getId());
        this.name = Objects.requireNonNull(room.getName());
        this.owner = Objects.requireNonNull(room.getOwner());
        this.uptime = Objects.requireNonNull(room.getUptime());
        this.occupants = room.getOccupantCount();
        this.invitedUsers = room.getInvitedUsers();
        this.songRequests = room.getSongRequests();
        this.currentSong = room.getCurrentSong();
    }

    private RoomDTO buildRoomDto(String id, String name, String owner) {
        Room room = new Room(id, name, owner);
        return new RoomDTO(room);
    }

    public String getId() {
        return id;
    }

    public String getName() {
        return name;
    }

    public String getOwner() {
        return owner;
    }

    public String getUptime() {
        return uptime;
    }

    public int getOccupants() {
        return occupants;
    }

    public List<String> getInvitedUsers() {
        return invitedUsers;
    }

    public List<RoomSongRequest> getSongRequests() {
        return songRequests;
    }

    public RoomSongRequest getCurrentSong() {
        return currentSong;
    }
}
