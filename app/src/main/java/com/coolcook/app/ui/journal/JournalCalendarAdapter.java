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

    private static final int[] STACK_HEIGHT_DP = {58, 64, 52, 68, 60, 66, 56};
    private static final float[] PRIMARY_ROTATION = {-7f, -4f, -6f, -3f, -8f, -5f, -2f};
    private static final float[] SECONDARY_ROTATION = {6f, 8f, 5f, 9f, 7f, 4f, 6f};
    private static final int[] SECONDARY_TRANSLATE_Y_DP = {6, 2, 7, 4, 8, 3, 5};
    private static final int[] SECONDARY_TRANSLATE_X_DP = {0, 2, -1, 1, -2, 2, 0};

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
        int pattern = normalizePattern(day.getVisualPatternIndex());
        applyVisualPattern(holder, pattern);

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

    private void applyVisualPattern(@NonNull DayViewHolder holder, int pattern) {
        ViewGroup.LayoutParams stackParams = holder.photoStack.getLayoutParams();
        stackParams.height = dpToPx(holder.itemView, STACK_HEIGHT_DP[pattern]);
        holder.photoStack.setLayoutParams(stackParams);

        holder.cardPrimary.setRotation(PRIMARY_ROTATION[pattern]);
        holder.cardSecondary.setRotation(SECONDARY_ROTATION[pattern]);
        holder.cardSecondary.setTranslationY(dpToPx(holder.itemView, SECONDARY_TRANSLATE_Y_DP[pattern]));
        holder.cardSecondary.setTranslationX(dpToPx(holder.itemView, SECONDARY_TRANSLATE_X_DP[pattern]));
    }

    private int normalizePattern(int index) {
        if (index < 0) {
            return 0;
        }
        return index % STACK_HEIGHT_DP.length;
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
