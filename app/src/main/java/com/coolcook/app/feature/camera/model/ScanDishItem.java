package com.coolcook.app.feature.camera.model;

import android.content.Context;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.coolcook.app.feature.search.model.FoodItem;
import com.coolcook.app.feature.search.util.FoodImageResolver;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

public class ScanDishItem {

    @NonNull
    private final String stableId;
    @NonNull
    private final String name;
    @Nullable
    private final FoodItem localFood;
    @NonNull
    private final List<String> usedIngredients;
    @NonNull
    private final List<String> missingIngredients;
    @NonNull
    private final List<String> healthTags;
    @NonNull
    private final String reason;
    @NonNull
    private final String recipe;
    private final double confidence;

    public ScanDishItem(
            @NonNull String stableId,
            @NonNull String name,
            @Nullable FoodItem localFood,
            @NonNull List<String> usedIngredients,
            @NonNull List<String> missingIngredients,
            @NonNull List<String> healthTags,
            @NonNull String reason,
            @NonNull String recipe,
            double confidence) {
        this.stableId = stableId;
        this.name = name;
        this.localFood = localFood;
        this.usedIngredients = new ArrayList<>(usedIngredients);
        this.missingIngredients = new ArrayList<>(missingIngredients);
        this.healthTags = new ArrayList<>(healthTags);
        this.reason = reason;
        this.recipe = recipe;
        this.confidence = confidence;
    }

    @NonNull
    public String getStableId() {
        return stableId;
    }

    @NonNull
    public String getName() {
        return name;
    }

    public boolean isLocal() {
        return localFood != null;
    }

    @Nullable
    public FoodItem getLocalFood() {
        return localFood;
    }

    @NonNull
    public String getFoodId() {
        return localFood == null ? "" : localFood.getId();
    }

    @NonNull
    public List<String> getUsedIngredients() {
        return Collections.unmodifiableList(usedIngredients);
    }

    @NonNull
    public List<String> getMissingIngredients() {
        return Collections.unmodifiableList(missingIngredients);
    }

    @NonNull
    public List<String> getHealthTags() {
        if (isLocal() && localFood != null && !localFood.getSuitableFor().isEmpty()) {
            return localFood.getSuitableFor();
        }
        return Collections.unmodifiableList(healthTags);
    }

    @NonNull
    public String getReason() {
        if (!reason.trim().isEmpty()) {
            return reason;
        }
        return isLocal() ? "Món này đã có sẵn trong dữ liệu của CoolCook." : "Món này được AI gợi ý từ nguyên liệu đã nhận diện.";
    }

    @NonNull
    public String getRecipe() {
        if (isLocal() && localFood != null) {
            return localFood.getRecipe();
        }
        return recipe;
    }

    @NonNull
    public String getRecipePreview() {
        String source = getRecipe().replace('\n', ' ').trim();
        if (source.length() <= 160) {
            return source;
        }
        return source.substring(0, 160).trim() + "...";
    }

    public double getConfidence() {
        return confidence;
    }

    @NonNull
    public String getBadgeLabel() {
        return isLocal() ? "Có sẵn" : "AI gợi ý";
    }

    public int resolveImageResId(@NonNull Context context) {
        if (isLocal() && localFood != null) {
            return localFood.resolveImageResId(context);
        }
        return FoodImageResolver.resolveImageResId(context, null, name);
    }
}
