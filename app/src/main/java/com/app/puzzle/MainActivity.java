package com.app.puzzle;

import android.Manifest;
import android.content.ClipData;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.graphics.Bitmap;
import android.graphics.ImageDecoder;
import android.graphics.drawable.BitmapDrawable;
import android.net.Uri;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.os.SystemClock;
import android.view.DragEvent;
import android.view.HapticFeedbackConstants;
import android.view.LayoutInflater;
import android.view.Menu;
import android.view.MenuItem;
import android.view.MotionEvent;
import android.view.View;
import android.widget.Button;
import android.widget.GridLayout;
import android.widget.ImageView;
import android.widget.TextView;

import androidx.activity.EdgeToEdge;
import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.PickVisualMediaRequest;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.annotation.NonNull;
import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.graphics.Insets;
import androidx.core.content.ContextCompat;
import androidx.core.view.ViewCompat;
import androidx.core.view.WindowInsetsCompat;

import com.app.puzzle.data.ScoreDatabaseHelper;
import com.app.puzzle.data.ScoreEntry;
import com.app.puzzle.solver.PuzzleSolver;
import com.google.android.material.appbar.MaterialToolbar;
import com.google.android.material.button.MaterialButton;
import com.google.android.material.card.MaterialCardView;
import com.google.android.material.dialog.MaterialAlertDialogBuilder;
import com.google.android.material.snackbar.Snackbar;
import com.google.android.material.textfield.TextInputEditText;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Locale;
import java.util.concurrent.TimeUnit;

public class MainActivity extends AppCompatActivity {

    private static final int MIN_GRID_SIZE = 3;
    private static final String PREFS_NAME = "puzzle_progress";
    private static final String KEY_LEVEL = "progress_level";
    private static final String KEY_NICKNAME = "progress_nickname";
    private static final String KEY_TOTAL_DURATION = "progress_total_duration";
    private static final String KEY_TOTAL_MOVES = "progress_total_moves";
    private static final String AUTO_SOLVE_NICKNAME = "IA";

    private final List<TextView> tiles = new ArrayList<>();
    private final List<Integer> currentBoard = new ArrayList<>();
    private final List<Bitmap> currentImageTiles = new ArrayList<>();

    private GridLayout puzzleGrid;
    private TextView moveCounterView;
    private TextView timeCounterView;
    private TextView statusMessageView;
    private TextView levelIndicatorView;
    private ImageView referenceImageView;
    private MaterialCardView referenceCard;
    private MaterialCardView boardCard;
    private MaterialButton pickImageButton;
    private MaterialButton captureImageButton;
    private MaterialButton shuffleButton;
    private MaterialButton autoSolveButton;
    private View rootView;
    private int moveCounter = 0;
    private boolean imageMode = false;
    private boolean puzzleSolved = false;
    private int currentLevel = 1;
    private int currentGridSize = MIN_GRID_SIZE;
    private int tileCount = currentGridSize * currentGridSize;
    private int blankTileIndex = tileCount - 1;
    private long accumulatedDurationMillis = 0L;
    private long accumulatedMoveCount = 0L;
    private String savedNickname;

    private ActivityResultLauncher<PickVisualMediaRequest> pickImageLauncher;
    private ActivityResultLauncher<Void> captureImageLauncher;
    private ActivityResultLauncher<String> cameraPermissionLauncher;

    private final Handler timerHandler = new Handler(Looper.getMainLooper());
    private final Handler autoSolveHandler = new Handler(Looper.getMainLooper());
    private long startTimeMillis = 0L;
    private boolean timerRunning = false;
    private boolean autoSolving = false;
    private List<Integer> pendingAutoSolveMoves;
    private int autoSolveStepIndex = 0;
    private boolean timerStarted = false;
    private Bitmap referenceSourceBitmap;
    private Bitmap scaledReferenceBitmap;

    private final Runnable timerRunnable = new Runnable() {
        @Override
        public void run() {
            if (!timerRunning) {
                return;
            }
            long elapsed = SystemClock.elapsedRealtime() - startTimeMillis;
            updateTimerText(elapsed);
            timerHandler.postDelayed(this, 1000);
        }
    };

    private ScoreDatabaseHelper databaseHelper;
    private SharedPreferences preferences;

    private final Runnable autoSolveRunnable = new Runnable() {
        @Override
        public void run() {
            if (!autoSolving || pendingAutoSolveMoves == null) {
                return;
            }
            if (autoSolveStepIndex >= pendingAutoSolveMoves.size()) {
                finishAutoSolveSuccess();
                return;
            }
            int fromIndex = pendingAutoSolveMoves.get(autoSolveStepIndex);
            int blankIndex = currentBoard.indexOf(blankTileIndex);
            if (blankIndex == -1 || !isAdjacent(fromIndex, blankIndex)) {
                finishAutoSolveFailure();
                return;
            }
            swapTiles(fromIndex, blankIndex);
            moveCounter++;
            updateMoveCounter();
            autoSolveStepIndex++;
            if (autoSolveStepIndex >= pendingAutoSolveMoves.size()) {
                finishAutoSolveSuccess();
            } else {
                autoSolveHandler.postDelayed(this, 250);
            }
        }
    };

