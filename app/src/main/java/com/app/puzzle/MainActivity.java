package com.app.puzzle;

import android.app.AlertDialog;
import android.app.Dialog;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.drawable.BitmapDrawable;
import android.graphics.drawable.ColorDrawable;
import android.graphics.drawable.Drawable;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.view.DragEvent;
import android.view.Gravity;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewGroup;
import android.view.Window;
import android.view.animation.AccelerateDecelerateInterpolator;
import android.widget.Button;
import android.widget.EditText;
import android.widget.GridLayout;
import android.widget.ImageView;
import android.widget.ListView;
import android.widget.TextView;
import android.widget.Toast;

import androidx.activity.EdgeToEdge;
import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.graphics.Insets;
import androidx.core.view.ViewCompat;
import androidx.core.view.WindowInsetsCompat;

import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import androidx.transition.AutoTransition;
import androidx.transition.TransitionManager;

public class MainActivity extends AppCompatActivity {

    private static final int GRID_DIMENSION = 3;
    private static final int BLANK_INDEX = GRID_DIMENSION * GRID_DIMENSION - 1;

    private ScoreDatabaseHelper databaseHelper;
    private int moveCount = 0;
    private boolean puzzleSolved = false;
    private boolean autoSolving = false;
    private PuzzleSolver puzzleSolver;

    private final Handler autoSolveHandler = new Handler(Looper.getMainLooper());

    private final View.OnTouchListener touchListener = (v, event) -> {
        if (event.getAction() != MotionEvent.ACTION_DOWN) return false;
        if (!(v instanceof TextView)) return false;
        TextView tv = (TextView) v;
        if (!isTileEmpty(tv)) {
            v.animate().scaleX(0.9f).scaleY(0.9f).setDuration(100)
                    .withEndAction(() -> v.animate().scaleX(1f).scaleY(1f).setDuration(100));
            View.DragShadowBuilder shadow = new View.DragShadowBuilder(v);
            v.startDragAndDrop(null, shadow, v, 0);
            return true;
        }
        return false;
    };

    private final View.OnDragListener dragListener = (v, event) -> {
        switch (event.getAction()) {
            case DragEvent.ACTION_DRAG_STARTED:
                return true;
            case DragEvent.ACTION_DROP:
                View source = (View) event.getLocalState();
                if (source == v) return false;
                if (!(source instanceof TextView) || !(v instanceof TextView)) return false;
                TextView from = (TextView) source;
                TextView to = (TextView) v;
                if (!isTileEmpty(to)) return false;
                int fromIndex = (int) from.getTag();
                int toIndex = (int) to.getTag();
                if (isAdjacent(fromIndex, toIndex)) {
                    transferTileContent(from, to);
                    to.animate().scaleX(1.05f).scaleY(1.05f).setDuration(150)
                            .withEndAction(() -> to.animate().scaleX(1f).scaleY(1f).setDuration(150));
                    registerMove();
                }
                return true;
            default:
                return true;
        }
    };

    private boolean isTileEmpty(TextView tile) {
        return tile.getText().toString().isEmpty() && tile.getForeground() == null;
    }

    private boolean isAdjacent(int fromIndex, int toIndex) {
        int fromRow = fromIndex / GRID_DIMENSION;
        int fromCol = fromIndex % GRID_DIMENSION;
        int toRow = toIndex / GRID_DIMENSION;
        int toCol = toIndex % GRID_DIMENSION;
        return Math.abs(fromRow - toRow) + Math.abs(fromCol - toCol) == 1;
    }

    private final ActivityResultLauncher<String> imagePicker =
            registerForActivityResult(new ActivityResultContracts.GetContent(), uri -> {
                if (uri != null) {
                    try (InputStream input = getContentResolver().openInputStream(uri)) {
                        Bitmap bitmap = BitmapFactory.decodeStream(input);
                        setImagePuzzle(bitmap);
                    } catch (IOException e) {
                        e.printStackTrace();
                    }
                }
            });

