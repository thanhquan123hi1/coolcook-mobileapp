package com.coolcook.app.feature.social.ui.adapter;

import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

import com.bumptech.glide.Glide;
import com.coolcook.app.R;
import com.coolcook.app.feature.social.model.JournalFeedItem;

import java.util.ArrayList;
import java.util.List;

public class JournalHistoryGridAdapter extends RecyclerView.Adapter<JournalHistoryGridAdapter.GridViewHolder> {

    private final List<JournalFeedItem> items = new ArrayList<>();

    @NonNull
    @Override
    public GridViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View view = LayoutInflater.from(parent.getContext())
                .inflate(R.layout.item_journal_history_grid, parent, false);
        return new GridViewHolder(view);
    }

    @Override
    public void onBindViewHolder(@NonNull GridViewHolder holder, int position) {
        JournalFeedItem item = items.get(position);
        Glide.with(holder.itemView)
                .load(item.getBestImageUrl())
                .placeholder(R.drawable.bg_journal_history_grid_cell)
                .error(R.drawable.bg_journal_history_grid_cell)
                .centerCrop()
                .into(holder.image);
    }

    @Override
    public int getItemCount() {
        return items.size();
    }

    public void submitItems(@NonNull List<JournalFeedItem> newItems) {
        items.clear();
        items.addAll(newItems);
        notifyDataSetChanged();
    }

    static class GridViewHolder extends RecyclerView.ViewHolder {
        final ImageView image;

        GridViewHolder(@NonNull View itemView) {
            super(itemView);
            image = itemView.findViewById(R.id.imgJournalGridMoment);
        }
    }
}
