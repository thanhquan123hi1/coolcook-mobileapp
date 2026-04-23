package com.coolcook.app.ui.journal;

import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.core.content.ContextCompat;
import androidx.recyclerview.widget.RecyclerView;

import com.bumptech.glide.Glide;
import com.coolcook.app.R;
import com.coolcook.app.ui.journal.model.JournalDay;
import com.coolcook.app.ui.journal.model.JournalEntry;
import com.google.android.material.card.MaterialCardView;

import java.util.ArrayList;
import java.util.List;

public class JournalCalendarAdapter extends RecyclerView.Adapter<JournalCalendarAdapter.DayViewHolder> {

    public interface OnDayClickListener {
        void onDayClick(@NonNull JournalDay day);
    }

    private static final int DAYS_PER_WEEK = 7;

    private static final int[] COLUMN_STACK_BIAS_DP = {1, 0, 1, 0, 1, 0, 1};
    private static final int[] ROW_STACK_BIAS_DP = {0, -1, 1, 0, -1, 1};

    private static final float[] COLUMN_PRIMARY_ROTATION = {-8.5f, -5.5f, -7f, -4.5f, -6.5f, -5f, -3.5f};
    private static final float[] COLUMN_SECONDARY_ROTATION = {8f, 6.5f, 9f, 6f, 8.5f, 6.5f, 7.5f};

    private static final int[] COLUMN_PRIMARY_TRANSLATE_X_DP = {-2, -1, -1, 0, 0, 1, 1};
    private static final int[] COLUMN_PRIMARY_TRANSLATE_Y_DP = {0, 1, 0, 1, 0, 1, 0};
    private static final int[] COLUMN_SECONDARY_TRANSLATE_X_DP = {1, 2, 1, 2, 0, 1, 0};
    private static final int[] COLUMN_SECONDARY_TRANSLATE_Y_DP = {4, 5, 4, 5, 4, 4, 3};

    private static final float[] DAY_PRIMARY_ROTATION_DELTA = {0f, 0.8f, -0.6f, 0.5f, -0.4f, 0.7f};
    private static final float[] DAY_SECONDARY_ROTATION_DELTA = {0f, -0.4f, 0.6f, -0.2f, 0.4f, -0.5f};

    private static final int[] DAY_PRIMARY_TRANSLATE_Y_DELTA_DP = {0, -1, 1, 0, -1, 1};
    private static final int[] DAY_SECONDARY_TRANSLATE_Y_DELTA_DP = {0, 1, 0, 1, 0, -1};

    private static final float[] DAY_PRIMARY_SCALE_X = {1.16f, 1.12f, 1.18f, 1.13f, 1.15f, 1.17f};
    private static final float[] DAY_PRIMARY_SCALE_Y = {0.94f, 0.92f, 0.95f, 0.93f, 0.94f, 0.92f};
    private static final float[] DAY_SECONDARY_SCALE_X = {1.08f, 1.06f, 1.10f, 1.07f, 1.09f, 1.06f};
    private static final float[] DAY_SECONDARY_SCALE_Y = {0.90f, 0.88f, 0.91f, 0.89f, 0.90f, 0.88f};

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
        applyVisualPattern(holder, day, position);

        if (!day.isInCurrentMonth()) {
            bindEmptyCell(holder);
            return;
        }

        holder.root.setVisibility(View.VISIBLE);
        holder.root.setClickable(true);
        holder.root.setAlpha(1f);

        holder.txtDayNumber.setText(String.valueOf(day.getDayOfMonth()));
        int dayTextColor = day.getTotalEntryCount() > 0
                ? ContextCompat.getColor(holder.itemView.getContext(), R.color.on_surface)
                : ContextCompat.getColor(holder.itemView.getContext(), R.color.on_surface_variant);
        holder.txtDayNumber.setTextColor(dayTextColor);

        bindImageSlots(holder, day.getLatestEntries());

        int extraCount = day.getExtraCount();
        if (extraCount > 0) {
            holder.txtDayExtra.setVisibility(View.VISIBLE);
            holder.txtDayExtra.setText("+" + extraCount);
        } else {
            holder.txtDayExtra.setVisibility(View.GONE);
        }

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

    @NonNull
    public List<JournalDay> getCurrentDays() {
        return new ArrayList<>(days);
    }

    private void bindEmptyCell(@NonNull DayViewHolder holder) {
        holder.root.setVisibility(View.INVISIBLE);
        holder.root.setClickable(false);
        holder.txtDayExtra.setVisibility(View.GONE);
        holder.txtDayNumber.setText("");
        holder.itemView.setOnClickListener(null);
    }

    private void bindImageSlots(@NonNull DayViewHolder holder, @NonNull List<JournalEntry> latestEntries) {
        if (latestEntries.size() >= 2) {
            loadRealImage(holder.imgPrimaryPhoto, latestEntries.get(0).getPreviewUrl(), holder.itemView);
            loadRealImage(holder.imgSecondaryPhoto, latestEntries.get(1).getPreviewUrl(), holder.itemView);
            return;
        }

        if (latestEntries.size() == 1) {
            loadRealImage(holder.imgPrimaryPhoto, latestEntries.get(0).getPreviewUrl(), holder.itemView);
            loadPlaceholder(holder.imgSecondaryPhoto, R.drawable.bg_journal_placeholder_cute_alt, holder.itemView);
            return;
        }

        loadPlaceholder(holder.imgPrimaryPhoto, R.drawable.bg_journal_placeholder_cute, holder.itemView);
        loadPlaceholder(holder.imgSecondaryPhoto, R.drawable.bg_journal_placeholder_cute_alt, holder.itemView);
    }

