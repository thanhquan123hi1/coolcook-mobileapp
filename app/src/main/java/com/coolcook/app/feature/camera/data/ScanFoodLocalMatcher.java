package com.coolcook.app.feature.camera.data;

import android.content.Context;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.coolcook.app.feature.camera.model.DetectedIngredient;
import com.coolcook.app.feature.camera.model.ScanDishItem;
import com.coolcook.app.feature.search.data.FoodJsonRepository;
import com.coolcook.app.feature.search.model.FoodItem;
import com.coolcook.app.feature.search.model.ParsedRecipe;
import com.coolcook.app.feature.search.parser.RecipeParser;

import java.text.Normalizer;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;

public class ScanFoodLocalMatcher {

    @NonNull
    private final List<FoodItem> foods;
    @NonNull
    private final List<String> ingredientVocabulary;

    public ScanFoodLocalMatcher(@NonNull Context context) {
        foods = new FoodJsonRepository(context).getFoods();
        ingredientVocabulary = buildIngredientVocabulary(foods);
    }

    @Nullable
    public FoodItem findDishByName(@NonNull String dishName) {
        String target = normalize(dishName);
        if (target.isEmpty()) {
            return null;
        }

        FoodItem bestMatch = null;
        int bestScore = 0;
        for (FoodItem food : foods) {
            String candidate = normalize(food.getName());
            int score = scoreMatch(target, candidate);
            if (score > bestScore) {
                bestScore = score;
                bestMatch = food;
            }
        }
        return bestScore >= 70 ? bestMatch : null;
    }

    @NonNull
    public String normalizeIngredientName(@NonNull String ingredientName) {
        String raw = ingredientName.trim();
        String target = normalize(raw);
        if (target.isEmpty()) {
            return raw;
        }

        String bestMatch = raw;
        int bestScore = 0;
        for (String candidate : ingredientVocabulary) {
            int score = scoreMatch(target, normalize(candidate));
            if (score > bestScore) {
                bestScore = score;
                bestMatch = candidate;
            }
        }
        return bestScore >= 65 ? bestMatch : raw;
    }

    @NonNull
    public String createStableId(@NonNull String name, boolean local) {
        return (local ? "local:" : "ai:") + normalize(name);
    }

    @NonNull
    public List<ScanDishItem> suggestDishes(@NonNull List<DetectedIngredient> detectedIngredients, int limit) {
        if (detectedIngredients.isEmpty() || limit <= 0) {
            return new ArrayList<>();
        }

        List<LocalSuggestion> rankedSuggestions = new ArrayList<>();
        for (FoodItem food : foods) {
            ParsedRecipe recipe = RecipeParser.parse(food.getRecipe());
            List<ParsedRecipe.Ingredient> recipeIngredients = recipe.getIngredients();
            if (recipeIngredients.isEmpty()) {
                continue;
            }

            List<String> usedIngredients = new ArrayList<>();
            List<String> missingIngredients = new ArrayList<>();
            double totalSimilarity = 0d;

            for (ParsedRecipe.Ingredient recipeIngredient : recipeIngredients) {
                String recipeName = recipeIngredient.getName().trim();
                if (recipeName.isEmpty()) {
                    continue;
                }

                String matchedDetectedName = null;
                int bestSimilarity = 0;
                for (DetectedIngredient detectedIngredient : detectedIngredients) {
                    String detectedName = detectedIngredient.getName().trim();
                    if (detectedName.isEmpty()) {
                        continue;
                    }

                    int similarity = scoreMatch(normalize(detectedName), normalize(recipeName));
                    if (similarity > bestSimilarity) {
                        bestSimilarity = similarity;
                        matchedDetectedName = detectedName;
                    }
                }

                if (bestSimilarity >= 72 && matchedDetectedName != null) {
                    if (!usedIngredients.contains(matchedDetectedName)) {
                        usedIngredients.add(matchedDetectedName);
                    }
                    totalSimilarity += bestSimilarity / 100d;
                } else if (!missingIngredients.contains(recipeName)) {
                    missingIngredients.add(recipeName);
                }
            }

            if (usedIngredients.isEmpty()) {
                continue;
            }

            double recipeCoverage = usedIngredients.size() / (double) Math.max(1, recipeIngredients.size());
            double pantryCoverage = usedIngredients.size() / (double) Math.max(1, detectedIngredients.size());
            double ingredientQuality = totalSimilarity / Math.max(1, usedIngredients.size());
            double confidence = Math.min(
                    0.98d,
                    recipeCoverage * 0.5d + pantryCoverage * 0.3d + ingredientQuality * 0.2d);

            rankedSuggestions.add(new LocalSuggestion(
                    food,
                    usedIngredients,
                    missingIngredients,
                    confidence));
        }

        Collections.sort(rankedSuggestions, Comparator
                .comparingDouble(LocalSuggestion::getConfidence).reversed()
                .thenComparing(Comparator.comparingInt(LocalSuggestion::getUsedCount).reversed())
                .thenComparingInt(LocalSuggestion::getMissingCount)
                .thenComparing(suggestion -> suggestion.food.getName(), String.CASE_INSENSITIVE_ORDER));

        List<ScanDishItem> dishItems = new ArrayList<>();
        for (int index = 0; index < rankedSuggestions.size() && dishItems.size() < limit; index++) {
            LocalSuggestion suggestion = rankedSuggestions.get(index);
            dishItems.add(new ScanDishItem(
                    createStableId(suggestion.food.getId(), true),
                    suggestion.food.getName(),
                    suggestion.food,
                    suggestion.usedIngredients,
                    suggestion.missingIngredients,
                    suggestion.food.getSuitableFor(),
                    buildReason(suggestion.usedIngredients, suggestion.missingIngredients),
                    suggestion.food.getRecipe(),
                    suggestion.confidence));
        }
        return dishItems;
    }

