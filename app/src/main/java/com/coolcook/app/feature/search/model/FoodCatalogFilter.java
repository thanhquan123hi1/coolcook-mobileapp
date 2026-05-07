package com.coolcook.app.feature.search.model;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.coolcook.app.feature.search.util.SearchTextUtils;

import java.util.Arrays;
import java.util.List;
import java.util.Locale;

public enum FoodCatalogFilter {
    ALL(null),
    DRY(null),
    SOUP(null),
    LOW_FAT(Arrays.asList(
            "mỡ nhiễm máu",
            "người ăn ít dầu mỡ",
            "người hạn chế dầu mỡ",
            "người cần món ít dầu",
            "người ăn thanh nhẹ",
            "người ăn thanh đạm"
    )),
    STOMACH(Arrays.asList(
            "dạ dày",
            "người đau dạ dày ăn thanh đạm",
            "người ăn mềm",
            "người ăn mềm dễ tiêu",
            "người cần món dễ tiêu",
            "người mới ốm dậy"
    )),
    PROTEIN(Arrays.asList(
            "tăng cơ bắp",
            "người vận động nhiều",
            "người tập luyện",
            "thiếu sắt",
            "người cần nhiều năng lượng",
            "người cần đạm nhẹ",
            "người cần omega-3"
    ));

    @Nullable
    private final List<String> keywords;

    FoodCatalogFilter(@Nullable List<String> keywords) {
        this.keywords = keywords;
    }

    public boolean matches(@NonNull FoodItem food) {
        if (this == ALL) {
            return true;
        }
        if (this == DRY) {
            return food.getCategory() == FoodCategory.DRY;
        }
        if (this == SOUP) {
            return food.getCategory() == FoodCategory.SOUP;
        }
        if (keywords == null || keywords.isEmpty()) {
            return true;
        }

        for (String suitable : food.getSuitableFor()) {
            String normalizedSuitable = SearchTextUtils.normalizeForSearch(suitable);
            for (String keyword : keywords) {
                if (normalizedSuitable.contains(SearchTextUtils.normalizeForSearch(keyword))) {
                    return true;
                }
            }
        }
        return false;
    }

    @NonNull
    public static FoodCatalogFilter fromExtra(@Nullable String value) {
        if (value == null || value.trim().isEmpty()) {
            return ALL;
        }
        try {
            return FoodCatalogFilter.valueOf(value.trim().toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException ignored) {
            return ALL;
        }
    }
}
