package com.coolcook.app.feature.camera.data;

import android.content.Context;
import android.content.SharedPreferences;

import androidx.annotation.NonNull;

import com.coolcook.app.feature.camera.model.ScanDishItem;

import org.json.JSONArray;
import org.json.JSONObject;

public class ScanSavedDishStore {

    private static final String PREF_NAME = "scan_saved_dishes";
    private static final String KEY_DISHES = "saved_items";

    @NonNull
    private final SharedPreferences preferences;

    public ScanSavedDishStore(@NonNull Context context) {
        preferences = context.getApplicationContext().getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE);
    }

    public boolean save(@NonNull ScanDishItem item) {
        try {
            JSONArray array = readArray();
            String stableId = item.getStableId();
            for (int index = 0; index < array.length(); index++) {
                JSONObject existing = array.optJSONObject(index);
                if (existing != null && stableId.equals(existing.optString("stableId"))) {
                    existing.put("savedAt", System.currentTimeMillis());
                    preferences.edit().putString(KEY_DISHES, array.toString()).apply();
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
            payload.put("savedAt", System.currentTimeMillis());
            array.put(payload);

            preferences.edit().putString(KEY_DISHES, array.toString()).apply();
            return true;
        } catch (Exception ignored) {
            return false;
        }
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
}
