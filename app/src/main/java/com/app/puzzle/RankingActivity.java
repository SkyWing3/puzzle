package com.app.puzzle;

import android.os.Bundle;
import android.text.TextUtils;
import android.view.View;
import android.widget.TextView;

import androidx.activity.EdgeToEdge;
import androidx.appcompat.app.AppCompatActivity;
import androidx.appcompat.widget.SearchView;
import androidx.core.graphics.Insets;
import androidx.core.view.ViewCompat;
import androidx.core.view.WindowInsetsCompat;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.app.puzzle.data.ScoreDatabaseHelper;
import com.app.puzzle.data.ScoreEntry;
import com.app.puzzle.ranking.RankingAdapter;
import com.google.android.material.appbar.MaterialToolbar;

import java.util.List;
import java.util.Locale;
import java.util.concurrent.TimeUnit;

public class RankingActivity extends AppCompatActivity {

    private ScoreDatabaseHelper databaseHelper;
    private RankingAdapter adapter;
    private TextView emptyView;
    private SearchView searchView;
    private String currentQuery = "";

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        EdgeToEdge.enable(this);
        setContentView(R.layout.activity_ranking);

        View root = findViewById(android.R.id.content);
        ViewCompat.setOnApplyWindowInsetsListener(root, (v, insets) -> {
            Insets systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars());
            v.setPadding(systemBars.left, systemBars.top, systemBars.right, systemBars.bottom);
            return insets;
        });

        MaterialToolbar toolbar = findViewById(R.id.rankingToolbar);
        setSupportActionBar(toolbar);
        toolbar.setNavigationOnClickListener(v -> finish());

        databaseHelper = new ScoreDatabaseHelper(this);
        adapter = new RankingAdapter(this::formatDuration);

        RecyclerView rankingList = findViewById(R.id.rankingList);
        rankingList.setLayoutManager(new LinearLayoutManager(this));
        rankingList.setAdapter(adapter);

        emptyView = findViewById(R.id.emptyView);

        searchView = findViewById(R.id.searchView);
        configureSearchView();

        loadScores(null);
    }

    private void configureSearchView() {
        searchView.setIconifiedByDefault(false);
        searchView.clearFocus();
        searchView.setOnQueryTextListener(new SearchView.OnQueryTextListener() {
            @Override
            public boolean onQueryTextSubmit(String query) {
                loadScores(query);
                searchView.clearFocus();
                return true;
            }

            @Override
            public boolean onQueryTextChange(String newText) {
                loadScores(newText);
                return true;
            }
        });
        searchView.setOnCloseListener(() -> {
            if (!TextUtils.isEmpty(currentQuery)) {
                searchView.setQuery("", false);
                loadScores(null);
            }
            return false;
        });
    }

    private void loadScores(String query) {
        String normalizedQuery = query == null ? "" : query.trim();
        currentQuery = normalizedQuery;
        List<ScoreEntry> scores = databaseHelper.searchScores(normalizedQuery);
        adapter.submitList(scores);
        updateEmptyState(scores);
    }

    private void updateEmptyState(List<ScoreEntry> scores) {
        boolean isEmpty = scores == null || scores.isEmpty();
        if (isEmpty) {
            emptyView.setVisibility(View.VISIBLE);
            if (TextUtils.isEmpty(currentQuery)) {
                emptyView.setText(R.string.ranking_empty);
            } else {
                emptyView.setText(getString(R.string.ranking_empty_search, currentQuery));
            }
        } else {
            emptyView.setVisibility(View.GONE);
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

    @Override
    protected void onDestroy() {
        super.onDestroy();
        if (databaseHelper != null) {
            databaseHelper.close();
        }
    }
}
