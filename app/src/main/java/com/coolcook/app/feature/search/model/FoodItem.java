package com.coolcook.app.feature.search.model;

import android.content.Context;

import androidx.annotation.DrawableRes;
import androidx.annotation.NonNull;

import com.coolcook.app.feature.search.util.FoodImageResolver;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Locale;

public class FoodItem {

    @NonNull
    private final String id;
    @NonNull
    private final String name;
    @NonNull
    private final FoodCategory category;
    @NonNull
    private final String image;
    @NonNull
    private final List<String> suitableFor;
    @NonNull
    private final String recipe;
    private final int cookTimeMinutes;

    public FoodItem(
            @NonNull String id,
            @NonNull String name,
            @NonNull FoodCategory category,
            @NonNull String image,
            @NonNull List<String> suitableFor,
            @NonNull String recipe,
            int cookTimeMinutes) {
        this.id = id;
        this.name = name;
        this.category = category;
        this.image = image;
        this.suitableFor = new ArrayList<>(suitableFor);
        this.recipe = recipe;
        this.cookTimeMinutes = cookTimeMinutes;
    }

    @NonNull
    public String getId() {
        return id;
    }

    @NonNull
    public String getName() {
        return name;
    }

    @NonNull
    public FoodCategory getCategory() {
        return category;
    }

    @NonNull
    public String getImage() {
        return image;
    }

    @NonNull
    public List<String> getSuitableFor() {
        return Collections.unmodifiableList(suitableFor);
    }

    @NonNull
    public String getPrimarySuitableTag() {
        return suitableFor.isEmpty() ? "Gợi ý hôm nay" : suitableFor.get(0);
    }

    @NonNull
    public String getHomeCardTag() {
        String tag = getPrimarySuitableTag().trim();
        if (tag.isEmpty()) {
            return "Gợi ý hôm nay";
        }

        String normalized = tag.toLowerCase(Locale.ROOT);
        if (normalized.contains("tang co")) {
            return "Tăng cơ";
        }
        if (normalized.contains("it dau") || normalized.contains("han che dau") || normalized.contains("dau mo")) {
            return "Ít dầu";
        }
        if (normalized.contains("thanh dam")) {
            return "Thanh đạm";
        }
        if (normalized.contains("de tieu") || normalized.contains("nhe bung") || normalized.contains("da day")) {
            return "Dễ tiêu";
        }
        if (normalized.contains("can bang")) {
            return "Cân bằng";
        }
        if (normalized.contains("kiem soat can")) {
            return "Giữ dáng";
        }
        if (normalized.contains("nang luong")) {
            return "Nhiều năng lượng";
        }

        return tag.length() > 14 ? tag.substring(0, 14).trim() : tag;
    }

    @NonNull
    public String getRecipe() {
        return recipe;
    }

    public int getCookTimeMinutes() {
        return cookTimeMinutes;
    }

    @DrawableRes
    public int resolveImageResId(@NonNull Context context) {
        return FoodImageResolver.resolveImageResId(context, image, name);
    }
}
