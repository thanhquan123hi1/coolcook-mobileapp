package com.coolcook.app.feature.journal.ui.adapter;

import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

import com.bumptech.glide.Glide;
import com.coolcook.app.R;
import com.coolcook.app.feature.journal.model.JournalEntry;

import java.util.ArrayList;
import java.util.List;

public class JournalDayDetailAdapter extends RecyclerView.Adapter<JournalDayDetailAdapter.PhotoViewHolder> {

    public interface OnPhotoClickListener {
        void onPhotoClick(@NonNull JournalEntry entry);
    }

    public interface OnPhotoLongClickListener {
        void onPhotoLongClick(@NonNull JournalEntry entry);
    }

    @NonNull
    private final List<JournalEntry> entries = new ArrayList<>();
    @NonNull
    private final OnPhotoClickListener onPhotoClickListener;
    @NonNull
    private final OnPhotoLongClickListener onPhotoLongClickListener;

    public JournalDayDetailAdapter(
            @NonNull OnPhotoClickListener onPhotoClickListener,
            @NonNull OnPhotoLongClickListener onPhotoLongClickListener) {
        this.onPhotoClickListener = onPhotoClickListener;
        this.onPhotoLongClickListener = onPhotoLongClickListener;
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
        holder.badge.setText(entry.isFoodEntry() ? "Món ăn" : "Ảnh");
        holder.placeholder.setVisibility(entry.hasPreviewImage() ? View.GONE : View.VISIBLE);

        if (entry.hasPreviewImage()) {
            holder.photo.setScaleType(ImageView.ScaleType.CENTER_CROP);
            holder.photo.setBackground(null);
            Glide.with(holder.itemView)
                    .load(entry.getPreviewUrl())
                    .placeholder(R.drawable.bg_journal_placeholder_cute)
                    .error(R.drawable.bg_journal_placeholder_cute_alt)
                    .into(holder.photo);
        } else {
            holder.photo.setScaleType(ImageView.ScaleType.CENTER_INSIDE);
            holder.photo.setBackgroundResource(R.drawable.bg_journal_placeholder_cute);
            holder.photo.setImageResource(R.drawable.ic_journal_diary_soft);
        }

        holder.itemView.setOnClickListener(v -> onPhotoClickListener.onPhotoClick(entry));
        holder.itemView.setOnLongClickListener(v -> {
            onPhotoLongClickListener.onPhotoLongClick(entry);
            return true;
        });
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
        final TextView placeholder;
        final TextView badge;

        PhotoViewHolder(@NonNull View itemView) {
            super(itemView);
            photo = itemView.findViewById(R.id.imgJournalDayPhoto);
            placeholder = itemView.findViewById(R.id.txtJournalDayPlaceholder);
            badge = itemView.findViewById(R.id.txtJournalDayEntryBadge);
        }
    }
}
