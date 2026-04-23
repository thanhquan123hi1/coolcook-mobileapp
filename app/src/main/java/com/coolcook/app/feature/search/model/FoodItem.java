package com.coolcook.app.feature.search.model;

import android.content.Context;

import androidx.annotation.DrawableRes;
import androidx.annotation.NonNull;

import com.coolcook.app.R;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

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
    public String getRecipe() {
        return recipe;
    }

    public int getCookTimeMinutes() {
        return cookTimeMinutes;
    }

    @DrawableRes
    public int resolveImageResId(@NonNull Context context) {
        int drawableId = context.getResources().getIdentifier(image, "drawable", context.getPackageName());
        if (drawableId != 0) {
            return drawableId;
        }

        if ("food_pho_bo".equals(image)) {
            return R.drawable.img_home_recipe_pho;
        }
        if ("food_goi_cuon".equals(image)) {
            return R.drawable.img_home_recipe_goi_cuon;
        }
        return R.drawable.img_scan_food_salad;
    }
}
