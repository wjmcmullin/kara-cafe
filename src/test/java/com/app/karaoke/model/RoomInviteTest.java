package com.app.karaoke.model;

import java.time.Instant;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import org.junit.jupiter.api.Test;

class RoomInviteTest {

    @Test
    void gettersReturnValuesAndTimestampIsRecent() {
        RoomInvite invite = new RoomInvite("room-123", "guest");

        assertThat(invite.getId()).isEqualTo("room-123");
        assertThat(invite.getInvitee()).isEqualTo("guest");
        assertThat(invite.getInvitedAt()).isNotNull();
        assertThat(invite.getInvitedAt()).isBeforeOrEqualTo(Instant.now());
    }

    @Test
    void constructorRejectsNullValues() {
        assertThatThrownBy(() -> new RoomInvite(null, "guest"))
                .isInstanceOf(NullPointerException.class);
        assertThatThrownBy(() -> new RoomInvite("room", null))
                .isInstanceOf(NullPointerException.class);
    }
}