    private interface NicknameCallback {
        void onNicknameChosen(String nickname);
    }

    private final View.OnTouchListener touchListener = (v, event) -> {
        if (autoSolving) {
            return false;
        }
        if (event.getAction() != MotionEvent.ACTION_DOWN) {
            return false;
        }
        if (!(v instanceof TextView)) {
            return false;
        }
        TextView tv = (TextView) v;
        int tilePosition = (int) tv.getTag();
        if (currentBoard.isEmpty() || currentBoard.get(tilePosition) == blankTileIndex) {
            return false;
        }
        View.DragShadowBuilder shadow = new View.DragShadowBuilder(v);
        ClipData data = ClipData.newPlainText("tile", String.valueOf(currentBoard.get(tilePosition)));
        v.performHapticFeedback(HapticFeedbackConstants.VIRTUAL_KEY);
        v.startDragAndDrop(data, shadow, v, 0);
        return true;
    };

    private final View.OnDragListener dragListener = (v, event) -> {
        if (autoSolving) {
            return false;
        }
        switch (event.getAction()) {
            case DragEvent.ACTION_DRAG_STARTED:
                return true;
            case DragEvent.ACTION_DRAG_ENTERED:
                if (v instanceof TextView) {
                    TextView enteredTile = (TextView) v;
                    int targetIndex = (int) enteredTile.getTag();
                    if (!currentBoard.isEmpty() && currentBoard.get(targetIndex) == blankTileIndex) {
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
                int fromIndex = (int) from.getTag();
                int toIndex = (int) to.getTag();
                if (currentBoard.get(toIndex) != blankTileIndex) {
                    return false;
                }
                if (isAdjacent(fromIndex, toIndex)) {
                    swapTiles(fromIndex, toIndex);
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

        rootView = findViewById(R.id.main);
        ViewCompat.setOnApplyWindowInsetsListener(rootView, (v, insets) -> {
            Insets systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars());
            v.setPadding(systemBars.left, systemBars.top, systemBars.right, systemBars.bottom);
            return insets;
        });

        MaterialToolbar toolbar = findViewById(R.id.topAppBar);
        setSupportActionBar(toolbar);

        puzzleGrid = findViewById(R.id.puzzleGrid);
        moveCounterView = findViewById(R.id.moveCounter);
        timeCounterView = findViewById(R.id.timeCounter);
        statusMessageView = findViewById(R.id.statusMessage);
        levelIndicatorView = findViewById(R.id.levelIndicator);
        referenceCard = findViewById(R.id.referenceCard);
        boardCard = findViewById(R.id.boardCard);
        referenceImageView = findViewById(R.id.referenceImage);
        pickImageButton = findViewById(R.id.pickImageButton);
        captureImageButton = findViewById(R.id.captureImageButton);
        shuffleButton = findViewById(R.id.shuffleButton);
        autoSolveButton = findViewById(R.id.autoSolveButton);

        databaseHelper = new ScoreDatabaseHelper(this);
        preferences = getSharedPreferences(PREFS_NAME, MODE_PRIVATE);
        loadPlayerProgress();
        configureForCurrentLevel();

        shuffleButton.setOnClickListener(v -> restartProgress());
        autoSolveButton.setOnClickListener(v -> startAutoSolve());

        pickImageButton.setOnClickListener(v -> launchImagePicker());
        captureImageButton.setOnClickListener(v -> launchCamera());

        registerActivityLaunchers();
        if (rootView != null) {
            rootView.addOnLayoutChangeListener((v, left, top, right, bottom, oldLeft, oldTop, oldRight, oldBottom) -> {
                if ((right - left) != (oldRight - oldLeft)) {
                    scheduleTileSizeUpdate();
                }
            });
        }
        resetBoard();
    }

    private void loadPlayerProgress() {
        if (preferences == null) {
            return;
        }
        currentLevel = Math.max(1, preferences.getInt(KEY_LEVEL, 1));
        accumulatedDurationMillis = preferences.getLong(KEY_TOTAL_DURATION, 0L);
        accumulatedMoveCount = preferences.getLong(KEY_TOTAL_MOVES, 0L);
        savedNickname = preferences.getString(KEY_NICKNAME, null);
        if (savedNickname != null) {
            savedNickname = savedNickname.trim();
        }
    }

    private void configureForCurrentLevel() {
        currentGridSize = MIN_GRID_SIZE + (currentLevel - 1);
        tileCount = currentGridSize * currentGridSize;
        blankTileIndex = tileCount - 1;
        rebuildPuzzleGrid();
        updateLevelIndicator();
    }

    private void rebuildPuzzleGrid() {
        if (puzzleGrid == null) {
            return;
        }
        tiles.clear();
        puzzleGrid.removeAllViews();
        puzzleGrid.setColumnCount(currentGridSize);
        puzzleGrid.setRowCount(currentGridSize);
        LayoutInflater inflater = LayoutInflater.from(this);
        for (int i = 0; i < tileCount; i++) {
            TextView tile = (TextView) inflater.inflate(R.layout.view_tile, puzzleGrid, false);
            tile.setId(View.generateViewId());
            tile.setTag(i);
            tile.setOnTouchListener(touchListener);
            tile.setOnDragListener(dragListener);
            puzzleGrid.addView(tile);
            tiles.add(tile);
        }
        currentBoard.clear();
        scheduleTileSizeUpdate();
    }

    private void updateLevelIndicator() {
        if (levelIndicatorView != null) {
            levelIndicatorView.setText(getString(R.string.level_indicator, currentLevel, currentGridSize));
        }
    }

    private void restartProgress() {
        cancelAutoSolveIfRunning();
        imageMode = false;
        if (referenceCard != null) {
            referenceCard.setVisibility(View.GONE);
        }
        if (referenceImageView != null) {
            referenceImageView.setImageDrawable(null);
        }
        releaseImageResources();
        currentLevel = 1;
        accumulatedDurationMillis = 0L;
        accumulatedMoveCount = 0L;
        persistProgress();
        configureForCurrentLevel();
        resetBoard();
    }

    private void resetBoard() {
        cancelAutoSolveIfRunning();
        updateLevelIndicator();
        List<Integer> board = generateSolvableBoard();
        applyBoard(board);
        moveCounter = 0;
        updateMoveCounter();
        puzzleSolved = false;
        startNewTimer();
        statusMessageView.setText(imageMode ? R.string.status_ready_image : R.string.status_ready_level);
        referenceCard.setVisibility(imageMode ? View.VISIBLE : View.GONE);
        if (autoSolveButton != null) {
            autoSolveButton.setEnabled(true);
        }
        setInteractionControlsEnabled(true);
        scheduleTileSizeUpdate();
    }

    private void handleSuccessfulMove() {
        startTimerIfNeeded();
        moveCounter++;
        updateMoveCounter();
        if (isSolved(currentBoard)) {
            if (!puzzleSolved) {
                puzzleSolved = true;
                long elapsed = stopTimer();
                statusMessageView.setText(getString(R.string.puzzle_solved_level, currentLevel));
                handlePuzzleCompletion(elapsed);
            }
        } else {
            statusMessageView.setText(imageMode ? R.string.status_keep_going_image : R.string.status_keep_going_level);
        }
        updateTilesAppearance();
    }

    private void updateMoveCounter() {
        moveCounterView.setText(getString(R.string.move_counter, moveCounter));
    }

    private void handlePuzzleCompletion(long elapsedMillis) {
        handlePuzzleCompletion(elapsedMillis, false);
    }

    private void handlePuzzleCompletion(long elapsedMillis, boolean autoSolved) {
        final long prospectiveDuration = accumulatedDurationMillis + elapsedMillis;
        final long prospectiveMoves = accumulatedMoveCount + moveCounter;
        if (autoSolved) {
            long aiDuration = elapsedMillis;
            long aiMoves = moveCounter;
            int aiLevel = currentLevel;
            if (databaseHelper != null) {
                ScoreEntry aiScore = databaseHelper.findScoreByNickname(AUTO_SOLVE_NICKNAME);
                if (aiScore != null) {
                    aiDuration += aiScore.getDurationMillis();
                    aiMoves += aiScore.getMoveCount();
                    aiLevel = Math.max(aiLevel, aiScore.getLevel());
                }
                long result = databaseHelper.upsertScore(aiScore, AUTO_SOLVE_NICKNAME, aiDuration, aiMoves, aiLevel);
                if (result == -1) {
                    Snackbar.make(rootView, R.string.error_saving_score, Snackbar.LENGTH_SHORT).show();
                } else {
                    Snackbar.make(rootView, R.string.auto_solve_ranked_as_ai, Snackbar.LENGTH_SHORT).show();
                }
            } else {
                Snackbar.make(rootView, R.string.error_saving_score, Snackbar.LENGTH_SHORT).show();
            }
            showCompletionDialog(true, elapsedMillis, prospectiveDuration);
            return;
        }
        showNicknameDialog(elapsedMillis, savedNickname, nickname -> {
            savedNickname = nickname;
            ScoreEntry existingScore = databaseHelper != null ? databaseHelper.findScoreByNickname(nickname) : null;
            accumulatedDurationMillis = prospectiveDuration;
            accumulatedMoveCount = prospectiveMoves;
            persistProgress();
            long result = databaseHelper != null
                    ? databaseHelper.upsertScore(existingScore, savedNickname, accumulatedDurationMillis, accumulatedMoveCount, currentLevel)
                    : -1;
            if (result == -1) {
                Snackbar.make(rootView, R.string.error_saving_score, Snackbar.LENGTH_SHORT).show();
            } else {
                int messageRes = existingScore == null ? R.string.score_saved : R.string.score_updated;
                Snackbar.make(rootView, messageRes, Snackbar.LENGTH_SHORT).show();
            }
            showCompletionDialog(false, elapsedMillis, accumulatedDurationMillis);
        });
    }

    private void showCompletionDialog(boolean autoSolved, long levelDurationMillis, long totalDurationMillis) {
        if (autoSolved) {
            showAutoSolveAdvanceDialog(levelDurationMillis, totalDurationMillis);
        } else {
            showLevelCompleteDialog(levelDurationMillis, totalDurationMillis);
        }
    }

    private void showLevelCompleteDialog(long levelDurationMillis, long totalDurationMillis) {
        String levelTime = formatDuration(levelDurationMillis);
        String totalTime = formatDuration(totalDurationMillis);
        new MaterialAlertDialogBuilder(this)
                .setTitle(getString(R.string.dialog_title_level_complete, currentLevel))
                .setMessage(getString(R.string.dialog_message_level_complete, levelTime, totalTime))
                .setPositiveButton(R.string.action_next_level, (dialog, which) -> {
                    dialog.dismiss();
                    advanceToNextLevel();
                })
                .setCancelable(false)
                .show();
    }

    private void showAutoSolveAdvanceDialog(long levelDurationMillis, long totalDurationMillis) {
        String levelTime = formatDuration(levelDurationMillis);
        String totalTime = formatDuration(totalDurationMillis);
        new MaterialAlertDialogBuilder(this)
                .setTitle(R.string.dialog_title_auto_solve_complete)
                .setMessage(getString(R.string.dialog_message_auto_solve_complete, levelTime, totalTime))
                .setPositiveButton(R.string.action_next_level, (dialog, which) -> {
                    dialog.dismiss();
                    advanceToNextLevel();
                })
                .setNegativeButton(R.string.action_cancel, (dialog, which) -> dialog.dismiss())
                .setCancelable(false)
                .show();
    }

    private void advanceToNextLevel() {
        currentLevel++;
        imageMode = false;
        referenceCard.setVisibility(View.GONE);
        referenceImageView.setImageDrawable(null);
        releaseImageResources();
        persistProgress();
        configureForCurrentLevel();
        resetBoard();
    }

    private void persistProgress() {
        if (preferences == null) {
            return;
        }
        SharedPreferences.Editor editor = preferences.edit();
        editor.putInt(KEY_LEVEL, currentLevel);
        editor.putLong(KEY_TOTAL_DURATION, accumulatedDurationMillis);
        editor.putLong(KEY_TOTAL_MOVES, accumulatedMoveCount);
        if (savedNickname != null) {
            editor.putString(KEY_NICKNAME, savedNickname);
        }
        editor.apply();
    }

    private void startNewTimer() {
        timerHandler.removeCallbacks(timerRunnable);
        startTimeMillis = 0L;
        timerStarted = false;
        timerRunning = false;
        updateTimerText(0);
    }

    private void startTimerIfNeeded() {
        if (timerStarted && timerRunning) {
            return;
        }
        startTimeMillis = SystemClock.elapsedRealtime();
        timerStarted = true;
        timerRunning = true;
        timerHandler.removeCallbacks(timerRunnable);
        timerHandler.postDelayed(timerRunnable, 1000);
    }

    private long stopTimer() {
        if (!timerStarted) {
            timerRunning = false;
            timerHandler.removeCallbacks(timerRunnable);
            updateTimerText(0);
            return 0L;
        }
        long elapsed = SystemClock.elapsedRealtime() - startTimeMillis;
        timerRunning = false;
        timerHandler.removeCallbacks(timerRunnable);
        updateTimerText(elapsed);
        return elapsed;
    }

    private void updateTimerText(long elapsedMillis) {
        if (timeCounterView != null) {
            timeCounterView.setText(getString(R.string.time_counter, formatDuration(elapsedMillis)));
        }
    }

    private String formatDuration(long durationMillis) {
        long totalMinutes = TimeUnit.MILLISECONDS.toMinutes(durationMillis);
        long hours = totalMinutes / 60;
        long minutes = totalMinutes - (hours * 60);
        long seconds = TimeUnit.MILLISECONDS.toSeconds(durationMillis)
                - TimeUnit.MINUTES.toSeconds(totalMinutes);
        if (hours > 0) {
            return String.format(Locale.getDefault(), "%02d:%02d:%02d", hours, minutes, seconds);
        }
        return String.format(Locale.getDefault(), "%02d:%02d", minutes, seconds);
    }

    private void applyBoard(List<Integer> board) {
        currentBoard.clear();
        currentBoard.addAll(board);
        for (int i = 0; i < tiles.size() && i < board.size(); i++) {
            updateTileAppearance(i);
        }
        updateTilesAppearance();
    }

    private void updateTilesAppearance() {
        for (int i = 0; i < tiles.size(); i++) {
            TextView tile = tiles.get(i);
            boolean isBlank = currentBoard.get(i) == blankTileIndex;
            tile.setAlpha(isBlank ? 0.35f : 1f);
            if (isBlank) {
                tile.setContentDescription(getString(R.string.empty_tile_description));
            } else if (imageMode) {
                tile.setContentDescription(getString(R.string.tile_image_description, currentBoard.get(i) + 1));
            } else {
                tile.setContentDescription(getString(R.string.tile_description, tile.getText()));
            }
        }
    }

    private void updateTileAppearance(int tilePosition) {
        TextView tileView = tiles.get(tilePosition);
        int tileIndex = currentBoard.get(tilePosition);
        if (imageMode) {
            tileView.setText("");
            tileView.setBackgroundResource(R.drawable.bg_tile);
            if (tileIndex == blankTileIndex) {
                tileView.setForeground(null);
            } else {
                Bitmap bitmap = tileIndex < currentImageTiles.size() ? currentImageTiles.get(tileIndex) : null;
                if (bitmap != null) {
                    tileView.setForeground(new BitmapDrawable(getResources(), bitmap));
                } else {
                    tileView.setForeground(null);
                }
            }
        } else {
            tileView.setForeground(null);
            if (tileIndex == blankTileIndex) {
                tileView.setText("");
            } else {
                tileView.setText(getTileLabel(tileIndex));
            }
            tileView.setBackgroundResource(R.drawable.bg_tile);
        }
    }

    private String getTileLabel(int tileIndex) {
        if (tileIndex < 0) {
            return "";
        }
        if (tileIndex < 26) {
            return String.valueOf((char) ('A' + tileIndex));
        }
        return String.valueOf(tileIndex - 25);
    }

    private List<Integer> generateSolvableBoard() {
        List<Integer> board = new ArrayList<>(tileCount);
        for (int i = 0; i < tileCount; i++) {
            board.add(i);
        }
        do {
            Collections.shuffle(board);
        } while (!isSolvable(board) || isSolved(board));
        return board;
    }

    private boolean isSolved(List<Integer> board) {
        for (int i = 0; i < board.size(); i++) {
            if (board.get(i) != i) {
                return false;
            }
        }
        return true;
    }

    private boolean isSolvable(List<Integer> board) {
        int inversions = 0;
        for (int i = 0; i < board.size(); i++) {
            int current = board.get(i);
            if (current == blankTileIndex) {
                continue;
            }
            for (int j = i + 1; j < board.size(); j++) {
                int next = board.get(j);
                if (next == blankTileIndex) {
                    continue;
                }
                if (current > next) {
                    inversions++;
                }
            }
        }
        if (currentGridSize % 2 == 1) {
            return inversions % 2 == 0;
        }
        int blankRow = board.indexOf(blankTileIndex) / currentGridSize;
        int blankRowFromBottom = currentGridSize - blankRow;
        if (blankRowFromBottom % 2 == 0) {
            return inversions % 2 == 1;
        } else {
            return inversions % 2 == 0;
        }
    }

    private boolean isAdjacent(int a, int b) {
        int ar = a / currentGridSize;
        int ac = a % currentGridSize;
        int br = b / currentGridSize;
        int bc = b % currentGridSize;
        return Math.abs(ar - br) + Math.abs(ac - bc) == 1;
    }

    private void swapTiles(int fromIndex, int toIndex) {
        Collections.swap(currentBoard, fromIndex, toIndex);
        updateTileAppearance(fromIndex);
        updateTileAppearance(toIndex);
        updateTilesAppearance();
    }

    private void showNicknameDialog(long elapsedMillis, String initialNickname, NicknameCallback callback) {
        View dialogView = getLayoutInflater().inflate(R.layout.dialog_nickname, null);
        TextInputEditText nicknameInput = dialogView.findViewById(R.id.nicknameInput);
        if (initialNickname != null) {
            String trimmedNickname = initialNickname.trim();
            if (!trimmedNickname.isEmpty()) {
                nicknameInput.setText(trimmedNickname);
                nicknameInput.setSelection(trimmedNickname.length());
            }
        }
        nicknameInput.requestFocus();

        MaterialAlertDialogBuilder builder = new MaterialAlertDialogBuilder(this)
                .setTitle(R.string.dialog_title_nickname)
                .setMessage(getString(R.string.dialog_message_nickname, formatDuration(elapsedMillis)))
                .setView(dialogView)
                .setNegativeButton(R.string.action_cancel, (dialog, which) -> dialog.dismiss())
                .setPositiveButton(R.string.action_save_score, null);

        AlertDialog dialog = builder.create();
        dialog.setOnShowListener(dialogInterface -> {
            Button positiveButton = dialog.getButton(AlertDialog.BUTTON_POSITIVE);
            positiveButton.setOnClickListener(v -> {
                String nickname = nicknameInput.getText() != null
                        ? nicknameInput.getText().toString().trim()
                        : "";
                if (nickname.isEmpty()) {
                    nicknameInput.setError(getString(R.string.error_empty_nickname));
                    return;
                }
                nicknameInput.setError(null);
                if (callback != null) {
                    callback.onNicknameChosen(nickname);
                }
                dialog.dismiss();
            });
        });
        dialog.show();
    }

    private void registerActivityLaunchers() {
        pickImageLauncher = registerForActivityResult(
                new ActivityResultContracts.PickVisualMedia(),
                uri -> {
                    if (uri != null) {
                        loadCustomImage(uri);
                    }
                }
        );

        captureImageLauncher = registerForActivityResult(
                new ActivityResultContracts.TakePicturePreview(),
                bitmap -> {
                    if (bitmap != null) {
                        handleNewCustomImage(bitmap);
                    }
                }
        );

        cameraPermissionLauncher = registerForActivityResult(
                new ActivityResultContracts.RequestPermission(),
                granted -> {
                    if (Boolean.TRUE.equals(granted)) {
                        startCameraCapture();
                    } else {
                        Snackbar.make(rootView, R.string.error_camera_permission, Snackbar.LENGTH_SHORT).show();
                    }
                }
        );
    }

    private void startAutoSolve() {
        if (autoSolving) {
            Snackbar.make(rootView, R.string.auto_solve_in_progress, Snackbar.LENGTH_SHORT).show();
            return;
        }
        if (isSolved(currentBoard)) {
            statusMessageView.setText(R.string.status_already_solved);
            return;
        }
        statusMessageView.setText(R.string.status_solving);
        autoSolving = true;
        if (autoSolveButton != null) {
            autoSolveButton.setEnabled(false);
        }
        setInteractionControlsEnabled(false);
        List<Integer> boardSnapshot = new ArrayList<>(currentBoard);
        new Thread(() -> {
            List<Integer> solutionMoves = PuzzleSolver.solve(boardSnapshot, currentGridSize);
            runOnUiThread(() -> handleAutoSolveResult(solutionMoves));
        }).start();
    }

    private void handleAutoSolveResult(List<Integer> solutionMoves) {
        if (!autoSolving) {
            return;
        }
        if (solutionMoves == null || solutionMoves.isEmpty()) {
            finishAutoSolveFailure();
            return;
        }
        pendingAutoSolveMoves = solutionMoves;
        autoSolveStepIndex = 0;
        autoSolveHandler.post(autoSolveRunnable);
    }

    private void finishAutoSolveSuccess() {
        autoSolveHandler.removeCallbacks(autoSolveRunnable);
        autoSolving = false;
        pendingAutoSolveMoves = null;
        autoSolveStepIndex = 0;
        puzzleSolved = true;
        long elapsed = stopTimer();
        statusMessageView.setText(R.string.status_auto_solved);
        if (autoSolveButton != null) {
            autoSolveButton.setEnabled(true);
        }
        setInteractionControlsEnabled(true);
        updateTilesAppearance();
        handlePuzzleCompletion(elapsed, true);
    }

    private void finishAutoSolveFailure() {
        autoSolveHandler.removeCallbacks(autoSolveRunnable);
        autoSolving = false;
        pendingAutoSolveMoves = null;
        autoSolveStepIndex = 0;
        if (autoSolveButton != null) {
            autoSolveButton.setEnabled(true);
        }
        setInteractionControlsEnabled(true);
        statusMessageView.setText(R.string.status_auto_solve_failed);
        Snackbar.make(rootView, R.string.status_auto_solve_failed, Snackbar.LENGTH_SHORT).show();
    }

    private void cancelAutoSolveIfRunning() {
        if (!autoSolving) {
            return;
        }
        autoSolveHandler.removeCallbacks(autoSolveRunnable);
        autoSolving = false;
        pendingAutoSolveMoves = null;
        autoSolveStepIndex = 0;
        if (autoSolveButton != null) {
            autoSolveButton.setEnabled(true);
        }
        setInteractionControlsEnabled(true);
    }

    private void setInteractionControlsEnabled(boolean enabled) {
        if (shuffleButton != null) {
            shuffleButton.setEnabled(enabled);
        }
        if (pickImageButton != null) {
            pickImageButton.setEnabled(enabled);
        }
        if (captureImageButton != null) {
            captureImageButton.setEnabled(enabled);
        }
    }

    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        getMenuInflater().inflate(R.menu.menu_main, menu);
        return true;
    }

    @Override
    public boolean onOptionsItemSelected(@NonNull MenuItem item) {
        if (item.getItemId() == R.id.action_ranking) {
            startActivity(new Intent(this, RankingActivity.class));
            return true;
        }
        return super.onOptionsItemSelected(item);
    }

    private void launchImagePicker() {
        if (pickImageLauncher != null) {
            pickImageLauncher.launch(new PickVisualMediaRequest.Builder()
                    .setMediaType(ActivityResultContracts.PickVisualMedia.ImageOnly.INSTANCE)
                    .build());
        }
    }

    private void launchCamera() {
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.CAMERA)
                == PackageManager.PERMISSION_GRANTED) {
            startCameraCapture();
        } else if (cameraPermissionLauncher != null) {
            cameraPermissionLauncher.launch(Manifest.permission.CAMERA);
        }
    }

