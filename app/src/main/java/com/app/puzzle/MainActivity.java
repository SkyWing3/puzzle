package com.app.puzzle;

import android.os.Bundle;
import android.view.DragEvent;
import android.view.MotionEvent;
import android.view.View;
import android.widget.GridLayout;
import android.widget.TextView;

import androidx.activity.EdgeToEdge;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.graphics.Insets;
import androidx.core.view.ViewCompat;
import androidx.core.view.WindowInsetsCompat;

import java.util.Arrays;
import java.util.Collections;
import java.util.List;

public class MainActivity extends AppCompatActivity {

    private final View.OnTouchListener touchListener = (v, event) -> {
        if (event.getAction() != MotionEvent.ACTION_DOWN) return false;
        if (!(v instanceof TextView)) return false;
        TextView tv = (TextView) v;
        if (tv.getText().toString().isEmpty()) return false;
        View.DragShadowBuilder shadow = new View.DragShadowBuilder(v);
        v.startDragAndDrop(null, shadow, v, 0);
        return true;
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
                if (!to.getText().toString().isEmpty()) return false;
                int fromIndex = (int) from.getTag();
                int toIndex = (int) to.getTag();
                if (isAdjacent(fromIndex, toIndex)) {
                    to.setText(from.getText());
                    from.setText("");
                }
                return true;
            default:
                return true;
        }
    };

    private boolean isAdjacent(int a, int b) {
        int ar = a / 3, ac = a % 3;
        int br = b / 3, bc = b % 3;
        return Math.abs(ar - br) + Math.abs(ac - bc) == 1;
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
        List<String> letters = Arrays.asList("A","B","C","D","E","F","G","H","");
        Collections.shuffle(letters);
        for (int i = 0; i < grid.getChildCount(); i++) {
            TextView tile = (TextView) grid.getChildAt(i);
            tile.setText(letters.get(i));
            tile.setTag(i);
            tile.setOnTouchListener(touchListener);
            tile.setOnDragListener(dragListener);
        }
    }
}
