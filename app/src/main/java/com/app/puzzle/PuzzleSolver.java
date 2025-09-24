package com.app.puzzle;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.PriorityQueue;

/**
 * Resolves sliding puzzles using the A* search algorithm to obtain the
 * minimal number of moves between a given arrangement and the solved state.
 */
final class PuzzleSolver {

    private static final class Node implements Comparable<Node> {
        final String key;
        final int blankIndex;
        final int costFromStart;
        final int estimatedTotalCost;

        Node(String key, int blankIndex, int costFromStart, int estimatedTotalCost) {
            this.key = key;
            this.blankIndex = blankIndex;
            this.costFromStart = costFromStart;
            this.estimatedTotalCost = estimatedTotalCost;
        }

        @Override
        public int compareTo(Node other) {
            return Integer.compare(this.estimatedTotalCost, other.estimatedTotalCost);
        }
    }

    private static final class Step {
        final String parentKey;
        final char movedTile;

        Step(String parentKey, char movedTile) {
            this.parentKey = parentKey;
            this.movedTile = movedTile;
        }
    }

    private final int dimension;
    private final int size;
    private final int blankValue;
    private final String goalKey;
    private final int[][] neighbors;

    PuzzleSolver(int dimension) {
        this.dimension = dimension;
        this.size = dimension * dimension;
        this.blankValue = size - 1;
        this.goalKey = buildGoalKey();
        this.neighbors = buildNeighbors();
    }

    List<Integer> solve(int[] start) {
        String startKey = keyFromState(start);
        if (startKey.equals(goalKey)) {
            return new ArrayList<>();
        }

        int startBlank = locateBlank(start);

        Map<String, Step> backtrack = new HashMap<>();
        Map<String, Integer> gScores = new HashMap<>();
        PriorityQueue<Node> openSet = new PriorityQueue<>();

        int initialHeuristic = manhattanDistance(startKey.toCharArray());
        openSet.add(new Node(startKey, startBlank, 0, initialHeuristic));
        gScores.put(startKey, 0);

        while (!openSet.isEmpty()) {
            Node current = openSet.poll();
            int recordedCost = gScores.getOrDefault(current.key, Integer.MAX_VALUE);
            if (current.costFromStart > recordedCost) {
                continue;
            }
            if (current.key.equals(goalKey)) {
                return reconstruct(backtrack, current.key);
            }

            char[] chars = current.key.toCharArray();
            for (int neighbor : neighbors[current.blankIndex]) {
                char[] next = chars.clone();
                char movedTile = next[neighbor];
                next[current.blankIndex] = movedTile;
                next[neighbor] = charForValue(blankValue);
                String nextKey = new String(next);
                int tentativeG = current.costFromStart + 1;
                int bestKnown = gScores.getOrDefault(nextKey, Integer.MAX_VALUE);
                if (tentativeG >= bestKnown) {
                    continue;
                }
                gScores.put(nextKey, tentativeG);
                backtrack.put(nextKey, new Step(current.key, movedTile));
                int heuristic = manhattanDistance(next);
                openSet.add(new Node(nextKey, neighbor, tentativeG, tentativeG + heuristic));
            }
        }

        return null;
    }

    private List<Integer> reconstruct(Map<String, Step> backtrack, String goal) {
        List<Integer> moves = new ArrayList<>();
        String current = goal;
        while (backtrack.containsKey(current)) {
            Step step = backtrack.get(current);
            moves.add(0, valueFromChar(step.movedTile));
            current = step.parentKey;
        }
        return moves;
    }

    private int locateBlank(int[] state) {
        for (int i = 0; i < state.length; i++) {
            if (state[i] == blankValue) {
                return i;
            }
        }
        return -1;
    }

    private int[][] buildNeighbors() {
        int[][] result = new int[size][];
        for (int index = 0; index < size; index++) {
            int row = index / dimension;
            int col = index % dimension;
            int[] buffer = new int[4];
            int count = 0;
            if (row > 0) buffer[count++] = (row - 1) * dimension + col;
            if (row < dimension - 1) buffer[count++] = (row + 1) * dimension + col;
            if (col > 0) buffer[count++] = row * dimension + (col - 1);
            if (col < dimension - 1) buffer[count++] = row * dimension + (col + 1);
            int[] neighbors = new int[count];
            System.arraycopy(buffer, 0, neighbors, 0, count);
            result[index] = neighbors;
        }
        return result;
    }

    private String keyFromState(int[] state) {
        char[] chars = new char[state.length];
        for (int i = 0; i < state.length; i++) {
            chars[i] = charForValue(state[i]);
        }
        return new String(chars);
    }

    private int manhattanDistance(char[] state) {
        int distance = 0;
        for (int index = 0; index < state.length; index++) {
            int value = valueFromChar(state[index]);
            if (value == blankValue) {
                continue;
            }
            int currentRow = index / dimension;
            int currentCol = index % dimension;
            int goalRow = value / dimension;
            int goalCol = value % dimension;
            distance += Math.abs(currentRow - goalRow) + Math.abs(currentCol - goalCol);
        }
        return distance;
    }

    private String buildGoalKey() {
        char[] chars = new char[size];
        for (int i = 0; i < size; i++) {
            chars[i] = charForValue(i);
        }
        return new String(chars);
    }

    private char charForValue(int value) {
        return (char) ('a' + value);
    }

    private int valueFromChar(char c) {
        return c - 'a';
    }
}
