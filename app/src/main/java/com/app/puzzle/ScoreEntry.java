package com.app.puzzle;

public class ScoreEntry {
    private final String playerName;
    private final int moves;
    private final long timestamp;

    public ScoreEntry(String playerName, int moves, long timestamp) {
        this.playerName = playerName;
        this.moves = moves;
        this.timestamp = timestamp;
    }

    public String getPlayerName() {
        return playerName;
    }

    public int getMoves() {
        return moves;
    }

    public long getTimestamp() {
        return timestamp;
    }
}
