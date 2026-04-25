package com.coolcook.app.feature.journal.ui.adapter;

import android.view.Gravity;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.core.content.ContextCompat;
import androidx.recyclerview.widget.RecyclerView;

import com.bumptech.glide.Glide;
import com.coolcook.app.R;
import com.coolcook.app.feature.journal.model.JournalDay;
import com.coolcook.app.feature.journal.model.JournalEntry;
import com.google.android.material.card.MaterialCardView;

import java.util.ArrayList;
import java.util.List;

public class JournalCalendarAdapter extends RecyclerView.Adapter<JournalCalendarAdapter.DayViewHolder> {

    public interface OnDayClickListener {
        void onDayClick(@NonNull JournalDay day);
    }

    private static final int MAX_VISIBLE_PHOTOS = 2;

    @NonNull
    private final List<JournalDay> days = new ArrayList<>();
    @NonNull
    private final OnDayClickListener onDayClickListener;

    public JournalCalendarAdapter(@NonNull OnDayClickListener onDayClickListener) {
        this.onDayClickListener = onDayClickListener;
    }

    @NonNull
    @Override
    public DayViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View view = LayoutInflater.from(parent.getContext()).inflate(R.layout.item_journal_day, parent, false);
        return new DayViewHolder(view);
    }

    @Override
    public void onBindViewHolder(@NonNull DayViewHolder holder, int position) {
        JournalDay day = days.get(position);
        if (!day.isInCurrentMonth()) {
            holder.bindEmpty();
            return;
        }

        holder.bindDay(day, buildRenderModel(day));
        holder.itemView.setOnClickListener(v -> onDayClickListener.onDayClick(day));
    }

    @Override
    public int getItemCount() {
        return days.size();
    }

    public void submitDays(@NonNull List<JournalDay> nextDays) {
        days.clear();
        days.addAll(nextDays);
        notifyDataSetChanged();
    }

    @Nullable
    private RenderModel buildRenderModel(@NonNull JournalDay day) {
        List<JournalEntry> previewEntries = new ArrayList<>();
        for (JournalEntry entry : day.getLatestEntries()) {
            previewEntries.add(entry);
            if (previewEntries.size() == MAX_VISIBLE_PHOTOS) {
                break;
            }
        }

        if (previewEntries.isEmpty()) {
            return null;
        }
        return new RenderModel(previewEntries, day.getTotalEntryCount());
    }

    private static class RenderModel {
        @NonNull
        final List<JournalEntry> entries;
        final int badgeCount;

        RenderModel(@NonNull List<JournalEntry> entries, int badgeCount) {
            this.entries = entries;
            this.badgeCount = badgeCount;
        }
    }

    private static class CardTransform {
        final int cardIndex;
        final int sourceIndex;
        final float rotation;
        final float scale;
        final float translationX;
        final float translationY;

        CardTransform(int cardIndex, int sourceIndex, float rotation, float scale, float translationX, float translationY) {
            this.cardIndex = cardIndex;
            this.sourceIndex = sourceIndex;
            this.rotation = rotation;
            this.scale = scale;
            this.translationX = translationX;
            this.translationY = translationY;
        }
    }

    static class DayViewHolder extends RecyclerView.ViewHolder {
        final View root;
        final View photoSlot;
        final FrameLayout photoCluster;
        final MaterialCardView cardBack;
        final MaterialCardView cardFront;
        final ImageView imgBack;
        final ImageView imgFront;
        final TextView txtBadge;
        final TextView txtDayNumber;
        final List<MaterialCardView> cards;
        final List<ImageView> images;
        final int photoWidthPx;
        final int photoHeightPx;
        final int badgeHeightPx;

        DayViewHolder(@NonNull View itemView) {
            super(itemView);
            root = itemView.findViewById(R.id.journalDayRoot);
            photoSlot = itemView.findViewById(R.id.journalDayPhotoSlot);
            photoCluster = itemView.findViewById(R.id.journalDayPhotoCluster);
            cardBack = itemView.findViewById(R.id.cardJournalPhotoBack);
            cardFront = itemView.findViewById(R.id.cardJournalPhotoFront);
            imgBack = itemView.findViewById(R.id.imgJournalPhotoBack);
            imgFront = itemView.findViewById(R.id.imgJournalPhotoFront);
            txtBadge = itemView.findViewById(R.id.txtJournalDayBadge);
            txtDayNumber = itemView.findViewById(R.id.txtJournalDayNumber);
            cards = List.of(cardBack, cardFront);
            images = List.of(imgBack, imgFront);
            photoWidthPx = itemView.getResources().getDimensionPixelSize(R.dimen.journal_day_photo_width);
            photoHeightPx = itemView.getResources().getDimensionPixelSize(R.dimen.journal_day_photo_height);
            badgeHeightPx = itemView.getResources().getDimensionPixelSize(R.dimen.journal_day_badge_height);
        }

        void bindEmpty() {
            root.setVisibility(View.INVISIBLE);
            root.setAlpha(1f);
            txtDayNumber.setText("");
            txtBadge.setVisibility(View.GONE);
            photoSlot.setVisibility(View.INVISIBLE);
            for (MaterialCardView card : cards) {
                card.setVisibility(View.GONE);
            }
            itemView.setOnClickListener(null);
        }

        void bindDay(@NonNull JournalDay day, @Nullable RenderModel renderModel) {
            root.setVisibility(View.VISIBLE);
            root.setAlpha(1f);
            txtDayNumber.setText(String.valueOf(day.getDayOfMonth()));
            txtDayNumber.setTextColor(ContextCompat.getColor(itemView.getContext(), R.color.on_surface));

            if (renderModel == null || renderModel.entries.isEmpty()) {
                hidePhotos();
                return;
            }

            showPhotos(renderModel);
        }

        private void hidePhotos() {
            photoSlot.setVisibility(View.INVISIBLE);
            txtBadge.setVisibility(View.GONE);
            for (MaterialCardView card : cards) {
                card.setVisibility(View.GONE);
            }
        }

        private void showPhotos(@NonNull RenderModel renderModel) {
            int sourceCount = Math.min(renderModel.entries.size(), MAX_VISIBLE_PHOTOS);
            boolean showBadge = renderModel.badgeCount > MAX_VISIBLE_PHOTOS;
            FrameLayout.LayoutParams clusterParams = (FrameLayout.LayoutParams) photoCluster.getLayoutParams();
            clusterParams.width = photoWidthPx + dpToPx(16);
            clusterParams.height = photoHeightPx + (showBadge ? badgeHeightPx + dpToPx(3) : 0);
            clusterParams.gravity = Gravity.TOP | Gravity.CENTER_HORIZONTAL;
            photoCluster.setLayoutParams(clusterParams);

            for (MaterialCardView card : cards) {
                card.setVisibility(View.GONE);
                card.setRotation(0f);
                card.setScaleX(1f);
                card.setScaleY(1f);
                card.setTranslationX(0f);
                card.setTranslationY(0f);
                card.setCardElevation(0f);
                card.setElevation(0f);
            }

            for (CardTransform transform : buildTransforms(sourceCount)) {
                MaterialCardView card = cards.get(transform.cardIndex);
                ImageView image = images.get(transform.cardIndex);

                FrameLayout.LayoutParams cardParams = (FrameLayout.LayoutParams) card.getLayoutParams();
                cardParams.width = photoWidthPx;
                cardParams.height = photoHeightPx;
                cardParams.gravity = Gravity.TOP | Gravity.CENTER_HORIZONTAL;
                cardParams.topMargin = dpToPx(2);
                card.setLayoutParams(cardParams);

                card.setRotation(transform.rotation);
                card.setScaleX(transform.scale);
                card.setScaleY(transform.scale);
                card.setTranslationX(transform.translationX);
                card.setTranslationY(transform.translationY);
                card.setVisibility(View.VISIBLE);

                JournalEntry entry = renderModel.entries.get(transform.sourceIndex);
                if (entry.hasPreviewImage()) {
                    image.setScaleType(ImageView.ScaleType.CENTER_CROP);
                    image.setBackground(null);
                    Glide.with(itemView)
                            .load(entry.getPreviewUrl())
                            .centerCrop()
                            .placeholder(R.drawable.bg_journal_placeholder_cute)
                            .error(R.drawable.bg_journal_placeholder_cute_alt)
                            .into(image);
                } else {
                    image.setScaleType(ImageView.ScaleType.CENTER_INSIDE);
                    image.setBackgroundResource(R.drawable.bg_journal_placeholder_cute);
                    image.setImageResource(R.drawable.ic_journal_diary_soft);
                }
            }

            if (showBadge) {
                FrameLayout.LayoutParams badgeParams = (FrameLayout.LayoutParams) txtBadge.getLayoutParams();
                badgeParams.gravity = Gravity.TOP | Gravity.CENTER_HORIZONTAL;
                badgeParams.topMargin = photoHeightPx + dpToPx(3);
                txtBadge.setLayoutParams(badgeParams);
                txtBadge.setVisibility(View.VISIBLE);
                txtBadge.setText("+" + renderModel.badgeCount);
            } else {
                txtBadge.setVisibility(View.GONE);
            }
            photoSlot.setVisibility(View.VISIBLE);
        }

        @NonNull
        private List<CardTransform> buildTransforms(int sourceCount) {
            if (sourceCount == 1) {
                return List.of(new CardTransform(1, 0, -7f, 1f, 0f, 0f));
            }
            return List.of(
                    new CardTransform(0, 1, 7f, 0.96f, dpToPx(5), dpToPx(3)),
                    new CardTransform(1, 0, -7f, 1f, 0f, 0f));
        }

        private int dpToPx(int value) {
            return Math.round(value * itemView.getResources().getDisplayMetrics().density);
        }

        private float dpToPx(float value) {
            return value * itemView.getResources().getDisplayMetrics().density;
        }
    }
}
