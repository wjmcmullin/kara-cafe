package com.app.karaoke.model;

import java.time.Instant;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import org.junit.jupiter.api.Test;

class RoomSongRequestTest {

    @Test
    void gettersReturnProvidedValuesAndTimestampIsRecent() {
        RoomSongRequest request = new RoomSongRequest("req-1", "alice", "Never Gonna Give You Up");

        assertThat(request.getId()).isEqualTo("req-1");
        assertThat(request.getRequester()).isEqualTo("alice");
        assertThat(request.getSong()).isEqualTo("Never Gonna Give You Up");
        assertThat(request.getRequestedAt()).isNotNull();
        assertThat(request.getRequestedAt()).isBeforeOrEqualTo(Instant.now());
    }

    @Test
    void constructorRejectsNullId() {
        assertThatThrownBy(() -> new RoomSongRequest(null, "alice", "song"))
                .isInstanceOf(NullPointerException.class);
    }

    @Test
    void constructorRejectsNullRequester() {
        assertThatThrownBy(() -> new RoomSongRequest("req-1", null, "song"))
                .isInstanceOf(NullPointerException.class);
    }

    @Test
    void constructorRejectsNullSong() {
        assertThatThrownBy(() -> new RoomSongRequest("req-1", "alice", null))
                .isInstanceOf(NullPointerException.class);
    }
}
