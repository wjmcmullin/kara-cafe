package com.app.karaoke.model;

import java.time.Instant;
import java.util.Objects;

public class RoomInvite {

    private final String id;
    private final String invitee;
    private final Instant invitedAt;

    public RoomInvite(String id, String invitee) {
        this.id = Objects.requireNonNull(id);
        this.invitee = Objects.requireNonNull(invitee);
        this.invitedAt = Instant.now();
    }

    public String getId() {
        return id;
    }

    public String getInvitee() {
        return invitee;
    }

    public Instant getInvitedAt() {
        return invitedAt;
    }
}