    private void setImagePuzzle(Bitmap bitmap) {
        GridLayout grid = findViewById(R.id.puzzleGrid);
        ImageView reference = findViewById(R.id.referenceImage);
        Button remove = findViewById(R.id.btnRemoveImage);
        Button add = findViewById(R.id.btnImage);
        resetMoveTracking();
        reference.setImageBitmap(bitmap);

        int pieceWidth = bitmap.getWidth() / GRID_DIMENSION;
        int pieceHeight = bitmap.getHeight() / GRID_DIMENSION;
        List<PuzzlePiece> pieces = new ArrayList<>();
        for (int r = 0; r < GRID_DIMENSION; r++) {
            for (int c = 0; c < GRID_DIMENSION; c++) {
                int index = r * GRID_DIMENSION + c;
                if (index == BLANK_INDEX) {
                    pieces.add(new PuzzlePiece(null, BLANK_INDEX));
                } else {
                    Bitmap piece = Bitmap.createBitmap(bitmap, c * pieceWidth, r * pieceHeight, pieceWidth, pieceHeight);
                    pieces.add(new PuzzlePiece(new BitmapDrawable(getResources(), piece), index));
                }
            }
        }
        do {
            Collections.shuffle(pieces);
        } while (!isSolvablePieces(pieces));
        for (int i = 0; i < grid.getChildCount(); i++) {
            TextView tile = (TextView) grid.getChildAt(i);
            PuzzlePiece piece = pieces.get(i);
            tile.setForeground(piece.drawable);
            tile.setText("");
            tile.setTag(R.id.tag_tile_content, piece.index);
        }

        ViewGroup root = findViewById(R.id.main);
        AutoTransition rootTrans = new AutoTransition();
        rootTrans.setDuration(300);
        rootTrans.setInterpolator(new AccelerateDecelerateInterpolator());
        TransitionManager.beginDelayedTransition(root, rootTrans);
        reference.setVisibility(View.VISIBLE);

        remove.setVisibility(View.VISIBLE);
        remove.setAlpha(0f);
        remove.post(() -> {
            remove.setTranslationX(-remove.getWidth());
            remove.animate().alpha(1f).translationX(0).setDuration(300)
                    .setInterpolator(new AccelerateDecelerateInterpolator());
            add.animate().translationX(remove.getWidth() + dpToPx(8)).setDuration(300)
                    .setInterpolator(new AccelerateDecelerateInterpolator());
        });
    }

    private void setLetterPuzzle() {
        GridLayout grid = findViewById(R.id.puzzleGrid);
        resetMoveTracking();
        List<Integer> order = new ArrayList<>();
        for (int i = 0; i < GRID_DIMENSION * GRID_DIMENSION; i++) {
            order.add(i);
        }
        do {
            Collections.shuffle(order);
        } while (!isSolvable(order));
        for (int i = 0; i < grid.getChildCount(); i++) {
            TextView tile = (TextView) grid.getChildAt(i);
            int contentIndex = order.get(i);
            if (contentIndex == BLANK_INDEX) {
                tile.setText("");
            } else {
                tile.setText(String.valueOf((char) ('A' + contentIndex)));
            }
            tile.setForeground(null);
            tile.setTag(R.id.tag_tile_content, contentIndex);
        }
    }

    private void clearImagePuzzle() {
        ImageView reference = findViewById(R.id.referenceImage);
        Button remove = findViewById(R.id.btnRemoveImage);
        Button add = findViewById(R.id.btnImage);
        ViewGroup root = findViewById(R.id.main);

        AutoTransition rootTrans = new AutoTransition();
        rootTrans.setDuration(300);
        rootTrans.setInterpolator(new AccelerateDecelerateInterpolator());
        TransitionManager.beginDelayedTransition(root, rootTrans);
        reference.setVisibility(View.GONE);

        add.animate().translationX(0).setDuration(300)
                .setInterpolator(new AccelerateDecelerateInterpolator());
        remove.animate().alpha(0f).translationX(-remove.getWidth()).setDuration(300)
                .setInterpolator(new AccelerateDecelerateInterpolator())
                .withEndAction(() -> remove.setVisibility(View.GONE));

        setLetterPuzzle();
    }

