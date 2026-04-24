package com.coolcook.app.feature.camera.data;

import androidx.annotation.NonNull;

import java.text.Normalizer;
import java.util.Arrays;
import java.util.List;
import java.util.Locale;

public final class ScanHealthFilters {

    public static final String FILTER_ALL = "Tất cả";
    public static final String FILTER_GAIN = "Tăng cơ";
    public static final String FILTER_STOMACH = "Dạ dày";
    public static final String FILTER_FAT = "Mỡ nhiễm máu";
    public static final String FILTER_DIABETES = "Tiểu đường";
    public static final String FILTER_LIGHT = "Ăn nhẹ bụng";

    private ScanHealthFilters() {
    }

    @NonNull
    public static List<String> defaultFilters() {
        return Arrays.asList(
                FILTER_ALL,
                FILTER_GAIN,
                FILTER_STOMACH,
                FILTER_FAT,
                FILTER_DIABETES,
                FILTER_LIGHT);
    }

    public static boolean matches(@NonNull List<String> tags, @NonNull String filter) {
        if (FILTER_ALL.equals(filter)) {
            return true;
        }
        for (String tag : tags) {
            if (matchesSingle(tag, filter)) {
                return true;
            }
        }
        return false;
    }

    private static boolean matchesSingle(@NonNull String tag, @NonNull String filter) {
        String normalized = normalize(tag);
        if (FILTER_GAIN.equals(filter)) {
            return normalized.contains("tang co") || normalized.contains("co bap");
        }
        if (FILTER_STOMACH.equals(filter)) {
            return normalized.contains("da day") || normalized.contains("om day");
        }
        if (FILTER_FAT.equals(filter)) {
            return normalized.contains("mo nhiem mau")
                    || normalized.contains("it dau mo")
                    || normalized.contains("han che dau mo");
        }
        if (FILTER_DIABETES.equals(filter)) {
            return normalized.contains("tieu duong");
        }
        if (FILTER_LIGHT.equals(filter)) {
            return normalized.contains("nhe bung")
                    || normalized.contains("de tieu")
                    || normalized.contains("mem de tieu");
        }
        return false;
    }

    @NonNull
    private static String normalize(@NonNull String input) {
        String ascii = Normalizer.normalize(input, Normalizer.Form.NFD)
                .replaceAll("\\p{M}+", "")
                .replace('đ', 'd')
                .replace('Đ', 'D');
        return ascii.toLowerCase(Locale.ROOT)
                .replaceAll("[^a-z0-9\\s]", " ")
                .replaceAll("\\s+", " ")
                .trim();
    }
}
