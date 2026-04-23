package com.coolcook.app.feature.search.util;

import androidx.annotation.NonNull;

import java.text.Normalizer;
import java.util.Locale;
import java.util.regex.Pattern;

public final class SearchTextUtils {

    private static final Pattern DIACRITICS_PATTERN = Pattern.compile("\\p{InCombiningDiacriticalMarks}+");

    private SearchTextUtils() {
    }

    @NonNull
    public static String normalizeForSearch(@NonNull String value) {
        String normalized = Normalizer.normalize(value, Normalizer.Form.NFD);
        normalized = DIACRITICS_PATTERN.matcher(normalized).replaceAll("");
        normalized = normalized.replace('đ', 'd').replace('Đ', 'D');
        return normalized.toLowerCase(Locale.ROOT).trim();
    }
}
