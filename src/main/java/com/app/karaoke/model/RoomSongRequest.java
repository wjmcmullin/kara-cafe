package com.app.karaoke.model;

import java.time.Instant;
import java.util.Objects;

public class RoomSongRequest {

    private final String id;
    private final String requester;
    private final String song;
    private final Instant requestedAt;

    public RoomSongRequest(String id, String requester, String song) {
        this.id = Objects.requireNonNull(id);
        this.requester = Objects.requireNonNull(requester);
        this.song = Objects.requireNonNull(song);
        this.requestedAt = Instant.now();
    }

    public String getId() {
        return id;
    }

    public String getRequester() {
        return requester;
    }

    public String getSong() {
        return song;
    }

    public Instant getRequestedAt() {
        return requestedAt;
    }
}
