package com.app.puzzle.data;

public class ScoreEntry {
    private final long id;
    private final String nickname;
    private final long durationMillis;
    private final long createdAt;

    public ScoreEntry(long id, String nickname, long durationMillis, long createdAt) {
        this.id = id;
        this.nickname = nickname;
        this.durationMillis = durationMillis;
        this.createdAt = createdAt;
    }

    public long getId() {
        return id;
    }

    public String getNickname() {
        return nickname;
    }

    public long getDurationMillis() {
        return durationMillis;
    }

    public long getCreatedAt() {
        return createdAt;
    }
}