    private void startCameraCapture() {
        if (captureImageLauncher != null) {
            captureImageLauncher.launch(null);
        }
    }

    private void loadCustomImage(Uri uri) {
        try {
            ImageDecoder.Source source = ImageDecoder.createSource(getContentResolver(), uri);
            Bitmap bitmap = ImageDecoder.decodeBitmap(source);
            handleNewCustomImage(bitmap);
        } catch (Exception e) {
            Snackbar.make(rootView, R.string.error_loading_image, Snackbar.LENGTH_SHORT).show();
        }
    }

    private void handleNewCustomImage(Bitmap bitmap) {
        if (bitmap == null) {
            Snackbar.make(rootView, R.string.error_loading_image, Snackbar.LENGTH_SHORT).show();
            return;
        }
        cancelAutoSolveIfRunning();
        releaseImageResources();
        referenceSourceBitmap = prepareBitmap(bitmap);
        updateScaledReferenceBitmap();
        if (scaledReferenceBitmap != null) {
            sliceBitmapIntoTiles(scaledReferenceBitmap);
            referenceImageView.setImageBitmap(scaledReferenceBitmap);
            referenceCard.setVisibility(View.VISIBLE);
            imageMode = true;
        } else {
            referenceImageView.setImageDrawable(null);
            referenceCard.setVisibility(View.GONE);
            imageMode = false;
        }
        statusMessageView.setText(imageMode ? R.string.status_ready_image : R.string.status_ready_level);
        resetBoard();
    }

