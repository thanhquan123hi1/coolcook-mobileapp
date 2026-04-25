package com.coolcook.app.feature.camera.ui.adapter;

import android.content.res.ColorStateList;
import android.graphics.Color;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.text.TextUtils;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.core.content.res.ResourcesCompat;
import androidx.recyclerview.widget.RecyclerView;

import com.coolcook.app.R;
import com.coolcook.app.core.util.MarkdownRenderer;
import com.coolcook.app.feature.camera.model.ScanDishItem;
import com.google.android.material.card.MaterialCardView;
import com.google.android.material.chip.Chip;
import com.google.android.material.chip.ChipGroup;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

public class ScanDishSuggestionAdapter extends RecyclerView.Adapter<ScanDishSuggestionAdapter.ScanDishViewHolder> {

    public interface DishActionListener {
        void onDishClicked(@NonNull ScanDishItem item);

        void onSaveDishClicked(@NonNull ScanDishItem item);

        void onAddToJournalClicked(@NonNull ScanDishItem item);
    }

    @NonNull
    private final List<ScanDishItem> items = new ArrayList<>();
    @Nullable
    private final DishActionListener actionListener;
    private final boolean showSaveButton;
    @NonNull
    private String selectedDishId = "";

    public ScanDishSuggestionAdapter(@Nullable DishActionListener actionListener, boolean showSaveButton) {
        this.actionListener = actionListener;
        this.showSaveButton = showSaveButton;
    }

