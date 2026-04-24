package com.coolcook.app.feature.camera.model;

import androidx.annotation.NonNull;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

public class SuggestedDish {

    @NonNull
    private final String name;
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

    public SuggestedDish(
            @NonNull String name,
            @NonNull List<String> usedIngredients,
            @NonNull List<String> missingIngredients,
            @NonNull List<String> healthTags,
            @NonNull String reason,
            @NonNull String recipe,
            double confidence) {
        this.name = name;
        this.usedIngredients = new ArrayList<>(usedIngredients);
        this.missingIngredients = new ArrayList<>(missingIngredients);
        this.healthTags = new ArrayList<>(healthTags);
        this.reason = reason;
        this.recipe = recipe;
        this.confidence = confidence;
    }

    @NonNull
    public String getName() {
        return name;
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
        return Collections.unmodifiableList(healthTags);
    }

    @NonNull
    public String getReason() {
        return reason;
    }

    @NonNull
    public String getRecipe() {
        return recipe;
    }

    public double getConfidence() {
        return confidence;
    }
}
