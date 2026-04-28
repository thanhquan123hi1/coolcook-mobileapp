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
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

public class ScanFoodLocalMatcher {

    @NonNull
    private final List<FoodItem> foods;
    @NonNull
    private final List<String> ingredientVocabulary;
    @NonNull
    private final Set<String> normalizedIngredientVocabulary;

    public ScanFoodLocalMatcher(@NonNull Context context) {
        foods = new FoodJsonRepository(context).getFoods();
        ingredientVocabulary = buildIngredientVocabulary(foods);
        normalizedIngredientVocabulary = buildNormalizedIngredientVocabulary(ingredientVocabulary);
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
            int score = scoreMatch(target, normalize(food.getName()));
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

    public boolean hasCompleteLocalCoverage(@NonNull List<DetectedIngredient> detectedIngredients) {
        return !detectedIngredients.isEmpty() && findUncoveredIngredients(detectedIngredients).isEmpty();
    }

    @NonNull
    public List<String> findUncoveredIngredients(@NonNull List<DetectedIngredient> detectedIngredients) {
        List<String> uncovered = new ArrayList<>();
        Set<String> seenNormalized = new LinkedHashSet<>();
        for (DetectedIngredient ingredient : detectedIngredients) {
            String rawName = ingredient.getName().trim();
            String normalizedName = normalize(rawName);
            if (normalizedName.isEmpty() || seenNormalized.contains(normalizedName)) {
                continue;
            }
            seenNormalized.add(normalizedName);
            if (!isCoveredByLocalDataStrict(rawName)) {
                uncovered.add(rawName);
            }
        }
        return uncovered;
    }

    @NonNull
    public List<ScanDishItem> suggestDishes(
            @NonNull List<DetectedIngredient> detectedIngredients,
            int limit,
            @NonNull String preferredHealthFilter) {
        return suggestDishesInternal(detectedIngredients, limit, preferredHealthFilter, true);
    }

    @NonNull
    public List<ScanDishItem> suggestDishesRelaxed(
            @NonNull List<DetectedIngredient> detectedIngredients,
            int limit,
            @NonNull String preferredHealthFilter) {
        return suggestDishesInternal(detectedIngredients, limit, preferredHealthFilter, false);
    }

    @NonNull
    private List<ScanDishItem> suggestDishesInternal(
            @NonNull List<DetectedIngredient> detectedIngredients,
            int limit,
            @NonNull String preferredHealthFilter,
            boolean strictMatching) {
        if (detectedIngredients.isEmpty() || limit <= 0) {
            return new ArrayList<>();
        }

        List<ScanDishItem> directMatches = findDirectDishMatches(
                detectedIngredients,
                limit,
                preferredHealthFilter);
        if (!directMatches.isEmpty()) {
            return directMatches;
        }

        List<LocalSuggestion> rankedSuggestions = new ArrayList<>();
        for (FoodItem food : foods) {
            ParsedRecipe recipe = RecipeParser.parse(food.getRecipe());
            List<ParsedRecipe.Ingredient> recipeIngredients = recipe.getIngredients();
            if (recipeIngredients.isEmpty()) {
                continue;
            }

            List<String> matchedIngredients = new ArrayList<>();
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
                    if (!matchedIngredients.contains(matchedDetectedName)) {
                        matchedIngredients.add(matchedDetectedName);
                    }
                    totalSimilarity += bestSimilarity / 100d;
                } else if (!missingIngredients.contains(recipeName)) {
                    missingIngredients.add(recipeName);
                }
            }

            if (matchedIngredients.isEmpty()) {
                continue;
            }

            double recipeCoverage = matchedIngredients.size() / (double) Math.max(1, recipeIngredients.size());
            double pantryCoverage = matchedIngredients.size() / (double) Math.max(1, detectedIngredients.size());
            double ingredientQuality = totalSimilarity / Math.max(1, matchedIngredients.size());
            boolean strongIngredientMatch = matchedIngredients.size() >= 2 || recipeCoverage >= 0.6d;
            if (strictMatching && !strongIngredientMatch) {
                continue;
            }
            double confidence = Math.min(
                    0.98d,
                    recipeCoverage * 0.55d + pantryCoverage * 0.25d + ingredientQuality * 0.20d);

            boolean healthMatch = ScanHealthFilters.matches(food.getSuitableFor(), preferredHealthFilter);
            rankedSuggestions.add(new LocalSuggestion(
                    food,
                    matchedIngredients,
                    missingIngredients,
                    confidence,
                    healthMatch));
        }