    private Bitmap prepareBitmap(Bitmap original) {
        int size = Math.min(original.getWidth(), original.getHeight());
        int xOffset = (original.getWidth() - size) / 2;
        int yOffset = (original.getHeight() - size) / 2;
        return Bitmap.createBitmap(original, xOffset, yOffset, size, size);
    }

    private void sliceBitmapIntoTiles(Bitmap bitmap) {
        clearTileForegrounds();
        releaseCurrentImageTiles();
        currentImageTiles.clear();
        if (bitmap == null) {
            return;
        }
        Bitmap workingBitmap = bitmap;
        int desiredTileSize = getDesiredTileSizePx();
        if (desiredTileSize > 0) {
            int targetSize = desiredTileSize * currentGridSize;
            if (targetSize > 0 && bitmap.getWidth() != targetSize) {
                workingBitmap = Bitmap.createScaledBitmap(bitmap, targetSize, targetSize, true);
            }
        }
        int tileSize = workingBitmap.getWidth() / currentGridSize;
        for (int row = 0; row < currentGridSize; row++) {
            for (int col = 0; col < currentGridSize; col++) {
                if (row == currentGridSize - 1 && col == currentGridSize - 1) {
                    currentImageTiles.add(null);
                    continue;
                }
                int left = col * tileSize;
                int top = row * tileSize;
                Bitmap tileBitmap = Bitmap.createBitmap(workingBitmap, left, top, tileSize, tileSize);
                if (tileBitmap.getWidth() != desiredTileSize && desiredTileSize > 0) {
                    Bitmap scaledTile = Bitmap.createScaledBitmap(tileBitmap, desiredTileSize, desiredTileSize, true);
                    tileBitmap.recycle();
                    tileBitmap = scaledTile;
                }
                currentImageTiles.add(tileBitmap);
            }
        }
        // Ensure list has tileCount elements (last null already added)
        if (currentImageTiles.size() < tileCount) {
            currentImageTiles.add(null);
        }
    }

