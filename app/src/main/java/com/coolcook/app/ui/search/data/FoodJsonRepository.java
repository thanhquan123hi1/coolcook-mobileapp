package com.coolcook.app.ui.search.data;

import android.content.Context;
import android.util.Log;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.coolcook.app.ui.search.model.FoodCategory;
import com.coolcook.app.ui.search.model.FoodItem;
import com.coolcook.app.ui.search.parser.RecipeParser;

import org.json.JSONArray;
import org.json.JSONObject;

import java.io.BufferedReader;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

public class FoodJsonRepository {

    private static final String TAG = "FoodJsonRepository";
    private static final String ASSET_FILE_NAME = "foods.json";

    @NonNull
    private final Context appContext;
    @Nullable
    private List<FoodItem> cachedFoods;

    public FoodJsonRepository(@NonNull Context context) {
        this.appContext = context.getApplicationContext();
    }

    @NonNull
    public List<FoodItem> getFoods() {
        if (cachedFoods != null) {
            return new ArrayList<>(cachedFoods);
        }

        List<FoodItem> foods = new ArrayList<>();
        try {
            JSONArray array = new JSONArray(readAssetsText());
            for (int index = 0; index < array.length(); index++) {
                JSONObject item = array.optJSONObject(index);
                if (item == null) {
                    continue;
                }
                foods.add(parseFoodItem(item));
            }
        } catch (Exception error) {
            Log.e(TAG, "Khong the doc danh sach mon an", error);
        }

        cachedFoods = foods;
        return new ArrayList<>(foods);
    }

    @Nullable
    public FoodItem findById(@NonNull String foodId) {
        for (FoodItem food : getFoods()) {
            if (food.getId().equals(foodId)) {
                return food;
            }
        }
        return null;
    }

    @NonNull
    private FoodItem parseFoodItem(@NonNull JSONObject item) {
        String recipe = item.optString("recipe", "");
        int cookTimeMinutes = item.optInt("cookTimeMinutes", RecipeParser.inferCookTimeMinutes(recipe));

        JSONArray suitableArray = item.optJSONArray("suitableFor");
        List<String> suitableFor = new ArrayList<>();
        if (suitableArray != null) {
            for (int index = 0; index < suitableArray.length(); index++) {
                String value = suitableArray.optString(index, "").trim();
                if (!value.isEmpty()) {
                    suitableFor.add(value);
                }
            }
        }

        return new FoodItem(
                item.optString("id", ""),
                item.optString("name", "Món ngon"),
                FoodCategory.fromValue(item.optString("category", "DRY")),
                item.optString("image", ""),
                suitableFor,
                recipe,
                cookTimeMinutes);
    }

    @NonNull
    private String readAssetsText() throws Exception {
        StringBuilder builder = new StringBuilder();
        try (InputStream inputStream = appContext.getAssets().open(ASSET_FILE_NAME);
             BufferedReader reader = new BufferedReader(
                     new InputStreamReader(inputStream, StandardCharsets.UTF_8))) {
            String line;
            while ((line = reader.readLine()) != null) {
                builder.append(line).append('\n');
            }
        }
        return builder.toString();
    }

    @NonNull
    public List<String> getRequiredDrawableNames() {
        List<String> names = new ArrayList<>();
        for (FoodItem item : getFoods()) {
            if (!item.getImage().isEmpty() && !names.contains(item.getImage())) {
                names.add(item.getImage());
            }
        }
        Collections.sort(names);
        return names;
    }
}