    private void loadRealImage(@NonNull ImageView target, @NonNull String imageUrl, @NonNull View ownerView) {
        Glide.with(ownerView)
                .load(imageUrl)
                .placeholder(R.drawable.bg_journal_placeholder_cute)
                .error(R.drawable.bg_journal_placeholder_cute_alt)
                .into(target);
    }

    private void loadPlaceholder(@NonNull ImageView target, int drawableRes, @NonNull View ownerView) {
        Glide.with(ownerView).clear(target);
        target.setImageResource(drawableRes);
    }

    private void applyVisualPattern(@NonNull DayViewHolder holder, @NonNull JournalDay day, int adapterPosition) {
        int column = Math.floorMod(adapterPosition, DAYS_PER_WEEK);
        int row = Math.max(0, adapterPosition / DAYS_PER_WEEK);
        int dayVariant = resolveDayVariant(day, adapterPosition);

        int baseStackHeightPx = holder.itemView.getResources().getDimensionPixelSize(R.dimen.journal_day_stack_height_default);
        int stackHeightPx = baseStackHeightPx
                + dpToPx(holder.itemView, COLUMN_STACK_BIAS_DP[column] + ROW_STACK_BIAS_DP[row % ROW_STACK_BIAS_DP.length]);

        ViewGroup.LayoutParams stackParams = holder.photoStack.getLayoutParams();
        stackParams.height = stackHeightPx;
        holder.photoStack.setLayoutParams(stackParams);

        holder.cardPrimary.setRotation(COLUMN_PRIMARY_ROTATION[column] + DAY_PRIMARY_ROTATION_DELTA[dayVariant]);
        holder.cardPrimary.setTranslationX(dpToPx(holder.itemView, COLUMN_PRIMARY_TRANSLATE_X_DP[column]));
        holder.cardPrimary.setTranslationY(dpToPx(holder.itemView,
                COLUMN_PRIMARY_TRANSLATE_Y_DP[column] + DAY_PRIMARY_TRANSLATE_Y_DELTA_DP[dayVariant]));
        holder.cardPrimary.setScaleX(DAY_PRIMARY_SCALE_X[dayVariant]);
        holder.cardPrimary.setScaleY(DAY_PRIMARY_SCALE_Y[dayVariant]);

        holder.cardSecondary.setRotation(COLUMN_SECONDARY_ROTATION[column] + DAY_SECONDARY_ROTATION_DELTA[dayVariant]);
        holder.cardSecondary.setTranslationX(dpToPx(holder.itemView, COLUMN_SECONDARY_TRANSLATE_X_DP[column]));
        holder.cardSecondary.setTranslationY(dpToPx(holder.itemView,
                COLUMN_SECONDARY_TRANSLATE_Y_DP[column] + DAY_SECONDARY_TRANSLATE_Y_DELTA_DP[dayVariant]));
        holder.cardSecondary.setScaleX(DAY_SECONDARY_SCALE_X[dayVariant]);
        holder.cardSecondary.setScaleY(DAY_SECONDARY_SCALE_Y[dayVariant]);
    }

    private int resolveDayVariant(@NonNull JournalDay day, int adapterPosition) {
        int seed = day.isInCurrentMonth() ? day.getDayOfMonth() : adapterPosition + 1;
        seed += day.getVisualPatternIndex() * 2;
        return Math.floorMod(seed, DAY_PRIMARY_SCALE_X.length);
    }

    private static int dpToPx(@NonNull View view, int dp) {
        float density = view.getResources().getDisplayMetrics().density;
        return Math.round(dp * density);
    }

    static class DayViewHolder extends RecyclerView.ViewHolder {
        final View root;
        final View photoStack;
        final MaterialCardView cardPrimary;
        final MaterialCardView cardSecondary;
        final ImageView imgPrimaryPhoto;
        final ImageView imgSecondaryPhoto;
        final TextView txtDayNumber;
        final TextView txtDayExtra;

        DayViewHolder(@NonNull View itemView) {
            super(itemView);
            root = itemView.findViewById(R.id.journalDayRoot);
            photoStack = itemView.findViewById(R.id.journalDayPhotoStack);
            cardPrimary = itemView.findViewById(R.id.cardJournalPrimaryPhoto);
            cardSecondary = itemView.findViewById(R.id.cardJournalSecondaryPhoto);
            imgPrimaryPhoto = itemView.findViewById(R.id.imgJournalPrimaryPhoto);
            imgSecondaryPhoto = itemView.findViewById(R.id.imgJournalSecondaryPhoto);
            txtDayNumber = itemView.findViewById(R.id.txtJournalDayNumber);
            txtDayExtra = itemView.findViewById(R.id.txtJournalDayExtra);
        }
    }
}
