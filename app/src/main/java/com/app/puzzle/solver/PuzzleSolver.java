package com.app.puzzle.solver;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.PriorityQueue;
import java.util.Queue;

/**
 * Provides an A* search implementation to solve sliding puzzles.
 */
public final class PuzzleSolver {

    private PuzzleSolver() {
        // Utility class
    }

    /**
     * Calculates the optimal sequence of moves to solve a sliding puzzle using A*.
     *
     * @param startState the initial board as tile indexes (including the blank tile index)
     * @param gridSize   the length of one side of the square grid
     * @return a list of tile positions that should be swapped with the blank tile in order
     */
    public static List<Integer> solve(List<Integer> startState, int gridSize) {
        if (startState == null || startState.size() != gridSize * gridSize) {
            return Collections.emptyList();
        }
        int tileCount = gridSize * gridSize;
        int[] start = new int[tileCount];
        int blankIndex = -1;
        for (int i = 0; i < tileCount; i++) {
            int value = startState.get(i);
            start[i] = value;
            if (value == tileCount - 1) {
                blankIndex = i;
            }
        }
        if (blankIndex == -1) {
            return Collections.emptyList();
        }
        if (isSolved(start)) {
            return Collections.emptyList();
        }

        Node startNode = new Node(start, blankIndex, 0, heuristic(start, gridSize), -1, null);
        Queue<Node> openSet = new PriorityQueue<>((a, b) -> Integer.compare(a.fScore, b.fScore));
        openSet.add(startNode);

        Map<String, Integer> bestCost = new HashMap<>();
        bestCost.put(keyOf(start), 0);

        while (!openSet.isEmpty()) {
            Node current = openSet.poll();
            if (isSolved(current.state)) {
                return reconstructMoves(current);
            }

            String currentKey = keyOf(current.state);
            int recordedCost = bestCost.getOrDefault(currentKey, Integer.MAX_VALUE);
            if (current.gScore > recordedCost) {
                continue;
            }

            int row = current.blankIndex / gridSize;
            int col = current.blankIndex % gridSize;
            int[][] directions = {
                    {row - 1, col},
                    {row + 1, col},
                    {row, col - 1},
                    {row, col + 1}
            };

            for (int[] pos : directions) {
                int newRow = pos[0];
                int newCol = pos[1];
                if (newRow < 0 || newRow >= gridSize || newCol < 0 || newCol >= gridSize) {
                    continue;
                }
                int swapIndex = newRow * gridSize + newCol;
                int[] nextState = Arrays.copyOf(current.state, current.state.length);
                nextState[current.blankIndex] = nextState[swapIndex];
                nextState[swapIndex] = tileCount - 1;
                int tentativeG = current.gScore + 1;
                String key = keyOf(nextState);
                if (tentativeG >= bestCost.getOrDefault(key, Integer.MAX_VALUE)) {
                    continue;
                }
                bestCost.put(key, tentativeG);
                int nextBlankIndex = swapIndex;
                int hScore = heuristic(nextState, gridSize);
                Node nextNode = new Node(nextState, nextBlankIndex, tentativeG, tentativeG + hScore, swapIndex, current);
                openSet.add(nextNode);
            }
        }
        return Collections.emptyList();
    }

    private static List<Integer> reconstructMoves(Node goal) {
        List<Integer> moves = new ArrayList<>();
        Node current = goal;
        while (current != null && current.parent != null) {
            moves.add(current.movedTileIndex);
            current = current.parent;
        }
        Collections.reverse(moves);
        return moves;
    }

    private static boolean isSolved(int[] state) {
        for (int i = 0; i < state.length; i++) {
            if (state[i] != i) {
                return false;
            }
        }
        return true;
    }

    private static int heuristic(int[] state, int gridSize) {
        int distance = 0;
        for (int index = 0; index < state.length; index++) {
            int value = state[index];
            if (value == state.length - 1) {
                continue;
            }
            int currentRow = index / gridSize;
            int currentCol = index % gridSize;
            int targetRow = value / gridSize;
            int targetCol = value % gridSize;
            distance += Math.abs(currentRow - targetRow) + Math.abs(currentCol - targetCol);
        }
        return distance;
    }

    private static String keyOf(int[] state) {
        char[] key = new char[state.length];
        for (int i = 0; i < state.length; i++) {
            key[i] = (char) ('0' + state[i]);
        }
        return new String(key);
    }

    private static final class Node {
        final int[] state;
        final int blankIndex;
        final int gScore;
        final int fScore;
        final int movedTileIndex;
        final Node parent;

        Node(int[] state, int blankIndex, int gScore, int fScore, int movedTileIndex, Node parent) {
            this.state = state;
            this.blankIndex = blankIndex;
            this.gScore = gScore;
            this.fScore = fScore;
            this.movedTileIndex = movedTileIndex;
            this.parent = parent;
        }
    }
}
