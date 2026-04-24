package com.coolcook.app.feature.camera.data;

import android.content.Context;
import android.content.SharedPreferences;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.coolcook.app.feature.camera.model.ScanDishItem;
import com.coolcook.app.feature.search.data.FoodJsonRepository;
import com.coolcook.app.feature.search.model.FoodItem;

import org.json.JSONArray;
import org.json.JSONObject;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

public class ScanSavedDishStore {

    private static final String PREF_NAME = "scan_saved_dishes";
    private static final String KEY_DISHES = "saved_items";

    @NonNull
    private final SharedPreferences preferences;
    @NonNull
    private final FoodJsonRepository foodJsonRepository;

    public ScanSavedDishStore(@NonNull Context context) {
        Context appContext = context.getApplicationContext();
        preferences = appContext.getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE);
        foodJsonRepository = new FoodJsonRepository(appContext);
    }

    public boolean save(@NonNull ScanDishItem item) {
        try {
            JSONArray array = readArray();
            String stableId = item.getStableId();
            for (int index = 0; index < array.length(); index++) {
                JSONObject existing = array.optJSONObject(index);
                if (existing != null && stableId.equals(existing.optString("stableId"))) {
                    return false;
                }
            }

            JSONObject payload = new JSONObject();
            payload.put("stableId", item.getStableId());
            payload.put("name", item.getName());
            payload.put("isLocal", item.isLocal());
            payload.put("foodId", item.getFoodId());
            payload.put("healthTags", new JSONArray(item.getHealthTags()));
            payload.put("usedIngredients", new JSONArray(item.getUsedIngredients()));
            payload.put("missingIngredients", new JSONArray(item.getMissingIngredients()));
            payload.put("reason", item.getReason());
            payload.put("recipe", item.getRecipe());
            payload.put("confidence", item.getConfidence());
            payload.put("savedAt", System.currentTimeMillis());
            array.put(payload);

            preferences.edit().putString(KEY_DISHES, array.toString()).apply();
            return true;
        } catch (Exception ignored) {
            return false;
        }
    }

    @NonNull
    public List<ScanDishItem> getSavedDishes() {
        List<SavedDishRecord> records = new ArrayList<>();
        JSONArray array = readArray();
        for (int index = 0; index < array.length(); index++) {
            JSONObject item = array.optJSONObject(index);
            if (item == null) {
                continue;
            }

            String name = item.optString("name", "").trim();
            String stableId = item.optString("stableId", "").trim();
            if (name.isEmpty() || stableId.isEmpty()) {
                continue;
            }

            FoodItem localFood = findLocalFood(item.optString("foodId", "").trim());
            ScanDishItem dishItem = new ScanDishItem(
                    stableId,
                    localFood != null ? localFood.getName() : name,
                    localFood,
                    toStringList(item.optJSONArray("usedIngredients")),
                    toStringList(item.optJSONArray("missingIngredients")),
                    toStringList(item.optJSONArray("healthTags")),
                    item.optString("reason", "").trim(),
                    item.optString("recipe", "").trim(),
                    item.optDouble("confidence", 0d));
            records.add(new SavedDishRecord(dishItem, item.optLong("savedAt", 0L)));
        }

        records.sort(Comparator.comparingLong(SavedDishRecord::getSavedAt).reversed());
        List<ScanDishItem> items = new ArrayList<>();
        for (SavedDishRecord record : records) {
            items.add(record.dishItem);
        }
        return items;
    }

    @Nullable
    private FoodItem findLocalFood(@NonNull String foodId) {
        if (foodId.isEmpty()) {
            return null;
        }
        return foodJsonRepository.findById(foodId);
    }

    @NonNull
    private JSONArray readArray() {
        String raw = preferences.getString(KEY_DISHES, "[]");
        try {
            return new JSONArray(raw);
        } catch (Exception ignored) {
            return new JSONArray();
        }
    }

    @NonNull
    private static List<String> toStringList(@Nullable JSONArray jsonArray) {
        List<String> values = new ArrayList<>();
        if (jsonArray == null) {
            return values;
        }
        for (int index = 0; index < jsonArray.length(); index++) {
            String value = jsonArray.optString(index, "").trim();
            if (!value.isEmpty() && !values.contains(value)) {
                values.add(value);
            }
        }
        return values;
    }

    private static final class SavedDishRecord {
        @NonNull
        final ScanDishItem dishItem;
        final long savedAt;

        SavedDishRecord(@NonNull ScanDishItem dishItem, long savedAt) {
            this.dishItem = dishItem;
            this.savedAt = savedAt;
        }

        long getSavedAt() {
            return savedAt;
        }
    }
}
