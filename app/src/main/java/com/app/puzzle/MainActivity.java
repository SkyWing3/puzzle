package com.app.puzzle;

import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.drawable.BitmapDrawable;
import android.graphics.drawable.Drawable;
import android.os.Bundle;
import android.view.DragEvent;
import android.view.MotionEvent;
import android.view.View;
import android.widget.Button;
import android.widget.GridLayout;
import android.widget.ImageView;
import android.widget.TextView;
import android.view.ViewGroup;
import android.view.animation.AccelerateDecelerateInterpolator;

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
import java.util.Arrays;
import java.util.Collections;
import java.util.List;

import androidx.transition.AutoTransition;
import androidx.transition.TransitionManager;

public class MainActivity extends AppCompatActivity {

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
                    to.setText(from.getText());
                    to.setForeground(from.getForeground());
                    from.setText("");
                    from.setForeground(null);
                    to.animate().scaleX(1.05f).scaleY(1.05f).setDuration(150)
                            .withEndAction(() -> to.animate().scaleX(1f).scaleY(1f).setDuration(150));
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
        int fromRow = fromIndex / 3;
        int fromCol = fromIndex % 3;
        int toRow = toIndex / 3;
        int toCol = toIndex % 3;
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
        reference.setImageBitmap(bitmap);

        int pieceWidth = bitmap.getWidth() / 3;
        int pieceHeight = bitmap.getHeight() / 3;
        List<Drawable> pieces = new ArrayList<>();
        for (int r = 0; r < 3; r++) {
            for (int c = 0; c < 3; c++) {
                Bitmap piece = Bitmap.createBitmap(bitmap, c * pieceWidth, r * pieceHeight, pieceWidth, pieceHeight);
                pieces.add(new BitmapDrawable(getResources(), piece));
            }
        }
        pieces.set(pieces.size() - 1, null);
        Collections.shuffle(pieces);
        for (int i = 0; i < grid.getChildCount(); i++) {
            TextView tile = (TextView) grid.getChildAt(i);
            tile.setForeground(pieces.get(i));
            tile.setText("");
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
        List<String> letters = Arrays.asList("A", "B", "C", "D", "E", "F", "G", "H", "");
        Collections.shuffle(letters);
        for (int i = 0; i < grid.getChildCount(); i++) {
            TextView tile = (TextView) grid.getChildAt(i);
            tile.setText(letters.get(i));
            tile.setForeground(null);
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

    private int dpToPx(int dp) {
        return Math.round(dp * getResources().getDisplayMetrics().density);
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
    }
}
