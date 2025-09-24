package com.app.puzzle;

import android.Manifest;
import android.content.ClipData;
import android.content.Intent;
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
import android.view.MotionEvent;
import android.view.View;
import android.view.Menu;
import android.view.MenuItem;
import android.widget.GridLayout;
import android.widget.ImageView;
import android.widget.TextView;
import android.widget.Button;

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
import com.google.android.material.appbar.MaterialToolbar;
import com.google.android.material.button.MaterialButton;
import com.google.android.material.card.MaterialCardView;
import com.google.android.material.dialog.MaterialAlertDialogBuilder;
import com.google.android.material.snackbar.Snackbar;
import com.google.android.material.textfield.TextInputEditText;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Locale;
import java.util.concurrent.TimeUnit;

public class MainActivity extends AppCompatActivity {

    private static final int GRID_SIZE = 3;
    private static final int TILE_COUNT = GRID_SIZE * GRID_SIZE;
    private static final int BLANK_TILE_INDEX = TILE_COUNT - 1;
    private static final List<String> LETTER_GOAL_STATE = Arrays.asList(
            "A", "B", "C", "D", "E", "F", "G", "H", "");

    private final List<TextView> tiles = new ArrayList<>();
    private final List<Integer> currentBoard = new ArrayList<>(TILE_COUNT);
    private final List<Bitmap> currentImageTiles = new ArrayList<>(TILE_COUNT);

    private GridLayout puzzleGrid;
    private TextView moveCounterView;
    private TextView timeCounterView;
    private TextView statusMessageView;
    private ImageView referenceImageView;
    private MaterialCardView referenceCard;
    private View rootView;
    private int moveCounter = 0;
    private boolean imageMode = false;
    private boolean puzzleSolved = false;

    private ActivityResultLauncher<PickVisualMediaRequest> pickImageLauncher;
    private ActivityResultLauncher<Void> captureImageLauncher;
    private ActivityResultLauncher<String> cameraPermissionLauncher;

    private final Handler timerHandler = new Handler(Looper.getMainLooper());
    private long startTimeMillis = 0L;
    private boolean timerRunning = false;

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