    @NonNull
    private static String buildReason(
            @NonNull List<String> usedIngredients,
            @NonNull List<String> missingIngredients) {
        StringBuilder reason = new StringBuilder("Phu hop vi ban da co ");
        reason.append(joinPreview(usedIngredients, 3)).append('.');
        if (!missingIngredients.isEmpty()) {
            reason.append(" Con thieu ").append(joinPreview(missingIngredients, 3)).append('.');
        } else {
            reason.append(" Ban da co du nhung nguyen lieu chinh de nau mon nay.");
        }
        return reason.toString();
    }

    @NonNull
    private static String joinPreview(@NonNull List<String> values, int maxItems) {
        List<String> preview = new ArrayList<>();
        for (int index = 0; index < values.size() && index < maxItems; index++) {
            String value = values.get(index).trim();
            if (!value.isEmpty() && !preview.contains(value)) {
                preview.add(value);
            }
        }
        return preview.isEmpty() ? "mot so nguyen lieu phu hop" : String.join(", ", preview);
    }

    @NonNull
    private static List<String> buildIngredientVocabulary(@NonNull List<FoodItem> foods) {
        Set<String> values = new LinkedHashSet<>();
        for (FoodItem food : foods) {
            ParsedRecipe recipe = RecipeParser.parse(food.getRecipe());
            for (ParsedRecipe.Ingredient ingredient : recipe.getIngredients()) {
                String name = ingredient.getName().trim();
                if (!name.isEmpty()) {
                    values.add(name);
                }
            }
        }
        return new ArrayList<>(values);
    }

    private static int scoreMatch(@NonNull String target, @NonNull String candidate) {
        if (target.equals(candidate)) {
            return 100;
        }
        if (candidate.contains(target) || target.contains(candidate)) {
            return 82 - Math.abs(candidate.length() - target.length());
        }

        List<String> targetTokens = tokenize(target);
        List<String> candidateTokens = tokenize(candidate);
        int shared = 0;
        for (String token : targetTokens) {
            if (candidateTokens.contains(token)) {
                shared++;
            }
        }
        if (shared == 0) {
            return 0;
        }

        int denominator = Math.max(targetTokens.size(), candidateTokens.size());
        return (shared * 100) / Math.max(1, denominator);
    }

    @NonNull
    private static List<String> tokenize(@NonNull String value) {
        List<String> tokens = new ArrayList<>();
        for (String token : value.split("\\s+")) {
            if (!token.isEmpty()) {
                tokens.add(token);
            }
        }
        return tokens;
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

    private static final class LocalSuggestion {
        @NonNull
        final FoodItem food;
        @NonNull
        final List<String> usedIngredients;
        @NonNull
        final List<String> missingIngredients;
        final double confidence;

        LocalSuggestion(
                @NonNull FoodItem food,
                @NonNull List<String> usedIngredients,
                @NonNull List<String> missingIngredients,
                double confidence) {
            this.food = food;
            this.usedIngredients = new ArrayList<>(usedIngredients);
            this.missingIngredients = new ArrayList<>(missingIngredients);
            this.confidence = confidence;
        }

        double getConfidence() {
            return confidence;
        }

        int getUsedCount() {
            return usedIngredients.size();
        }

        int getMissingCount() {
            return missingIngredients.size();
        }
    }
}
