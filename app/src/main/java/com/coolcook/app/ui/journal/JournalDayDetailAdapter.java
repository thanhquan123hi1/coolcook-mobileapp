package com.coolcook.app.ui.journal;

import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

import com.bumptech.glide.Glide;
import com.coolcook.app.R;
import com.coolcook.app.ui.journal.model.JournalEntry;

import java.util.ArrayList;
import java.util.List;

public class JournalDayDetailAdapter extends RecyclerView.Adapter<JournalDayDetailAdapter.PhotoViewHolder> {

    public interface OnPhotoClickListener {
        void onPhotoClick(@NonNull JournalEntry entry);
    }

    @NonNull
    private final List<JournalEntry> entries = new ArrayList<>();
    @NonNull
    private final OnPhotoClickListener onPhotoClickListener;

    public JournalDayDetailAdapter(@NonNull OnPhotoClickListener onPhotoClickListener) {
        this.onPhotoClickListener = onPhotoClickListener;
    }

    @NonNull
    @Override
    public PhotoViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View view = LayoutInflater.from(parent.getContext())
                .inflate(R.layout.item_journal_day_detail_photo, parent, false);
        return new PhotoViewHolder(view);
    }

    @Override
    public void onBindViewHolder(@NonNull PhotoViewHolder holder, int position) {
        JournalEntry entry = entries.get(position);
        Glide.with(holder.itemView)
                .load(entry.getPreviewUrl())
                .placeholder(R.drawable.bg_journal_placeholder_cute)
                .error(R.drawable.bg_journal_placeholder_cute_alt)
                .into(holder.photo);

        holder.itemView.setOnClickListener(v -> onPhotoClickListener.onPhotoClick(entry));
    }

    @Override
    public int getItemCount() {
        return entries.size();
    }

    public void submitEntries(@NonNull List<JournalEntry> nextEntries) {
        entries.clear();
        entries.addAll(nextEntries);
        notifyDataSetChanged();
    }

    public static class PhotoViewHolder extends RecyclerView.ViewHolder {
        final ImageView photo;

        PhotoViewHolder(@NonNull View itemView) {
            super(itemView);
            photo = itemView.findViewById(R.id.imgJournalDayPhoto);
        }
    }
}
