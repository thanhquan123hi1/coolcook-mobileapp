package com.coolcook.app.ui.search.data;

import android.content.Context;
import android.content.SharedPreferences;

import androidx.annotation.NonNull;

import java.util.HashSet;
import java.util.Set;

public class FavoriteFoodStore {

    private static final String PREF_NAME = "favorite_foods";
    private static final String KEY_FAVORITE_IDS = "favorite_ids";

    @NonNull
    private final SharedPreferences preferences;

    public FavoriteFoodStore(@NonNull Context context) {
        preferences = context.getApplicationContext().getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE);
    }

    public boolean isFavorite(@NonNull String foodId) {
        return getFavoriteIds().contains(foodId);
    }

    public boolean toggle(@NonNull String foodId) {
        Set<String> nextIds = getFavoriteIds();
        boolean favorite;
        if (nextIds.contains(foodId)) {
            nextIds.remove(foodId);
            favorite = false;
        } else {
            nextIds.add(foodId);
            favorite = true;
        }

        preferences.edit().putStringSet(KEY_FAVORITE_IDS, nextIds).apply();
        return favorite;
    }

    @NonNull
    public Set<String> getFavoriteIds() {
        return new HashSet<>(preferences.getStringSet(KEY_FAVORITE_IDS, new HashSet<>()));
    }
}
