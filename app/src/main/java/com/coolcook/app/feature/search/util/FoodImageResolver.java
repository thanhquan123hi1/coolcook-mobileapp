package com.coolcook.app.feature.search.util;

import android.content.Context;
import android.util.Log;
import android.widget.ImageView;

import androidx.annotation.DrawableRes;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.coolcook.app.R;

public final class FoodImageResolver {

    private static final String TAG = "FoodImage";

    private FoodImageResolver() {
    }

    @DrawableRes
    public static int resolveImageResId(
            @NonNull Context context,
            @Nullable String imageName,
            @Nullable String foodName) {
        String safeImageName = imageName == null ? "" : imageName.trim();
        int imageResId = 0;
        if (!safeImageName.isEmpty()) {
            imageResId = context.getResources().getIdentifier(
                    safeImageName,
                    "drawable",
                    context.getPackageName());
        }

        if (imageResId != 0) {
            return imageResId;
        }

        Log.d(TAG, "Missing image for: " + (foodName == null || foodName.trim().isEmpty() ? safeImageName : foodName));
        return R.drawable.img_scan_food_salad;
    }

    public static void loadInto(
            @NonNull ImageView imageView,
            @Nullable String imageName,
            @Nullable String foodName) {
        imageView.setImageResource(resolveImageResId(imageView.getContext(), imageName, foodName));
    }
}