        Collections.sort(rankedSuggestions, Comparator
                .comparingInt(LocalSuggestion::getMatchedCount).reversed()
                .thenComparing(LocalSuggestion::hasHealthMatch, Comparator.reverseOrder())
                .thenComparingDouble(LocalSuggestion::getConfidence).reversed()
                .thenComparingInt(LocalSuggestion::getMissingCount)
                .thenComparing(suggestion -> suggestion.food.getName(), String.CASE_INSENSITIVE_ORDER));

        List<ScanDishItem> dishItems = new ArrayList<>();
        for (int index = 0; index < rankedSuggestions.size() && dishItems.size() < limit; index++) {
            LocalSuggestion suggestion = rankedSuggestions.get(index);
            dishItems.add(new ScanDishItem(
                    createStableId(suggestion.food.getId(), true),
                    suggestion.food.getName(),
                    suggestion.food,
                    suggestion.matchedIngredients,
                    suggestion.missingIngredients,
                    suggestion.food.getSuitableFor(),
                    buildReason(suggestion.matchedIngredients, suggestion.food.getName()),
                    suggestion.food.getRecipe(),
                    suggestion.confidence));
        }
        return dishItems;
    }

    @NonNull
    private List<ScanDishItem> findDirectDishMatches(
            @NonNull List<DetectedIngredient> detectedIngredients,
            int limit,
            @NonNull String preferredHealthFilter) {
        List<DirectSuggestion> rankedSuggestions = new ArrayList<>();
        Set<String> seenFoodIds = new LinkedHashSet<>();

        for (DetectedIngredient ingredient : detectedIngredients) {
            String detectedName = ingredient.getName().trim();
            if (detectedName.isEmpty()) {
                continue;
            }

            FoodItem localFood = findDishByName(detectedName);
            if (localFood == null || seenFoodIds.contains(localFood.getId())) {
                continue;
            }

            int nameScore = scoreMatch(normalize(detectedName), normalize(localFood.getName()));
            if (nameScore < 88) {
                continue;
            }

            seenFoodIds.add(localFood.getId());
            rankedSuggestions.add(new DirectSuggestion(
                    localFood,
                    detectedName,
                    Math.min(0.99d, 0.82d + (nameScore / 100d * 0.17d)),
                    ScanHealthFilters.matches(localFood.getSuitableFor(), preferredHealthFilter)));
        }

        rankedSuggestions.sort(Comparator
                .comparing(DirectSuggestion::hasHealthMatch, Comparator.reverseOrder())
                .thenComparingDouble(DirectSuggestion::getConfidence).reversed()
                .thenComparing(suggestion -> suggestion.food.getName(), String.CASE_INSENSITIVE_ORDER));

        List<ScanDishItem> dishItems = new ArrayList<>();
        for (int index = 0; index < rankedSuggestions.size() && dishItems.size() < limit; index++) {
            DirectSuggestion suggestion = rankedSuggestions.get(index);
            dishItems.add(new ScanDishItem(
                    createStableId(suggestion.food.getId(), true),
                    suggestion.food.getName(),
                    suggestion.food,
                    Collections.singletonList(suggestion.detectedName),
                    new ArrayList<>(),
                    suggestion.food.getSuitableFor(),
                    buildDirectReason(suggestion.detectedName, suggestion.food.getName()),
                    suggestion.food.getRecipe(),
                    suggestion.confidence));
        }
        return dishItems;
    }

    @NonNull
    public List<String> suggestComplementaryIngredients(
            @NonNull List<DetectedIngredient> detectedIngredients,
            @NonNull List<String> selectedIngredients,
            int limit) {
        if (limit <= 0) {
            return new ArrayList<>();
        }

        Set<String> normalizedSelected = new LinkedHashSet<>();
        for (DetectedIngredient ingredient : detectedIngredients) {
            normalizedSelected.add(normalize(ingredient.getName()));
        }
        for (String ingredient : selectedIngredients) {
            normalizedSelected.add(normalize(ingredient));
        }

        Map<String, Integer> scores = new LinkedHashMap<>();
        for (FoodItem food : foods) {
            ParsedRecipe recipe = RecipeParser.parse(food.getRecipe());
            List<ParsedRecipe.Ingredient> recipeIngredients = recipe.getIngredients();
            if (recipeIngredients.isEmpty()) {
                continue;
            }

            int matchedCount = 0;
            List<String> missingCandidates = new ArrayList<>();
            for (ParsedRecipe.Ingredient ingredient : recipeIngredients) {
                String name = ingredient.getName().trim();
                String normalized = normalize(name);
                if (name.isEmpty() || normalized.isEmpty()) {
                    continue;
                }
                if (normalizedSelected.contains(normalized)) {
                    matchedCount++;
                } else {
                    missingCandidates.add(name);
                }
            }

            if (matchedCount == 0 || missingCandidates.isEmpty()) {
                continue;
            }

            int recipeWeight = matchedCount * 10;
            for (String candidate : missingCandidates) {
                scores.put(candidate, scores.getOrDefault(candidate, 0) + recipeWeight);
            }
        }

        List<Map.Entry<String, Integer>> ranked = new ArrayList<>(scores.entrySet());
        ranked.sort(Map.Entry.<String, Integer>comparingByValue().reversed()
                .thenComparing(Map.Entry::getKey, String.CASE_INSENSITIVE_ORDER));

        List<String> suggestions = new ArrayList<>();
        for (Map.Entry<String, Integer> entry : ranked) {
            String candidate = entry.getKey().trim();
            if (candidate.isEmpty()) {
                continue;
            }
            String normalizedCandidate = normalize(candidate);
            if (normalizedSelected.contains(normalizedCandidate) || suggestions.contains(candidate)) {
                continue;
            }
            suggestions.add(candidate);
            if (suggestions.size() >= limit) {
                break;
            }
        }
        return suggestions;
    }

    @NonNull
    private static String buildReason(@NonNull List<String> matchedIngredients, @NonNull String dishName) {
        return "Vì có " + joinPreview(matchedIngredients, 3) + " nên phù hợp để nấu " + dishName + ".";
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
        return preview.isEmpty() ? "một vài nguyên liệu phù hợp" : String.join(", ", preview);
    }

    @NonNull
    private static String buildDirectReason(@NonNull String detectedName, @NonNull String dishName) {
        return "Nhận diện trực tiếp \"" + detectedName + "\" nên ưu tiên lấy món local " + dishName + ".";
    }

    @NonNull
    private Set<String> buildNormalizedIngredientVocabulary(@NonNull List<String> values) {
        Set<String> normalizedValues = new LinkedHashSet<>();
        for (String value : values) {
            String normalized = normalize(value);
            if (!normalized.isEmpty()) {
                normalizedValues.add(normalized);
            }
        }
        return normalizedValues;
    }

    private boolean isCoveredByLocalDataStrict(@NonNull String rawName) {
        String normalizedName = normalize(rawName);
        if (normalizedName.isEmpty()) {
            return false;
        }

        FoodItem localDish = findDishByName(rawName);
        if (localDish != null && scoreMatch(normalizedName, normalize(localDish.getName())) >= 90) {
            return true;
        }

        if (normalizedIngredientVocabulary.contains(normalizedName)) {
            return true;
        }

        for (String candidate : ingredientVocabulary) {
            if (scoreMatch(normalizedName, normalize(candidate)) >= 88) {
                return true;
            }
        }
        return false;
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
        final List<String> matchedIngredients;
        @NonNull
        final List<String> missingIngredients;
        final double confidence;
        final boolean healthMatch;

        LocalSuggestion(
                @NonNull FoodItem food,
                @NonNull List<String> matchedIngredients,
                @NonNull List<String> missingIngredients,
                double confidence,
                boolean healthMatch) {
            this.food = food;
            this.matchedIngredients = new ArrayList<>(matchedIngredients);
            this.missingIngredients = new ArrayList<>(missingIngredients);
            this.confidence = confidence;
            this.healthMatch = healthMatch;
        }

        int getMatchedCount() {
            return matchedIngredients.size();
        }

        int getMissingCount() {
            return missingIngredients.size();
        }

        double getConfidence() {
            return confidence;
        }

        boolean hasHealthMatch() {
            return healthMatch;
        }
    }

    private static final class DirectSuggestion {
        @NonNull
        final FoodItem food;
        @NonNull
        final String detectedName;
        final double confidence;
        final boolean healthMatch;

        DirectSuggestion(
                @NonNull FoodItem food,
                @NonNull String detectedName,
                double confidence,
                boolean healthMatch) {
            this.food = food;
            this.detectedName = detectedName;
            this.confidence = confidence;
            this.healthMatch = healthMatch;
        }

        double getConfidence() {
            return confidence;
        }

        boolean hasHealthMatch() {
            return healthMatch;
        }
    }
}