    @NonNull
    @Override
    public ScanDishViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View view = LayoutInflater.from(parent.getContext()).inflate(R.layout.item_scan_dish_card, parent, false);
        return new ScanDishViewHolder(view);
    }

    @Override
    public void onBindViewHolder(@NonNull ScanDishViewHolder holder, int position) {
        ScanDishItem item = items.get(position);

        holder.imgDish.setImageResource(item.resolveImageResId(holder.itemView.getContext()));
        holder.txtBadge.setText(item.getBadgeLabel());
        holder.txtName.setText(item.getName());
        holder.txtConfidence.setText(String.format(
                Locale.US,
                "Độ phù hợp %.0f%%",
                Math.max(0d, Math.min(1d, item.getConfidence())) * 100d));
        MarkdownRenderer.render(holder.txtReason, item.getReason());
        MarkdownRenderer.render(holder.txtRecipePreview, item.getRecipePreview());
        holder.txtUsedIngredients.setText(joinOrFallback(item.getUsedIngredients(), "Đang cập nhật"));

        if (item.getMissingIngredients().isEmpty()) {
            holder.rowMissing.setVisibility(View.GONE);
        } else {
            holder.rowMissing.setVisibility(View.VISIBLE);
            holder.txtMissingIngredients.setText(joinOrFallback(item.getMissingIngredients(), ""));
        }

        bindHealthTags(holder.groupHealthTags, item.getHealthTags());
        bindBadge(holder.txtBadge, item.isLocal());
        holder.btnSaveDish.setVisibility(showSaveButton ? View.VISIBLE : View.GONE);
        holder.btnJournalDish.setVisibility(showSaveButton ? View.VISIBLE : View.GONE);
        holder.btnSaveDish.setOnClickListener(v -> {
            if (actionListener != null) {
                actionListener.onSaveDishClicked(item);
            }
        });
        holder.btnJournalDish.setOnClickListener(v -> {
            if (actionListener != null) {
                actionListener.onAddToJournalClicked(item);
            }
        });
        holder.itemView.setOnClickListener(v -> {
            if (actionListener != null) {
                actionListener.onDishClicked(item);
            }
        });
        boolean selected = item.getStableId().equals(selectedDishId);
        holder.cardRoot.setStrokeWidth((int) dp(holder.itemView, selected ? 2 : 1));
        holder.cardRoot.setStrokeColor(Color.parseColor(selected ? "#E39A22" : "#E6D9CA"));
        holder.cardRoot.setCardBackgroundColor(Color.parseColor(selected ? "#FFF6E7" : "#FFFCF8"));
    }

    @Override
    public int getItemCount() {
        return items.size();
    }

    public void submitItems(@NonNull List<ScanDishItem> nextItems) {
        items.clear();
        items.addAll(nextItems);
        notifyDataSetChanged();
    }

    public void setSelectedDishId(@Nullable String selectedDishId) {
        this.selectedDishId = selectedDishId == null ? "" : selectedDishId;
        notifyDataSetChanged();
    }

    private void bindBadge(@NonNull TextView view, boolean local) {
        GradientDrawable drawable = new GradientDrawable();
        drawable.setCornerRadius(dp(view, 999));
        drawable.setColor(Color.parseColor(local ? "#DFF3E7" : "#FBE7D6"));
        view.setBackground(drawable);
        view.setTextColor(Color.parseColor(local ? "#235C43" : "#9A4F16"));
    }

    private void bindHealthTags(@NonNull ChipGroup chipGroup, @NonNull List<String> tags) {
        chipGroup.removeAllViews();
        Typeface typeface = ResourcesCompat.getFont(chipGroup.getContext(), R.font.be_vietnam_pro_medium);
        List<String> trimmed = new ArrayList<>();
        for (String tag : tags) {
            String value = tag == null ? "" : tag.trim();
            if (!value.isEmpty() && !trimmed.contains(value)) {
                trimmed.add(value);
            }
        }

        for (String tag : trimmed) {
            Chip chip = new Chip(chipGroup.getContext());
            chip.setText(tag);
            chip.setCheckable(false);
            chip.setClickable(false);
            chip.setTextColor(Color.parseColor("#4D3B2E"));
            chip.setChipBackgroundColor(ColorStateList.valueOf(Color.parseColor("#F7EEE5")));
            chip.setChipStrokeWidth(dp(chipGroup, 1));
            chip.setChipStrokeColor(ColorStateList.valueOf(Color.parseColor("#E4D6C6")));
            if (typeface != null) {
                chip.setTypeface(typeface);
            }
            chipGroup.addView(chip);
        }
    }

    @NonNull
    private static String joinOrFallback(@NonNull List<String> values, @NonNull String fallback) {
        List<String> filtered = new ArrayList<>();
        for (String value : values) {
            if (!TextUtils.isEmpty(value) && !filtered.contains(value.trim())) {
                filtered.add(value.trim());
            }
        }
        if (filtered.isEmpty()) {
            return fallback;
        }
        return TextUtils.join(", ", filtered);
    }

    private static float dp(@NonNull View view, float value) {
        return value * view.getResources().getDisplayMetrics().density;
    }

    static class ScanDishViewHolder extends RecyclerView.ViewHolder {
        final MaterialCardView cardRoot;
        final ImageView imgDish;
        final TextView txtBadge;
        final TextView txtName;
        final TextView txtConfidence;
        final TextView txtReason;
        final TextView txtUsedIngredients;
        final View rowMissing;
        final TextView txtMissingIngredients;
        final ChipGroup groupHealthTags;
        final TextView txtRecipePreview;
        final TextView btnSaveDish;
        final TextView btnJournalDish;

        ScanDishViewHolder(@NonNull View itemView) {
            super(itemView);
            cardRoot = itemView.findViewById(R.id.cardDishSuggestion);
            imgDish = itemView.findViewById(R.id.imgDishSuggestion);
            txtBadge = itemView.findViewById(R.id.txtDishBadge);
            txtName = itemView.findViewById(R.id.txtDishName);
            txtConfidence = itemView.findViewById(R.id.txtDishConfidence);
            txtReason = itemView.findViewById(R.id.txtDishReason);
            txtUsedIngredients = itemView.findViewById(R.id.txtDishUsedIngredients);
            rowMissing = itemView.findViewById(R.id.rowDishMissingIngredients);
            txtMissingIngredients = itemView.findViewById(R.id.txtDishMissingIngredients);
            groupHealthTags = itemView.findViewById(R.id.groupDishHealthTags);
            txtRecipePreview = itemView.findViewById(R.id.txtDishRecipePreview);
            btnSaveDish = itemView.findViewById(R.id.btnDishSave);
            btnJournalDish = itemView.findViewById(R.id.btnDishJournal);
        }
    }
}
