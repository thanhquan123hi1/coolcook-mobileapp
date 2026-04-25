package com.coolcook.app.feature.social.ui.adapter;

import android.text.TextUtils;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.recyclerview.widget.RecyclerView;

import com.bumptech.glide.Glide;
import com.coolcook.app.R;
import com.coolcook.app.feature.social.model.JournalFeedItem;

import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.Locale;

public class JournalHistoryAdapter extends RecyclerView.Adapter<JournalHistoryAdapter.HistoryViewHolder> {

    public interface Listener {
        void onItemVisible(@NonNull JournalFeedItem item);
    }

    private final List<JournalFeedItem> items = new ArrayList<>();
    private final SimpleDateFormat fallbackFormat = new SimpleDateFormat("dd/MM", Locale.forLanguageTag("vi-VN"));
    private final Listener listener;

    public JournalHistoryAdapter(@NonNull Listener listener) {
        this.listener = listener;
    }

    @NonNull
    @Override
    public HistoryViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View view = LayoutInflater.from(parent.getContext())
                .inflate(R.layout.item_journal_history_locket, parent, false);
        return new HistoryViewHolder(view);
    }

    @Override
    public void onBindViewHolder(@NonNull HistoryViewHolder holder, int position) {
        JournalFeedItem item = items.get(position);
        Glide.with(holder.itemView)
                .load(item.getBestImageUrl())
                .placeholder(R.drawable.bg_journal_history_card)
                .error(R.drawable.bg_journal_history_card)
                .centerCrop()
                .into(holder.image);

        Glide.with(holder.itemView)
                .load(item.getOwnerAvatarUrl())
                .placeholder(R.drawable.img_home_profile)
                .error(R.drawable.img_home_profile)
                .circleCrop()
                .into(holder.avatar);

        holder.ownerName.setText(item.isOwnMoment() ? "Bạn" : item.getOwnerName());
        holder.time.setText(formatRelativeTime(item.getCreatedAt()));
        if (TextUtils.isEmpty(item.getCaption())) {
            holder.caption.setVisibility(View.GONE);
        } else {
            holder.caption.setVisibility(View.VISIBLE);
            holder.caption.setText(item.getCaption());
        }
        listener.onItemVisible(item);
    }

    @Override
    public int getItemCount() {
        return items.size();
    }

    @NonNull
    public List<JournalFeedItem> getItems() {
        return new ArrayList<>(items);
    }

    @NonNull
    public JournalFeedItem getItem(int position) {
        return items.get(position);
    }

    public void submitItems(@NonNull List<JournalFeedItem> newItems) {
        items.clear();
        items.addAll(newItems);
        notifyDataSetChanged();
    }

    @NonNull
    public String formatRelativeTime(@Nullable Date createdAt) {
        if (createdAt == null) {
            return "Vừa xong";
        }
        long diffMillis = Math.max(0L, System.currentTimeMillis() - createdAt.getTime());
        long minutes = diffMillis / 60_000L;
        if (minutes < 1L) {
            return "Vừa xong";
        }
        if (minutes < 60L) {
            return minutes + "m";
        }
        long hours = minutes / 60L;
        if (hours < 24L) {
            return hours + "h";
        }
        return fallbackFormat.format(createdAt);
    }

    static class HistoryViewHolder extends RecyclerView.ViewHolder {
        final ImageView image;
        final ImageView avatar;
        final TextView caption;
        final TextView ownerName;
        final TextView time;

        HistoryViewHolder(@NonNull View itemView) {
            super(itemView);
            image = itemView.findViewById(R.id.journalHistoryImage);
            avatar = itemView.findViewById(R.id.journalHistoryAvatar);
            caption = itemView.findViewById(R.id.journalHistoryCaption);
            ownerName = itemView.findViewById(R.id.journalHistoryOwnerName);
            time = itemView.findViewById(R.id.journalHistoryTime);
        }
    }
}