    private final View.OnTouchListener touchListener = (v, event) -> {
        if (event.getAction() != MotionEvent.ACTION_DOWN) {
            return false;
        }
        if (!(v instanceof TextView)) {
            return false;
        }
        TextView tv = (TextView) v;
        int tilePosition = (int) tv.getTag();
        if (currentBoard.isEmpty() || currentBoard.get(tilePosition) == BLANK_TILE_INDEX) {
            return false;
        }
        View.DragShadowBuilder shadow = new View.DragShadowBuilder(v);
        ClipData data = ClipData.newPlainText("tile", String.valueOf(currentBoard.get(tilePosition)));
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
                    int targetIndex = (int) enteredTile.getTag();
                    if (!currentBoard.isEmpty() && currentBoard.get(targetIndex) == BLANK_TILE_INDEX) {
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
                if (currentBoard.get(toIndex) != BLANK_TILE_INDEX) {
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
        referenceCard = findViewById(R.id.referenceCard);
        referenceImageView = findViewById(R.id.referenceImage);
        MaterialButton pickImageButton = findViewById(R.id.pickImageButton);
        MaterialButton captureImageButton = findViewById(R.id.captureImageButton);

        databaseHelper = new ScoreDatabaseHelper(this);

        prepareTiles();

        View shuffleButton = findViewById(R.id.shuffleButton);
        shuffleButton.setOnClickListener(v -> resetBoard());

        pickImageButton.setOnClickListener(v -> launchImagePicker());
        captureImageButton.setOnClickListener(v -> launchCamera());

        registerActivityLaunchers();
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
        List<Integer> board = generateSolvableBoard();
        applyBoard(board);
        moveCounter = 0;
        updateMoveCounter();
        puzzleSolved = false;
        startNewTimer();
        statusMessageView.setText(imageMode ? R.string.status_ready_image : R.string.status_ready);
        referenceCard.setVisibility(imageMode ? View.VISIBLE : View.GONE);
    }

    private void handleSuccessfulMove() {
        moveCounter++;
        updateMoveCounter();
        if (isSolved(currentBoard)) {
            if (!puzzleSolved) {
                puzzleSolved = true;
                long elapsed = stopTimer();
                statusMessageView.setText(R.string.puzzle_solved);
                showNicknameDialog(elapsed);
            }
        } else {
            statusMessageView.setText(imageMode ? R.string.status_keep_going_image : R.string.status_keep_going);
        }
        updateTilesAppearance();
    }

    private void updateMoveCounter() {
        moveCounterView.setText(getString(R.string.move_counter, moveCounter));
    }

    private void startNewTimer() {
        timerHandler.removeCallbacks(timerRunnable);
        startTimeMillis = SystemClock.elapsedRealtime();
        timerRunning = true;
        updateTimerText(0);
        timerHandler.postDelayed(timerRunnable, 1000);
    }

    private long stopTimer() {
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
        long minutes = TimeUnit.MILLISECONDS.toMinutes(durationMillis);
        long seconds = TimeUnit.MILLISECONDS.toSeconds(durationMillis) - TimeUnit.MINUTES.toSeconds(minutes);
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
            boolean isBlank = currentBoard.get(i) == BLANK_TILE_INDEX;
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
            if (tileIndex == BLANK_TILE_INDEX) {
                tileView.setForeground(null);
            } else {
                Bitmap bitmap = currentImageTiles.get(tileIndex);
                if (bitmap != null) {
                    tileView.setForeground(new BitmapDrawable(getResources(), bitmap));
                }
            }
        } else {
            tileView.setForeground(null);
            tileView.setText(LETTER_GOAL_STATE.get(tileIndex));
            tileView.setBackgroundResource(R.drawable.bg_tile);
        }
    }

    private List<Integer> generateSolvableBoard() {
        List<Integer> board = new ArrayList<>(TILE_COUNT);
        for (int i = 0; i < TILE_COUNT; i++) {
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
            if (current == BLANK_TILE_INDEX) {
                continue;
            }
            for (int j = i + 1; j < board.size(); j++) {
                int next = board.get(j);
                if (next == BLANK_TILE_INDEX) {
                    continue;
                }
                if (current > next) {
                    inversions++;
                }
            }
        }
        return inversions % 2 == 0;
    }

    private boolean isAdjacent(int a, int b) {
        int ar = a / GRID_SIZE;
        int ac = a % GRID_SIZE;
        int br = b / GRID_SIZE;
        int bc = b % GRID_SIZE;
        return Math.abs(ar - br) + Math.abs(ac - bc) == 1;
    }

    private void swapTiles(int fromIndex, int toIndex) {
        Collections.swap(currentBoard, fromIndex, toIndex);
        updateTileAppearance(fromIndex);
        updateTileAppearance(toIndex);
        updateTilesAppearance();
    }

    private void showNicknameDialog(long elapsedMillis) {
        View dialogView = getLayoutInflater().inflate(R.layout.dialog_nickname, null);
        TextInputEditText nicknameInput = dialogView.findViewById(R.id.nicknameInput);

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
                long result = databaseHelper.insertScore(nickname, elapsedMillis);
                if (result == -1) {
                    Snackbar.make(rootView, R.string.error_saving_score, Snackbar.LENGTH_SHORT).show();
                } else {
                    Snackbar.make(rootView, R.string.score_saved, Snackbar.LENGTH_SHORT).show();
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
        Bitmap preparedBitmap = prepareBitmap(bitmap);
        sliceBitmapIntoTiles(preparedBitmap);
        referenceImageView.setImageBitmap(preparedBitmap);
        referenceCard.setVisibility(View.VISIBLE);
        imageMode = true;
        statusMessageView.setText(R.string.status_ready_image);
        resetBoard();
    }

    private Bitmap prepareBitmap(Bitmap original) {
        int size = Math.min(original.getWidth(), original.getHeight());
        int xOffset = (original.getWidth() - size) / 2;
        int yOffset = (original.getHeight() - size) / 2;
        Bitmap square = Bitmap.createBitmap(original, xOffset, yOffset, size, size);
        int targetSize = getResources().getDimensionPixelSize(R.dimen.tile_size) * GRID_SIZE;
        if (square.getWidth() != targetSize) {
            return Bitmap.createScaledBitmap(square, targetSize, targetSize, true);
        }
        return square;
    }

    private void sliceBitmapIntoTiles(Bitmap bitmap) {
        clearTileForegrounds();
        releaseCurrentImageTiles();
        currentImageTiles.clear();
        int tileSize = bitmap.getWidth() / GRID_SIZE;
        for (int row = 0; row < GRID_SIZE; row++) {
            for (int col = 0; col < GRID_SIZE; col++) {
                if (row == GRID_SIZE - 1 && col == GRID_SIZE - 1) {
                    currentImageTiles.add(null);
                    continue;
                }
                int left = col * tileSize;
                int top = row * tileSize;
                Bitmap tileBitmap = Bitmap.createBitmap(bitmap, left, top, tileSize, tileSize);
                currentImageTiles.add(tileBitmap);
            }
        }
        // Ensure list has TILE_COUNT elements (last null already added)
        if (currentImageTiles.size() < TILE_COUNT) {
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

    @Override
    protected void onDestroy() {
        super.onDestroy();
        timerHandler.removeCallbacks(timerRunnable);
        if (databaseHelper != null) {
            databaseHelper.close();
        }
        releaseCurrentImageTiles();
        currentImageTiles.clear();
    }
}
