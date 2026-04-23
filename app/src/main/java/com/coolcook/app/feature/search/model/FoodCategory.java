package com.coolcook.app.feature.search.model;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

public enum FoodCategory {
    DRY("Món khô"),
    SOUP("Món nước");

    @NonNull
    private final String displayName;

    FoodCategory(@NonNull String displayName) {
        this.displayName = displayName;
    }

    @NonNull
    public String getDisplayName() {
        return displayName;
    }

    @NonNull
    public static FoodCategory fromValue(@Nullable String value) {
        if (value == null) {
            return DRY;
        }

        for (FoodCategory category : values()) {
            if (category.name().equalsIgnoreCase(value.trim())) {
                return category;
            }
        }
        return DRY;
    }
}
