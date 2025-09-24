package com.app.puzzle;

import android.content.ClipData;
import android.os.Bundle;
import android.view.DragEvent;
import android.view.HapticFeedbackConstants;
import android.view.MotionEvent;
import android.view.View;
import android.widget.GridLayout;
import android.widget.TextView;

import androidx.activity.EdgeToEdge;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.graphics.Insets;
import androidx.core.view.ViewCompat;
import androidx.core.view.WindowInsetsCompat;

import com.google.android.material.appbar.MaterialToolbar;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;

public class MainActivity extends AppCompatActivity {

    private static final List<String> GOAL_STATE = Arrays.asList("A", "B", "C", "D", "E", "F", "G", "H", "");

    private final List<TextView> tiles = new ArrayList<>();

    private GridLayout puzzleGrid;
    private TextView moveCounterView;
    private TextView statusMessageView;
    private int moveCounter = 0;

    private final View.OnTouchListener touchListener = (v, event) -> {
        if (event.getAction() != MotionEvent.ACTION_DOWN) {
            return false;
        }
        if (!(v instanceof TextView)) {
            return false;
        }
        TextView tv = (TextView) v;
        if (tv.getText().toString().isEmpty()) {
            return false;
        }
        View.DragShadowBuilder shadow = new View.DragShadowBuilder(v);
        ClipData data = ClipData.newPlainText("tile", tv.getText());
        v.performHapticFeedback(HapticFeedbackConstants.VIRTUAL_KEY);
        v.startDragAndDrop(data, shadow, v, 0);
        return true;
    };

    private final View.OnDragListener dragListener = (v, event) -> {
        switch (event.getAction()) {
            case DragEvent.ACTION_DRAG_STARTED:
                return true;
            case DragEvent.ACTION_DRAG_ENTERED:
                if (v instanceof TextView) {
                    TextView enteredTile = (TextView) v;
                    if (enteredTile.getText().toString().isEmpty()) {
                        enteredTile.animate().scaleX(1.05f).scaleY(1.05f).setDuration(100).start();
                    }
                }
                return true;
            case DragEvent.ACTION_DRAG_EXITED:
                if (v instanceof View) {
                    v.animate().scaleX(1f).scaleY(1f).setDuration(100).start();
                }
                return true;
            case DragEvent.ACTION_DROP:
                View source = (View) event.getLocalState();
                if (source == v) {
                    return false;
                }
                if (!(source instanceof TextView) || !(v instanceof TextView)) {
                    return false;
                }
                TextView from = (TextView) source;
                TextView to = (TextView) v;
                if (!to.getText().toString().isEmpty()) {
                    return false;
                }
                int fromIndex = (int) from.getTag();
                int toIndex = (int) to.getTag();
                if (isAdjacent(fromIndex, toIndex)) {
                    to.setText(from.getText());
                    from.setText("");
                    to.performHapticFeedback(HapticFeedbackConstants.KEYBOARD_TAP);
                    v.animate().scaleX(1f).scaleY(1f).setDuration(100).start();
                    handleSuccessfulMove();
                    return true;
                }
                return false;
            case DragEvent.ACTION_DRAG_ENDED:
                if (v instanceof View) {
                    v.animate().scaleX(1f).scaleY(1f).setDuration(100).start();
                }
                return true;
            default:
                return true;
        }
    };

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        EdgeToEdge.enable(this);
        setContentView(R.layout.activity_main);

        View root = findViewById(R.id.main);
        ViewCompat.setOnApplyWindowInsetsListener(root, (v, insets) -> {
            Insets systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars());
            v.setPadding(systemBars.left, systemBars.top, systemBars.right, systemBars.bottom);
            return insets;
        });

        MaterialToolbar toolbar = findViewById(R.id.topAppBar);
        setSupportActionBar(toolbar);

        puzzleGrid = findViewById(R.id.puzzleGrid);
        moveCounterView = findViewById(R.id.moveCounter);
        statusMessageView = findViewById(R.id.statusMessage);

        prepareTiles();

        View shuffleButton = findViewById(R.id.shuffleButton);
        shuffleButton.setOnClickListener(v -> resetBoard());

        resetBoard();
    }

    private void prepareTiles() {
        tiles.clear();
        for (int i = 0; i < puzzleGrid.getChildCount(); i++) {
            View child = puzzleGrid.getChildAt(i);
            if (child instanceof TextView) {
                TextView tile = (TextView) child;
                tile.setTag(i);
                tile.setOnTouchListener(touchListener);
                tile.setOnDragListener(dragListener);
                tiles.add(tile);
            }
        }
    }

    private void resetBoard() {
        List<String> board = generateSolvableBoard();
        applyBoard(board);
        moveCounter = 0;
        updateMoveCounter();
        statusMessageView.setText(R.string.status_ready);
    }

    private void handleSuccessfulMove() {
        moveCounter++;
        updateMoveCounter();
        if (isSolved(readBoard())) {
            statusMessageView.setText(R.string.puzzle_solved);
        } else {
            statusMessageView.setText(R.string.status_keep_going);
        }
        updateTilesAppearance();
    }

    private void updateMoveCounter() {
        moveCounterView.setText(getString(R.string.move_counter, moveCounter));
    }

    private void applyBoard(List<String> board) {
        for (int i = 0; i < tiles.size() && i < board.size(); i++) {
            tiles.get(i).setText(board.get(i));
        }
        updateTilesAppearance();
    }

    private void updateTilesAppearance() {
        for (TextView tile : tiles) {
            boolean isEmpty = tile.getText().toString().isEmpty();
            tile.setAlpha(isEmpty ? 0.35f : 1f);
            tile.setContentDescription(isEmpty
                    ? getString(R.string.empty_tile_description)
                    : getString(R.string.tile_description, tile.getText()));
        }
    }

    private List<String> readBoard() {
        List<String> currentState = new ArrayList<>(tiles.size());
        for (TextView tile : tiles) {
            currentState.add(tile.getText().toString());
        }
        return currentState;
    }

    private List<String> generateSolvableBoard() {
        List<String> board = new ArrayList<>(GOAL_STATE);
        do {
            Collections.shuffle(board);
        } while (!isSolvable(board) || isSolved(board));
        return board;
    }

    private boolean isSolved(List<String> board) {
        return board.equals(GOAL_STATE);
    }

    private boolean isSolvable(List<String> board) {
        int inversions = 0;
        for (int i = 0; i < board.size(); i++) {
            String current = board.get(i);
            if (current.isEmpty()) {
                continue;
            }
            for (int j = i + 1; j < board.size(); j++) {
                String next = board.get(j);
                if (next.isEmpty()) {
                    continue;
                }
                if (current.compareTo(next) > 0) {
                    inversions++;
                }
            }
        }
        return inversions % 2 == 0;
    }

    private boolean isAdjacent(int a, int b) {
        int ar = a / 3;
        int ac = a % 3;
        int br = b / 3;
        int bc = b % 3;
        return Math.abs(ar - br) + Math.abs(ac - bc) == 1;
    }
}
