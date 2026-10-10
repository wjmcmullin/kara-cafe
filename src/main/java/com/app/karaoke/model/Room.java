package com.app.karaoke.model;

import java.time.Duration;
import java.time.Instant;
import java.util.Collections;
import java.util.LinkedList;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

public class Room {

    private final String id;
    private final String name;
    private final String owner;
    private final Instant createdAt;
    private final Set<String> occupants = ConcurrentHashMap.newKeySet();
    private final Set<String> invitedUsers = ConcurrentHashMap.newKeySet();
    private final List<RoomSongRequest> songRequests = Collections.synchronizedList(new LinkedList<>());
    private volatile RoomSongRequest currentSong;

    public Room(String id, String name, String owner) {
        this.id = Objects.requireNonNull(id);
        this.name = Objects.requireNonNull(name);
        this.owner = Objects.requireNonNull(owner);
        this.createdAt = Instant.now();
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

    public Instant getCreatedAt() {
        return createdAt;
    }

    public synchronized int getOccupantCount() {
        return occupants.size();
    }

    public boolean enter(String username) {
        if (username == null || username.isBlank()) {
            return false;
        }
        return occupants.add(username.trim());
    }

    public boolean leave(String username) {
        if (username == null || username.isBlank()) {
            return false;
        }
        return occupants.remove(username.trim());
    }

    public void invite(String username) {
        if (username != null && !username.isBlank()) {
            invitedUsers.add(username.trim());
        }
    }

    public List<String> getInvitedUsers() {
        return List.copyOf(invitedUsers);
    }

    public void addSongRequest(RoomSongRequest request) {
        songRequests.add(request);
    }

    public List<RoomSongRequest> getSongRequests() {
        synchronized (songRequests) {
            return List.copyOf(songRequests);
        }
    }

    public synchronized RoomSongRequest startNextSong() {
        if (songRequests.isEmpty()) {
            currentSong = null;
            return null;
        }
        currentSong = songRequests.remove(0);
        return currentSong;
    }

    public synchronized void finishCurrentSong() {
        currentSong = null;
    }

    public RoomSongRequest getCurrentSong() {
        return currentSong;
    }

    public String getUptime() {
        Duration uptime = Duration.between(createdAt, Instant.now());
        long hours = uptime.toHours();
        long minutes = uptime.toMinutesPart();
        long seconds = uptime.toSecondsPart();

        if (hours > 0) {
            return String.format("%dh %02dm %02ds", hours, minutes, seconds);
        }
        if (minutes > 0) {
            return String.format("%dm %02ds", minutes, seconds);
        }
        return String.format("%02ds", seconds);
    }
}
