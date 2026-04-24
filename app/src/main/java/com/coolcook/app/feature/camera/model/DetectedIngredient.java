package com.coolcook.app.feature.camera.model;

import androidx.annotation.NonNull;

public class DetectedIngredient {

    @NonNull
    private final String name;
    private final double confidence;
    @NonNull
    private final String category;
    @NonNull
    private final String visibleAmount;
    @NonNull
    private final String notes;

    public DetectedIngredient(
            @NonNull String name,
            double confidence,
            @NonNull String category,
            @NonNull String visibleAmount,
            @NonNull String notes) {
        this.name = name;
        this.confidence = confidence;
        this.category = category;
        this.visibleAmount = visibleAmount;
        this.notes = notes;
    }

    @NonNull
    public String getName() {
        return name;
    }

    public double getConfidence() {
        return confidence;
    }

    @NonNull
    public String getCategory() {
        return category;
    }

    @NonNull
    public String getVisibleAmount() {
        return visibleAmount;
    }

    @NonNull
    public String getNotes() {
        return notes;
    }
}
