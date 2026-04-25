package com.coolcook.app.feature.social.ui.adapter;

import android.text.TextUtils;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.camera.view.PreviewView;
import androidx.recyclerview.widget.RecyclerView;

import com.bumptech.glide.Glide;
import com.coolcook.app.R;
import com.coolcook.app.feature.social.model.JournalFeedItem;

import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.Locale;

public class JournalLocketPagerAdapter extends RecyclerView.Adapter<RecyclerView.ViewHolder> {

    public interface Listener {
        void onHistoryPageVisible(@Nullable JournalFeedItem item);

        void onCameraPageBound(
                @NonNull PreviewView previewView,
                @NonNull View previewInnerFrame,
                @NonNull View flashButton,
                @NonNull TextView flashIcon,
                @NonNull View galleryButton,
                @NonNull View shutterButton,
                @NonNull View flipButton);
    }

    private static final int VIEW_TYPE_CAMERA = 0;
    private static final int VIEW_TYPE_HISTORY = 1;

    private final List<JournalFeedItem> items = new ArrayList<>();
    private final SimpleDateFormat fallbackFormat = new SimpleDateFormat("dd/MM", Locale.forLanguageTag("vi-VN"));
    private final Listener listener;

    public JournalLocketPagerAdapter(@NonNull Listener listener) {
        this.listener = listener;
    }

    @Override
    public int getItemViewType(int position) {
        return position == 0 ? VIEW_TYPE_CAMERA : VIEW_TYPE_HISTORY;
    }

    @NonNull
    @Override
    public RecyclerView.ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        LayoutInflater inflater = LayoutInflater.from(parent.getContext());
        if (viewType == VIEW_TYPE_CAMERA) {
            View view = inflater.inflate(R.layout.item_journal_locket_camera_page, parent, false);
            return new CameraPageViewHolder(view);
        }
        View view = inflater.inflate(R.layout.item_journal_history_locket, parent, false);
        return new HistoryViewHolder(view);
    }

    @Override
    public void onBindViewHolder(@NonNull RecyclerView.ViewHolder holder, int position) {
        if (holder instanceof CameraPageViewHolder) {
            CameraPageViewHolder cameraHolder = (CameraPageViewHolder) holder;
            listener.onCameraPageBound(
                    cameraHolder.previewView,
                    cameraHolder.previewInnerFrame,
                    cameraHolder.flashButton,
                    cameraHolder.flashIcon,
                    cameraHolder.galleryButton,
                    cameraHolder.shutterButton,
                    cameraHolder.flipButton);
            listener.onHistoryPageVisible(null);
            return;
        }

        HistoryViewHolder historyHolder = (HistoryViewHolder) holder;
        JournalFeedItem item = getHistoryItemForPage(position);
        if (item == null) {
            return;
        }

        Glide.with(historyHolder.itemView)
                .load(item.getBestImageUrl())
                .placeholder(R.drawable.bg_journal_history_card)
                .error(R.drawable.bg_journal_history_card)
                .centerCrop()
                .into(historyHolder.image);

        Glide.with(historyHolder.itemView)
                .load(item.getOwnerAvatarUrl())
                .placeholder(R.drawable.img_home_profile)
                .error(R.drawable.img_home_profile)
                .circleCrop()
                .into(historyHolder.avatar);

        historyHolder.ownerName.setText(item.isOwnMoment() ? "Bạn" : item.getOwnerName());
        historyHolder.time.setText(formatRelativeTime(item.getCreatedAt()));
        historyHolder.separator.setVisibility(View.VISIBLE);
        if (TextUtils.isEmpty(item.getCaption())) {
            historyHolder.caption.setVisibility(View.GONE);
        } else {
            historyHolder.caption.setVisibility(View.VISIBLE);
            historyHolder.caption.setText(item.getCaption());
        }
        listener.onHistoryPageVisible(item);
    }

    @Override
    public int getItemCount() {
        return 1 + items.size();
    }

    public void submitItems(@NonNull List<JournalFeedItem> newItems) {
        items.clear();
        items.addAll(newItems);
        notifyDataSetChanged();
    }

    public int getHistoryCount() {
        return items.size();
    }

    public boolean isCameraPage(int position) {
        return position == 0;
    }

    @Nullable
    public JournalFeedItem getHistoryItemForPage(int position) {
        int index = position - 1;
        if (index < 0 || index >= items.size()) {
            return null;
        }
        return items.get(index);
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

    static class CameraPageViewHolder extends RecyclerView.ViewHolder {
        final PreviewView previewView;
        final View previewInnerFrame;
        final View flashButton;
        final TextView flashIcon;
        final View galleryButton;
        final View shutterButton;
        final View flipButton;

        CameraPageViewHolder(@NonNull View itemView) {
            super(itemView);
            previewView = itemView.findViewById(R.id.journalCameraPreviewView);
            previewInnerFrame = itemView.findViewById(R.id.journalCameraPreviewInnerFrame);
            flashButton = itemView.findViewById(R.id.btnJournalCameraFlash);
            flashIcon = itemView.findViewById(R.id.iconJournalCameraFlash);
            galleryButton = itemView.findViewById(R.id.btnJournalCameraGallery);
            shutterButton = itemView.findViewById(R.id.btnJournalCameraShutter);
            flipButton = itemView.findViewById(R.id.btnJournalCameraFlip);
        }
    }

    static class HistoryViewHolder extends RecyclerView.ViewHolder {
        final ImageView image;
        final ImageView avatar;
        final TextView caption;
        final TextView ownerName;
        final TextView separator;
        final TextView time;
        HistoryViewHolder(@NonNull View itemView) {
            super(itemView);
            image = itemView.findViewById(R.id.journalHistoryImage);
            avatar = itemView.findViewById(R.id.journalHistoryAvatar);
            caption = itemView.findViewById(R.id.journalHistoryCaption);
            ownerName = itemView.findViewById(R.id.journalHistoryOwnerName);
            separator = itemView.findViewById(R.id.journalHistoryMetaSeparator);
            time = itemView.findViewById(R.id.journalHistoryTime);
        }
    }
}