    private void autoSolvePuzzle() {
        if (autoSolving) {
            return;
        }
        int[] currentState = getCurrentState();
        if (isSolvedState(currentState)) {
            Toast.makeText(this, R.string.already_solved, Toast.LENGTH_SHORT).show();
            return;
        }
        if (!isSolvable(currentState)) {
            Toast.makeText(this, R.string.unsolvable_puzzle, Toast.LENGTH_SHORT).show();
            return;
        }

        autoSolving = true;
        autoSolveHandler.removeCallbacksAndMessages(null);
        setTileInteractivity(false);
        setControlsEnabled(false);

        new Thread(() -> {
            List<Integer> solution = puzzleSolver.solve(currentState);
            runOnUiThread(() -> {
                if (solution == null || solution.isEmpty()) {
                    autoSolving = false;
                    setTileInteractivity(true);
                    setControlsEnabled(true);
                    Toast.makeText(this, R.string.solution_not_found, Toast.LENGTH_SHORT).show();
                    return;
                }
                playSolutionAnimations(solution);
            });
        }).start();
    }

    private void playSolutionAnimations(List<Integer> moves) {
        long delay = 0L;
        for (int i = 0; i < moves.size(); i++) {
            int tileValue = moves.get(i);
            boolean isLast = i == moves.size() - 1;
            autoSolveHandler.postDelayed(() -> performAutoMove(tileValue, isLast), delay);
            delay += 360L;
        }
    }

    private void performAutoMove(int tileValue, boolean isLast) {
        GridLayout grid = findViewById(R.id.puzzleGrid);
        TextView targetTile = null;
        TextView blankTile = null;
        for (int i = 0; i < grid.getChildCount(); i++) {
            TextView tile = (TextView) grid.getChildAt(i);
            Object tag = tile.getTag(R.id.tag_tile_content);
            if (tag instanceof Integer) {
                int value = (Integer) tag;
                if (value == tileValue) {
                    targetTile = tile;
                } else if (value == BLANK_INDEX) {
                    blankTile = tile;
                }
            }
        }
        if (targetTile == null || blankTile == null) {
            abortAutoSolving();
            return;
        }

        int targetPosition = grid.indexOfChild(targetTile);
        int blankPosition = grid.indexOfChild(blankTile);
        if (!isAdjacent(targetPosition, blankPosition)) {
            abortAutoSolving();
            return;
        }

        float dx = blankTile.getLeft() - targetTile.getLeft();
        float dy = blankTile.getTop() - targetTile.getTop();
        final TextView finalTargetTile = targetTile;
        final TextView finalBlankTile = blankTile;
        finalTargetTile.bringToFront();
        finalTargetTile.animate()
                .translationX(dx)
                .translationY(dy)
                .setDuration(320)
                .setInterpolator(new AccelerateDecelerateInterpolator())
                .withEndAction(() -> {
                    finalTargetTile.setTranslationX(0f);
                    finalTargetTile.setTranslationY(0f);
                    transferTileContent(finalTargetTile, finalBlankTile);
                    if (isLast) {
                        finishAutoSolving();
                    }
                })
                .start();
        finalBlankTile.animate()
                .scaleX(0.95f)
                .scaleY(0.95f)
                .setDuration(160)
                .setInterpolator(new AccelerateDecelerateInterpolator())
                .withEndAction(() -> finalBlankTile.animate()
                        .scaleX(1f)
                        .scaleY(1f)
                        .setDuration(160)
                        .setInterpolator(new AccelerateDecelerateInterpolator()))
                .start();
    }

    private void finishAutoSolving() {
        autoSolveHandler.removeCallbacksAndMessages(null);
        puzzleSolved = true;
        autoSolving = false;
        setTileInteractivity(true);
        setControlsEnabled(true);
    }

    private void abortAutoSolving() {
        autoSolveHandler.removeCallbacksAndMessages(null);
        autoSolving = false;
        setTileInteractivity(true);
        setControlsEnabled(true);
    }

    private void transferTileContent(TextView from, TextView to) {
        to.setText(from.getText());
        to.setForeground(from.getForeground());
        Object fromTag = from.getTag(R.id.tag_tile_content);
        to.setTag(R.id.tag_tile_content, fromTag);

        from.setText("");
        from.setForeground(null);
        from.setTag(R.id.tag_tile_content, BLANK_INDEX);
    }

