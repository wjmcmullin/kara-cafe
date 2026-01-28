package com.app.karaoke.model;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

class RoomVideoEntryTest {

    @Test
    void gettersReturnProvidedValues() {
        RoomVideoEntry entry = new RoomVideoEntry("song.mp4", "karaoke/song.mp4", 1024L);

        assertThat(entry.getName()).isEqualTo("song.mp4");
        assertThat(entry.getRelativePath()).isEqualTo("karaoke/song.mp4");
        assertThat(entry.getSizeBytes()).isEqualTo(1024L);
    }

    @Test
    void differentInstancesHoldIndependentValues() {
        RoomVideoEntry first = new RoomVideoEntry("a.mp4", "dir/a.mp4", 100L);
        RoomVideoEntry second = new RoomVideoEntry("b.mp4", "dir/b.mp4", 200L);

        assertThat(first.getName()).isEqualTo("a.mp4");
        assertThat(second.getName()).isEqualTo("b.mp4");

        assertThat(first.getRelativePath()).isEqualTo("dir/a.mp4");
        assertThat(second.getRelativePath()).isEqualTo("dir/b.mp4");

        assertThat(first.getSizeBytes()).isEqualTo(100L);
        assertThat(second.getSizeBytes()).isEqualTo(200L);
    }
}