    private void clearTileForegrounds() {
        for (TextView tile : tiles) {
            tile.setForeground(null);
        }
    }

    private void releaseCurrentImageTiles() {
        for (Bitmap bitmap : currentImageTiles) {
            if (bitmap != null && !bitmap.isRecycled()) {
                bitmap.recycle();
            }
        }
    }

    private void releaseScaledReferenceBitmap() {
        if (scaledReferenceBitmap != null
                && scaledReferenceBitmap != referenceSourceBitmap
                && !scaledReferenceBitmap.isRecycled()) {
            scaledReferenceBitmap.recycle();
        }
        scaledReferenceBitmap = null;
    }

    private void releaseReferenceSourceBitmap() {
        if (referenceSourceBitmap != null && !referenceSourceBitmap.isRecycled()) {
            referenceSourceBitmap.recycle();
        }
        referenceSourceBitmap = null;
    }

    private void releaseImageResources() {
        clearTileForegrounds();
        releaseCurrentImageTiles();
        currentImageTiles.clear();
        if (referenceImageView != null) {
            referenceImageView.setImageDrawable(null);
        }
        releaseScaledReferenceBitmap();
        releaseReferenceSourceBitmap();
    }

    private void updateScaledReferenceBitmap() {
        if (referenceSourceBitmap == null) {
            releaseScaledReferenceBitmap();
            return;
        }
        int desiredTileSize = getDesiredTileSizePx();
        int targetSize = desiredTileSize > 0
                ? desiredTileSize * currentGridSize
                : referenceSourceBitmap.getWidth();
        if (targetSize <= 0) {
            targetSize = referenceSourceBitmap.getWidth();
        }
        if (scaledReferenceBitmap != null
                && !scaledReferenceBitmap.isRecycled()
                && scaledReferenceBitmap.getWidth() == targetSize) {
            return;
        }
        releaseScaledReferenceBitmap();
        scaledReferenceBitmap = Bitmap.createScaledBitmap(referenceSourceBitmap, targetSize, targetSize, true);
    }

