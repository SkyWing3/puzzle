package com.app.puzzle.data;

public class ScoreEntry {
    private final long id;
    private final String nickname;
    private final long durationMillis;
    private final long moveCount;
    private final long createdAt;
    private final int level;

    public ScoreEntry(long id, String nickname, long durationMillis, long moveCount, long createdAt, int level) {
        this.id = id;
        this.nickname = nickname;
        this.durationMillis = durationMillis;
        this.moveCount = moveCount;
        this.createdAt = createdAt;
        this.level = level;
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

    public long getMoveCount() {
        return moveCount;
    }

    public long getCreatedAt() {
        return createdAt;
    }

    public int getLevel() {
        return level;
    }
}
