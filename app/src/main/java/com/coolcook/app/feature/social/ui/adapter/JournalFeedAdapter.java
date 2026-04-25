package com.coolcook.app.feature.social.ui.adapter;

import android.text.TextUtils;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

import com.bumptech.glide.Glide;
import com.coolcook.app.R;
import com.coolcook.app.core.util.MarkdownRenderer;
import com.coolcook.app.feature.social.model.JournalFeedItem;

import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.Locale;

public class JournalFeedAdapter extends RecyclerView.Adapter<JournalFeedAdapter.FeedViewHolder> {

    private final List<JournalFeedItem> items = new ArrayList<>();
    private final SimpleDateFormat fallbackFormat = new SimpleDateFormat("dd/MM", Locale.forLanguageTag("vi-VN"));

    @NonNull
    @Override
    public FeedViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View view = LayoutInflater.from(parent.getContext())
                .inflate(R.layout.item_journal_feed_moment, parent, false);
        return new FeedViewHolder(view);
    }

    @Override
    public void onBindViewHolder(@NonNull FeedViewHolder holder, int position) {
        JournalFeedItem item = items.get(position);

        Glide.with(holder.itemView)
                .load(item.getBestImageUrl())
                .placeholder(R.drawable.img_scan_food_salad)
                .error(R.drawable.img_scan_food_salad)
                .into(holder.imgMoment);

        Glide.with(holder.itemView)
                .load(item.getOwnerAvatarUrl())
                .placeholder(R.drawable.img_home_profile)
                .error(R.drawable.img_home_profile)
                .circleCrop()
                .into(holder.imgAvatar);

        holder.txtOwner.setText(item.isOwnMoment() ? "Bạn" : item.getOwnerName());
        holder.txtMeta.setText(relativeTime(item.getCreatedAt()));

        if (TextUtils.isEmpty(item.getCaption())) {
            holder.txtCaption.setVisibility(View.GONE);
        } else {
            holder.txtCaption.setVisibility(View.VISIBLE);
            MarkdownRenderer.render(holder.txtCaption, item.getCaption());
        }
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

    public void prependIfMissing(@NonNull JournalFeedItem item) {
        for (JournalFeedItem existing : items) {
            if (existing.getMomentId().equals(item.getMomentId())) {
                return;
            }
        }
        items.add(0, item);
        notifyItemInserted(0);
    }

    private String relativeTime(Date createdAt) {
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

    static class FeedViewHolder extends RecyclerView.ViewHolder {
        final ImageView imgMoment;
        final ImageView imgAvatar;
        final TextView txtCaption;
        final TextView txtOwner;
        final TextView txtMeta;

        FeedViewHolder(@NonNull View itemView) {
            super(itemView);
            imgMoment = itemView.findViewById(R.id.imgFeedMoment);
            imgAvatar = itemView.findViewById(R.id.imgFeedAvatar);
            txtCaption = itemView.findViewById(R.id.txtFeedCaption);
            txtOwner = itemView.findViewById(R.id.txtFeedOwner);
            txtMeta = itemView.findViewById(R.id.txtFeedMeta);
        }
    }
}