    private void scheduleTileSizeUpdate() {
        if (puzzleGrid != null) {
            puzzleGrid.post(this::updateTileSizes);
        }
    }

    private void updateTileSizes() {
        if (puzzleGrid == null || tiles.isEmpty()) {
            return;
        }
        int tileSize = getDesiredTileSizePx();
        if (tileSize <= 0) {
            puzzleGrid.post(this::updateTileSizes);
            return;
        }
        boolean updated = false;
        for (TextView tile : tiles) {
            GridLayout.LayoutParams params = (GridLayout.LayoutParams) tile.getLayoutParams();
            if (params == null) {
                params = new GridLayout.LayoutParams();
            }
            if (params.width != tileSize || params.height != tileSize) {
                params.width = tileSize;
                params.height = tileSize;
                tile.setLayoutParams(params);
                updated = true;
            }
        }
        if (updated) {
            puzzleGrid.requestLayout();
            refreshImageTilesIfNeeded();
        }
    }

    private void refreshImageTilesIfNeeded() {
        if (!imageMode || referenceSourceBitmap == null) {
            return;
        }
        updateScaledReferenceBitmap();
        if (scaledReferenceBitmap == null || scaledReferenceBitmap.isRecycled()) {
            return;
        }
        sliceBitmapIntoTiles(scaledReferenceBitmap);
        if (!currentBoard.isEmpty()) {
            applyBoard(new ArrayList<>(currentBoard));
        }
        if (referenceImageView != null) {
            referenceImageView.setImageBitmap(scaledReferenceBitmap);
        }
    }