    private void registerMove() {
        if (!autoSolving) {
            moveCount++;
        }
        if (!autoSolving && !puzzleSolved && isPuzzleSolved()) {
            onPuzzleSolved();
        }
    }

    private boolean isPuzzleSolved() {
        GridLayout grid = findViewById(R.id.puzzleGrid);
        for (int i = 0; i < grid.getChildCount(); i++) {
            TextView tile = (TextView) grid.getChildAt(i);
            Object tag = tile.getTag(R.id.tag_tile_content);
            if (!(tag instanceof Integer)) {
                return false;
            }
            int contentIndex = (Integer) tag;
            if (contentIndex != i) {
                return false;
            }
        }
        return true;
    }

    private void onPuzzleSolved() {
        puzzleSolved = true;
        View dialogView = getLayoutInflater().inflate(R.layout.dialog_save_score, null);
        EditText nameInput = dialogView.findViewById(R.id.playerName);

        new AlertDialog.Builder(this)
                .setTitle(R.string.save_score_title)
                .setMessage(R.string.save_score_message)
                .setView(dialogView)
                .setPositiveButton(R.string.save, (dialog, which) -> {
                    String name = nameInput.getText().toString().trim();
                    if (name.isEmpty()) {
                        name = getString(R.string.default_player_name);
                    }
                    databaseHelper.insertScore(name, moveCount);
                    showRankingDialog();
                })
                .setNegativeButton(R.string.cancel, null)
                .show();
    }

