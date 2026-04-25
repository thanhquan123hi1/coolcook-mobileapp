package com.coolcook.app.feature.camera.ui;

import android.content.res.ColorStateList;
import android.graphics.Color;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.widget.FrameLayout;
import android.view.Gravity;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.LinearLayout;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.content.res.ResourcesCompat;

import com.coolcook.app.R;
import com.coolcook.app.core.util.MarkdownRenderer;
import com.coolcook.app.feature.camera.model.ScanDishItem;
import com.coolcook.app.feature.search.model.ParsedRecipe;
import com.coolcook.app.feature.search.parser.RecipeParser;
import com.google.android.material.bottomsheet.BottomSheetBehavior;
import com.google.android.material.bottomsheet.BottomSheetDialog;
import com.google.android.material.chip.Chip;
import com.google.android.material.chip.ChipGroup;

public final class ScanDishRecipeBottomSheet {

    private ScanDishRecipeBottomSheet() {
    }

    public static void show(@NonNull AppCompatActivity activity, @NonNull ScanDishItem item) {
        BottomSheetDialog dialog = new BottomSheetDialog(activity);
        View root = LayoutInflater.from(activity).inflate(R.layout.bottom_sheet_scan_recipe_detail, null, false);
        dialog.setContentView(root);
        dialog.setCancelable(true);
        dialog.setCanceledOnTouchOutside(true);
        root.findViewById(R.id.btnRecipeSheetClose).setOnClickListener(v -> dialog.dismiss());

        ((TextView) root.findViewById(R.id.txtRecipeSheetTitle)).setText(item.getName());
        TextView badge = root.findViewById(R.id.txtRecipeSheetBadge);
        badge.setText(item.getBadgeLabel());
        bindBadge(badge, item.isLocal());
        MarkdownRenderer.render(root.findViewById(R.id.txtRecipeSheetReason), item.getReason());
        bindHealthTags(root.findViewById(R.id.groupRecipeSheetHealthTags), item);

        ParsedRecipe parsedRecipe = RecipeParser.parse(item.getRecipe());
        TextView servingView = root.findViewById(R.id.txtRecipeSheetServing);
        if (parsedRecipe.getServing().trim().isEmpty()) {
            servingView.setVisibility(View.GONE);
        } else {
            servingView.setVisibility(View.VISIBLE);
            servingView.setText("Khẩu phần: " + parsedRecipe.getServing());
        }

        if (parsedRecipe.isParsed()) {
            bindIngredients(activity, root.findViewById(R.id.listRecipeIngredients), parsedRecipe);
            bindSteps(activity, root.findViewById(R.id.listRecipeSteps), parsedRecipe);
            bindTips(activity, root.findViewById(R.id.listRecipeTips), parsedRecipe);
            root.findViewById(R.id.txtRecipeSheetRaw).setVisibility(View.GONE);
        } else {
            root.findViewById(R.id.sectionRecipeIngredients).setVisibility(View.GONE);
            root.findViewById(R.id.sectionRecipeSteps).setVisibility(View.GONE);
            root.findViewById(R.id.sectionRecipeTips).setVisibility(View.GONE);
            TextView rawView = root.findViewById(R.id.txtRecipeSheetRaw);
            rawView.setVisibility(View.VISIBLE);
            MarkdownRenderer.render(rawView, item.getRecipe().trim().isEmpty()
                    ? "Chưa có công thức chi tiết cho món này."
                    : item.getRecipe());
        }

        BottomSheetBehavior<FrameLayout> behavior = dialog.getBehavior();
        behavior.setSkipCollapsed(true);
        behavior.setState(BottomSheetBehavior.STATE_EXPANDED);
        dialog.show();
    }

    private static void bindBadge(@NonNull TextView view, boolean local) {
        GradientDrawable drawable = new GradientDrawable();
        drawable.setCornerRadius(dp(view, 999));
        drawable.setColor(Color.parseColor(local ? "#DFF3E7" : "#FBE7D6"));
        view.setTextColor(Color.parseColor(local ? "#235C43" : "#9A4F16"));
        view.setBackground(drawable);
    }