    private int computeAvailableBoardWidth() {
        int width = 0;
        if (boardCard != null) {
            width = boardCard.getWidth()
                    - boardCard.getContentPaddingLeft()
                    - boardCard.getContentPaddingRight();
        }
        if (width <= 0 && puzzleGrid != null) {
            View parent = (View) puzzleGrid.getParent();
            if (parent != null) {
                width = parent.getWidth() - parent.getPaddingLeft() - parent.getPaddingRight();
            }
        }
        if (width <= 0 && puzzleGrid != null) {
            width = puzzleGrid.getWidth();
        }
        if (width > 0 && puzzleGrid != null) {
            width -= puzzleGrid.getPaddingLeft() + puzzleGrid.getPaddingRight();
        }
        if (width <= 0 && rootView != null) {
            int margin = getResources().getDimensionPixelSize(R.dimen.screen_margin) * 2;
            width = rootView.getWidth() - margin;
        }
        return width;
    }

    private int getDesiredTileSizePx() {
        int availableWidth = computeAvailableBoardWidth();
        int baseSize = getResources().getDimensionPixelSize(R.dimen.tile_size);
        if (availableWidth <= 0 || currentGridSize <= 0) {
            return baseSize;
        }
        int spacing = getResources().getDimensionPixelSize(R.dimen.tile_spacing);
        int totalSpacing = spacing * 2 * currentGridSize;
        int effectiveWidth = availableWidth - totalSpacing;
        if (effectiveWidth <= 0) {
            effectiveWidth = availableWidth;
        }
        int dynamicSize = effectiveWidth / currentGridSize;
        if (dynamicSize <= 0) {
            dynamicSize = Math.max(1, availableWidth / currentGridSize);
        }
        return Math.max(1, dynamicSize);
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        timerHandler.removeCallbacks(timerRunnable);
        if (databaseHelper != null) {
            databaseHelper.close();
        }
        releaseImageResources();
        autoSolveHandler.removeCallbacksAndMessages(null);
    }
}
