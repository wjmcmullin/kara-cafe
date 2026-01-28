package com.app.karaoke.model;

public class RoomVideoEntry {

    private final String name;
    private final String relativePath;
    private final long sizeBytes;

    public RoomVideoEntry(String name, String relativePath, long sizeBytes) {
        this.name = name;
        this.relativePath = relativePath;
        this.sizeBytes = sizeBytes;
    }

    public String getName() {
        return name;
    }

    public String getRelativePath() {
        return relativePath;
    }

    public long getSizeBytes() {
        return sizeBytes;
    }
}