    private void showRankingDialog() {
        List<ScoreEntry> scores = databaseHelper.getTopScores(20);
        Dialog dialog = new Dialog(this);
        dialog.requestWindowFeature(Window.FEATURE_NO_TITLE);
        dialog.setContentView(R.layout.dialog_ranking);
        dialog.setCancelable(true);

        Window window = dialog.getWindow();
        if (window != null) {
            window.setBackgroundDrawable(new ColorDrawable(android.graphics.Color.TRANSPARENT));
            window.setLayout(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
            window.setGravity(Gravity.CENTER);
        }

        ListView listView = dialog.findViewById(R.id.rankingList);
        TextView empty = dialog.findViewById(R.id.emptyRanking);

        if (scores.isEmpty()) {
            listView.setVisibility(View.GONE);
            empty.setVisibility(View.VISIBLE);
        } else {
            listView.setVisibility(View.VISIBLE);
            empty.setVisibility(View.GONE);
            listView.setAdapter(new RankingAdapter(this, scores));
        }

        dialog.setOnShowListener(d -> {
            View container = dialog.findViewById(R.id.dialogContainer);
            if (container != null) {
                container.setAlpha(0f);
                container.setScaleX(0.9f);
                container.setScaleY(0.9f);
                container.animate()
                        .alpha(1f)
                        .scaleX(1f)
                        .scaleY(1f)
                        .setDuration(300)
                        .setInterpolator(new AccelerateDecelerateInterpolator())
                        .start();
            }
        });

        dialog.show();
    }

    private void resetMoveTracking() {
        moveCount = 0;
        puzzleSolved = false;
        autoSolving = false;
        autoSolveHandler.removeCallbacksAndMessages(null);
        setTileInteractivity(true);
        setControlsEnabled(true);
    }

    private static class PuzzlePiece {
        final Drawable drawable;
        final int index;

        PuzzlePiece(Drawable drawable, int index) {
            this.drawable = drawable;
            this.index = index;
        }
    }

    private int dpToPx(int dp) {
        return Math.round(dp * getResources().getDisplayMetrics().density);
    }

    private int[] getCurrentState() {
        GridLayout grid = findViewById(R.id.puzzleGrid);
        int[] state = new int[grid.getChildCount()];
        for (int i = 0; i < grid.getChildCount(); i++) {
            TextView tile = (TextView) grid.getChildAt(i);
            Object tag = tile.getTag(R.id.tag_tile_content);
            if (tag instanceof Integer) {
                state[i] = (Integer) tag;
            } else {
                state[i] = BLANK_INDEX;
            }
        }
        return state;
    }

    private boolean isSolvedState(int[] state) {
        for (int i = 0; i < state.length; i++) {
            if (state[i] != i) {
                return false;
            }
        }
        return true;
    }

    private boolean isSolvable(List<Integer> order) {
        int[] array = new int[order.size()];
        for (int i = 0; i < order.size(); i++) {
            array[i] = order.get(i);
        }
        return isSolvable(array);
    }

    private boolean isSolvablePieces(List<PuzzlePiece> pieces) {
        List<Integer> order = new ArrayList<>();
        for (PuzzlePiece piece : pieces) {
            order.add(piece.index);
        }
        return isSolvable(order);
    }

    private boolean isSolvable(int[] state) {
        int inversions = 0;
        for (int i = 0; i < state.length; i++) {
            if (state[i] == BLANK_INDEX) continue;
            for (int j = i + 1; j < state.length; j++) {
                if (state[j] == BLANK_INDEX) continue;
                if (state[i] > state[j]) {
                    inversions++;
                }
            }
        }
        if (GRID_DIMENSION % 2 == 1) {
            return inversions % 2 == 0;
        }
        int blankRowFromBottom = GRID_DIMENSION - (findBlankRow(state));
        if (blankRowFromBottom % 2 == 0) {
            return inversions % 2 == 1;
        } else {
            return inversions % 2 == 0;
        }
    }

    private int findBlankRow(int[] state) {
        for (int i = 0; i < state.length; i++) {
            if (state[i] == BLANK_INDEX) {
                return i / GRID_DIMENSION;
            }
        }
        return 0;
    }

    private void setTileInteractivity(boolean enabled) {
        GridLayout grid = findViewById(R.id.puzzleGrid);
        if (grid == null) {
            return;
        }
        for (int i = 0; i < grid.getChildCount(); i++) {
            TextView tile = (TextView) grid.getChildAt(i);
            tile.setEnabled(enabled);
            tile.setClickable(enabled);
            tile.setFocusable(enabled);
            if (enabled) {
                tile.setOnTouchListener(touchListener);
                tile.setOnDragListener(dragListener);
            } else {
                tile.setOnTouchListener(null);
                tile.setOnDragListener(null);
            }
        }
    }

    private void setControlsEnabled(boolean enabled) {
        Button add = findViewById(R.id.btnImage);
        Button remove = findViewById(R.id.btnRemoveImage);
        Button ranking = findViewById(R.id.btnRanking);
        Button solve = findViewById(R.id.btnSolve);
        if (add != null) add.setEnabled(enabled);
        if (remove != null) remove.setEnabled(enabled);
        if (ranking != null) ranking.setEnabled(enabled);
        if (solve != null) solve.setEnabled(enabled);
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        EdgeToEdge.enable(this);
        setContentView(R.layout.activity_main);
        ViewCompat.setOnApplyWindowInsetsListener(findViewById(R.id.main), (v, insets) -> {
            Insets systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars());
            v.setPadding(systemBars.left, systemBars.top, systemBars.right, systemBars.bottom);
            return insets;
        });

        databaseHelper = new ScoreDatabaseHelper(this);
        puzzleSolver = new PuzzleSolver(GRID_DIMENSION);
        GridLayout grid = findViewById(R.id.puzzleGrid);
        setLetterPuzzle();
        for (int i = 0; i < grid.getChildCount(); i++) {
            TextView tile = (TextView) grid.getChildAt(i);
            tile.setTag(i);
            tile.setOnTouchListener(touchListener);
            tile.setOnDragListener(dragListener);
        }

        Button btn = findViewById(R.id.btnImage);
        btn.setOnClickListener(v -> imagePicker.launch("image/*"));
        Button remove = findViewById(R.id.btnRemoveImage);
        remove.setOnClickListener(v -> clearImagePuzzle());
        Button ranking = findViewById(R.id.btnRanking);
        ranking.setOnClickListener(v -> showRankingDialog());
        Button solve = findViewById(R.id.btnSolve);
        solve.setOnClickListener(v -> autoSolvePuzzle());
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        if (databaseHelper != null) {
            databaseHelper.close();
        }
        autoSolveHandler.removeCallbacksAndMessages(null);
    }
}