    private static void bindHealthTags(@NonNull ChipGroup chipGroup, @NonNull ScanDishItem item) {
        chipGroup.removeAllViews();
        Typeface typeface = ResourcesCompat.getFont(chipGroup.getContext(), R.font.be_vietnam_pro_medium);
        for (String tag : item.getHealthTags()) {
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

    private static void bindIngredients(
            @NonNull AppCompatActivity activity,
            @NonNull LinearLayout container,
            @NonNull ParsedRecipe parsedRecipe) {
        container.removeAllViews();
        for (ParsedRecipe.Ingredient ingredient : parsedRecipe.getIngredients()) {
            LinearLayout row = new LinearLayout(activity);
            row.setOrientation(LinearLayout.HORIZONTAL);
            row.setGravity(Gravity.CENTER_VERTICAL);
            row.setPadding(0, dp(activity, 6), 0, dp(activity, 6));

            TextView name = createText(activity, ingredient.getName(), 14, "#26180F", R.font.be_vietnam_pro_medium);
            row.addView(name, new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f));

            if (!ingredient.getAmount().trim().isEmpty()) {
                TextView amount = createText(activity, ingredient.getAmount(), 12, "#8F5A14", R.font.be_vietnam_pro_medium);
                amount.setPadding(dp(activity, 10), dp(activity, 5), dp(activity, 10), dp(activity, 5));
                amount.setBackgroundResource(R.drawable.bg_food_ingredient_quantity);
                row.addView(amount);
            }
            container.addView(row);
        }
    }

    private static void bindSteps(
            @NonNull AppCompatActivity activity,
            @NonNull LinearLayout container,
            @NonNull ParsedRecipe parsedRecipe) {
        container.removeAllViews();
        for (int index = 0; index < parsedRecipe.getSteps().size(); index++) {
            LinearLayout row = new LinearLayout(activity);
            row.setOrientation(LinearLayout.HORIZONTAL);
            row.setGravity(Gravity.TOP);
            row.setPadding(0, dp(activity, 6), 0, dp(activity, 8));

            TextView number = createText(activity, String.valueOf(index + 1), 13, "#FFFFFF", R.font.be_vietnam_pro_bold);
            number.setGravity(Gravity.CENTER);
            number.setBackgroundResource(R.drawable.bg_food_step_number);
            row.addView(number, new LinearLayout.LayoutParams(dp(activity, 30), dp(activity, 30)));

            TextView step = createText(activity, parsedRecipe.getSteps().get(index), 14, "#5E4A3B", R.font.be_vietnam_pro);
            LinearLayout.LayoutParams stepParams = new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f);
            stepParams.setMarginStart(dp(activity, 12));
            row.addView(step, stepParams);

            container.addView(row);
        }
    }

    private static void bindTips(
            @NonNull AppCompatActivity activity,
            @NonNull LinearLayout container,
            @NonNull ParsedRecipe parsedRecipe) {
        container.removeAllViews();
        for (String tip : parsedRecipe.getTips()) {
            TextView view = createText(activity, "• " + tip, 14, "#5E4A3B", R.font.be_vietnam_pro);
            view.setPadding(0, dp(activity, 4), 0, dp(activity, 4));
            container.addView(view);
        }
    }

    @NonNull
    private static TextView createText(
            @NonNull AppCompatActivity activity,
            @NonNull String text,
            int textSizeSp,
            @NonNull String color,
            int fontRes) {
        TextView textView = new TextView(activity);
        textView.setText(text);
        textView.setTextSize(textSizeSp);
        textView.setTextColor(Color.parseColor(color));
        Typeface typeface = ResourcesCompat.getFont(activity, fontRes);
        if (typeface != null) {
            textView.setTypeface(typeface);
        }
        return textView;
    }

    private static int dp(@NonNull View view, int value) {
        return Math.round(value * view.getResources().getDisplayMetrics().density);
    }

    private static int dp(@NonNull AppCompatActivity activity, int value) {
        return Math.round(value * activity.getResources().getDisplayMetrics().density);
    }
}
