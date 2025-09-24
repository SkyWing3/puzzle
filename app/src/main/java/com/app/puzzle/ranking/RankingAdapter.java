package com.app.puzzle.ranking;

import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

import com.app.puzzle.R;
import com.app.puzzle.data.ScoreEntry;

import java.text.DateFormat;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.Locale;

public class RankingAdapter extends RecyclerView.Adapter<RankingAdapter.ScoreViewHolder> {

    public interface DurationFormatter {
        String format(long durationMillis);
    }

    private final List<ScoreEntry> scores = new ArrayList<>();
    private final DateFormat dateFormat = new SimpleDateFormat("dd/MM/yyyy HH:mm", Locale.getDefault());
    private final DurationFormatter durationFormatter;

    public RankingAdapter(DurationFormatter durationFormatter) {
        this.durationFormatter = durationFormatter;
    }

    public void submitList(List<ScoreEntry> newScores) {
        scores.clear();
        if (newScores != null) {
            scores.addAll(newScores);
        }
        notifyDataSetChanged();
    }

    @NonNull
    @Override
    public ScoreViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View view = LayoutInflater.from(parent.getContext()).inflate(R.layout.item_ranking, parent, false);
        return new ScoreViewHolder(view);
    }

    @Override
    public void onBindViewHolder(@NonNull ScoreViewHolder holder, int position) {
        ScoreEntry entry = scores.get(position);
        holder.positionView.setText(String.valueOf(position + 1));
        holder.nicknameView.setText(entry.getNickname());
        String formattedTime = durationFormatter.format(entry.getDurationMillis());
        holder.timeView.setText(holder.itemView.getContext().getString(R.string.ranking_time_label, formattedTime));
        holder.levelView.setText(holder.itemView.getContext().getString(R.string.ranking_level_label, entry.getLevel()));
        String dateLabel = dateFormat.format(new Date(entry.getCreatedAt()));
        holder.dateView.setText(holder.itemView.getContext().getString(R.string.ranking_date_label, dateLabel));
    }

    @Override
    public int getItemCount() {
        return scores.size();
    }

    static class ScoreViewHolder extends RecyclerView.ViewHolder {
        final TextView positionView;
        final TextView nicknameView;
        final TextView timeView;
        final TextView levelView;
        final TextView dateView;

        ScoreViewHolder(@NonNull View itemView) {
            super(itemView);
            positionView = itemView.findViewById(R.id.positionView);
            nicknameView = itemView.findViewById(R.id.nicknameView);
            timeView = itemView.findViewById(R.id.timeView);
            levelView = itemView.findViewById(R.id.levelView);
            dateView = itemView.findViewById(R.id.dateView);
        }
    }
}
