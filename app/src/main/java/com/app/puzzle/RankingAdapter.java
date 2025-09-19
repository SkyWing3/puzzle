package com.app.puzzle;

import android.content.Context;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.view.animation.AccelerateDecelerateInterpolator;
import android.widget.BaseAdapter;
import android.widget.TextView;

import java.util.List;

public class RankingAdapter extends BaseAdapter {

    private final List<ScoreEntry> scores;
    private final LayoutInflater inflater;

    public RankingAdapter(Context context, List<ScoreEntry> scores) {
        this.scores = scores;
        this.inflater = LayoutInflater.from(context);
    }

    @Override
    public int getCount() {
        return scores.size();
    }

    @Override
    public Object getItem(int position) {
        return scores.get(position);
    }

    @Override
    public long getItemId(int position) {
        return position;
    }

    @Override
    public View getView(int position, View convertView, ViewGroup parent) {
        ViewHolder holder;
        if (convertView == null) {
            convertView = inflater.inflate(R.layout.item_ranking_entry, parent, false);
            holder = new ViewHolder(convertView);
            convertView.setTag(holder);
        } else {
            holder = (ViewHolder) convertView.getTag();
        }

        ScoreEntry entry = scores.get(position);
        holder.position.setText(String.valueOf(position + 1));
        holder.name.setText(entry.getPlayerName());
        holder.moves.setText(parent.getResources().getString(R.string.moves_format, entry.getMoves()));

        float startDelay = position * 50f;
        convertView.setAlpha(0f);
        convertView.setTranslationY(40f);
        convertView.animate()
                .alpha(1f)
                .translationY(0f)
                .setStartDelay((long) startDelay)
                .setDuration(250)
                .setInterpolator(new AccelerateDecelerateInterpolator())
                .start();

        return convertView;
    }

    private static class ViewHolder {
        final TextView position;
        final TextView name;
        final TextView moves;

        ViewHolder(View view) {
            position = view.findViewById(R.id.position);
            name = view.findViewById(R.id.name);
            moves = view.findViewById(R.id.moves);
        }
    }
}
