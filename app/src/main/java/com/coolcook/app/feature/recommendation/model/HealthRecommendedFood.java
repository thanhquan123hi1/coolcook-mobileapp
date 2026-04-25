package com.coolcook.app.feature.recommendation.model;

import android.content.Context;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.coolcook.app.R;
import com.coolcook.app.feature.camera.model.ScanDishItem;
import com.coolcook.app.feature.search.model.FoodItem;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

public class HealthRecommendedFood {

    @Nullable
    private final FoodItem localFood;
    @NonNull
    private final String name;
    @NonNull
    private final String reason;
    @NonNull
    private final List<String> suitableFor;
    @NonNull
    private final String shouldLimit;
    @NonNull
    private final String simpleRecipe;

    public HealthRecommendedFood(
            @Nullable FoodItem localFood,
            @NonNull String name,
            @NonNull String reason,
            @NonNull List<String> suitableFor,
            @NonNull String shouldLimit,
            @NonNull String simpleRecipe) {
        this.localFood = localFood;
        this.name = name;
        this.reason = reason;
        this.suitableFor = new ArrayList<>(suitableFor);
        this.shouldLimit = shouldLimit;
        this.simpleRecipe = simpleRecipe;
    }

    @Nullable
    public FoodItem getLocalFood() {
        return localFood;
    }

    public boolean isLocal() {
        return localFood != null;
    }

    @NonNull
    public String getFoodId() {
        return localFood == null ? "" : localFood.getId();
    }

    @NonNull
    public String getImageName() {
        return localFood == null ? "" : localFood.getImage();
    }

    @NonNull
    public String getName() {
        return name;
    }

    @NonNull
    public String getReason() {
        return reason;
    }

    @NonNull
    public List<String> getSuitableFor() {
        if (localFood != null) {
            return localFood.getSuitableFor();
        }
        return Collections.unmodifiableList(suitableFor);
    }

    @NonNull
    public String getShouldLimit() {
        return shouldLimit;
    }

    @NonNull
    public String getSimpleRecipe() {
        if (localFood != null) {
            return localFood.getRecipe();
        }
        return simpleRecipe;
    }

    public int resolveImageResId(@NonNull Context context) {
        if (localFood != null) {
            return localFood.resolveImageResId(context);
        }
        return R.drawable.img_scan_food_salad;
    }

    @NonNull
    public ScanDishItem toScanDishItem() {
        return new ScanDishItem(
                isLocal() ? "local:" + getFoodId() : "health:" + name.toLowerCase(),
                name,
                localFood,
                new ArrayList<>(),
                new ArrayList<>(),
                getSuitableFor(),
                reason,
                getSimpleRecipe(),
                isLocal() ? 0.92d : 0.84d);
    }
}
