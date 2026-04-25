package com.coolcook.app.feature.recommendation.data;

import android.content.Context;
import android.text.TextUtils;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.coolcook.app.feature.chatbot.data.GeminiRepository;
import com.coolcook.app.feature.profile.data.HealthAnalyzer;
import com.coolcook.app.feature.profile.data.HealthProfileRepository;
import com.coolcook.app.feature.profile.model.HealthAnalysisResult;
import com.coolcook.app.feature.profile.model.HealthProfile;
import com.coolcook.app.feature.recommendation.model.HealthRecommendedFood;
import com.coolcook.app.feature.search.data.FoodJsonRepository;
import com.coolcook.app.feature.search.model.FoodItem;
import com.google.firebase.firestore.FirebaseFirestore;

import org.json.JSONArray;
import org.json.JSONObject;

import java.text.Normalizer;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;

public class HealthFoodRecommendationRepository {

    public interface Callback {
        void onComplete(
                @Nullable HealthProfile profile,
                @Nullable HealthAnalysisResult analysis,
                @NonNull List<HealthRecommendedFood> recommendations,
                @Nullable String friendlyError);
    }

    @NonNull
    private final FoodJsonRepository foodJsonRepository;
    @NonNull
    private final HealthProfileRepository healthProfileRepository;
    @NonNull
    private final GeminiRepository geminiRepository;

    public HealthFoodRecommendationRepository(@NonNull Context context) {
        Context appContext = context.getApplicationContext();
        this.foodJsonRepository = new FoodJsonRepository(appContext);
        this.healthProfileRepository = new HealthProfileRepository(FirebaseFirestore.getInstance());
        this.geminiRepository = new GeminiRepository();
    }

    public void loadRecommendations(@NonNull String userId, @NonNull Callback callback) {
        healthProfileRepository.loadHealthProfile(userId, (profile, error) -> {
            if (profile == null) {
                callback.onComplete(null, null, new ArrayList<>(), error);
                return;
            }

            HealthAnalysisResult analysis = HealthAnalyzer.analyze(profile);
            List<HealthRecommendedFood> localRecommendations = buildLocalRecommendations(analysis);
            if (localRecommendations.size() >= 3) {
                callback.onComplete(profile, analysis, top(localRecommendations, 5), null);
                return;
            }

            requestAiFallback(profile, analysis, localRecommendations, callback);
        });
    }

    @NonNull
    private List<HealthRecommendedFood> buildLocalRecommendations(@NonNull HealthAnalysisResult analysis) {
        List<ScoredFood> scoredFoods = new ArrayList<>();
        for (FoodItem food : foodJsonRepository.getFoods()) {
            int score = scoreFood(food, analysis);
            if (score <= 0) {
                continue;
            }
            scoredFoods.add(new ScoredFood(food, score));
        }

        scoredFoods.sort(Comparator
                .comparingInt(ScoredFood::getScore).reversed()
                .thenComparing(item -> item.food.getName(), String.CASE_INSENSITIVE_ORDER));

        List<HealthRecommendedFood> results = new ArrayList<>();
        for (ScoredFood scoredFood : scoredFoods) {
            results.add(new HealthRecommendedFood(
                    scoredFood.food,
                    scoredFood.food.getName(),
                    buildReason(scoredFood.food, analysis),
                    scoredFood.food.getSuitableFor(),
                    join(analysis.getShouldLimit()),
                    scoredFood.food.getRecipe()));
        }
        return top(results, 5);
    }

    private int scoreFood(@NonNull FoodItem food, @NonNull HealthAnalysisResult analysis) {
        int score = 0;
        for (String suitable : food.getSuitableFor()) {
            String normalizedSuitable = normalize(suitable);
            for (String tag : analysis.getTags()) {
                String normalizedTag = normalize(tag);
                if (normalizedSuitable.equals(normalizedTag)) {
                    score += 3;
                    continue;
                }
                if (isNearMeaning(normalizedSuitable, normalizedTag)) {
                    score += 2;
                }
            }
        }

        String recipe = normalize(food.getRecipe());
        boolean highPressure = containsAny(analysis.getTags(), "huyết áp cao", "nhịp tim cao");
        if (highPressure && containsAnyNormalized(recipe, "dau dieu", "gio heo", "mam ruoc", "nhieu muoi")) {
            score -= 3;
        }
        if (highPressure && containsAnyNormalized(recipe, "do chien", "ot", "sa te")) {
            score -= 2;
        }

        if (score == 0 && containsAny(analysis.getTags(), "ăn cân bằng")) {
            score = 1;
        }
        return score;
    }

    private void requestAiFallback(
            @NonNull HealthProfile profile,
            @NonNull HealthAnalysisResult analysis,
            @NonNull List<HealthRecommendedFood> localRecommendations,
            @NonNull Callback callback) {
        String prompt = buildPrompt(profile, analysis, localRecommendations);
        geminiRepository.requestStructuredResponse(
                "Bạn là AI gợi ý món ăn cho ứng dụng CoolCook. "
                        + "Chỉ trả về JSON hợp lệ, không markdown, không giải thích ngoài JSON.",
                prompt,
                null,
                null,
                new GeminiRepository.StreamCallback() {
                    @Override
                    public void onStart() {
                    }

                    @Override
                    public void onChunk(@NonNull String accumulatedText) {
                    }

                    @Override
                    public void onCompleted(@NonNull String finalText) {
                        List<HealthRecommendedFood> merged = new ArrayList<>(localRecommendations);
                        merged.addAll(parseAiRecommendations(finalText));
                        callback.onComplete(profile, analysis, top(deduplicate(merged), 5), null);
                    }

                    @Override
                    public void onError(@NonNull String friendlyError) {
                        callback.onComplete(profile, analysis, top(localRecommendations, 5), null);
                    }
                });
    }

    @NonNull
    private String buildPrompt(
            @NonNull HealthProfile profile,
            @NonNull HealthAnalysisResult analysis,
            @NonNull List<HealthRecommendedFood> localRecommendations) {
        return "{\n"
                + "  \"task\": \"goi_y_mon_an_suc_khoe\",\n"
                + "  \"healthProfile\": {\n"
                + "    \"weightKg\": " + profile.getWeightKg() + ",\n"
                + "    \"systolicBp\": " + profile.getSystolicBp() + ",\n"
                + "    \"diastolicBp\": " + profile.getDiastolicBp() + ",\n"
                + "    \"heartRateBpm\": " + profile.getHeartRateBpm() + ",\n"
                + "    \"goal\": \"" + escape(profile.getGoal()) + "\"\n"
                + "  },\n"
                + "  \"analysis\": {\n"
                + "    \"summary\": \"" + escape(analysis.getSummary()) + "\",\n"
                + "    \"tags\": " + new JSONArray(analysis.getTags()) + ",\n"
                + "    \"shouldEat\": " + new JSONArray(analysis.getShouldEat()) + ",\n"
                + "    \"shouldLimit\": " + new JSONArray(analysis.getShouldLimit()) + "\n"
                + "  },\n"
                + "  \"existingLocalRecommendations\": " + new JSONArray(extractNames(localRecommendations)) + ",\n"
                + "  \"rules\": [\n"
                + "    \"de xuat them 3 mon moi, khong trung mon local\",\n"
                + "    \"ngon ngu an toan: chi dung cac cum tu co the phu hop, nen uu tien, nen han che, goi y tham khao\",\n"
                + "    \"khong duoc chan doan, dieu tri hay khang dinh benh\",\n"
                + "    \"mon phai de lam, thanh dam va phu hop boi canh Viet Nam\"\n"
                + "  ],\n"
                + "  \"outputSchema\": {\n"
                + "    \"recommendations\": [\n"
                + "      {\n"
                + "        \"name\": \"\",\n"
                + "        \"reason\": \"\",\n"
                + "        \"suitableFor\": [],\n"
                + "        \"shouldLimit\": \"\",\n"
                + "        \"simpleRecipe\": \"\"\n"
                + "      }\n"
                + "    ]\n"
                + "  }\n"
                + "}";
    }

    @NonNull
    private List<HealthRecommendedFood> parseAiRecommendations(@NonNull String rawText) {
        List<HealthRecommendedFood> recommendations = new ArrayList<>();
        try {
            JSONObject root = new JSONObject(extractJsonPayload(rawText));
            JSONArray array = root.optJSONArray("recommendations");
            if (array == null) {
                return recommendations;
            }
            for (int index = 0; index < array.length(); index++) {
                JSONObject item = array.optJSONObject(index);
                if (item == null) {
                    continue;
                }
                String name = item.optString("name", "").trim();
                if (name.isEmpty()) {
                    continue;
                }
                recommendations.add(new HealthRecommendedFood(
                        null,
                        name,
                        item.optString("reason", "").trim(),
                        toStringList(item.optJSONArray("suitableFor")),
                        item.optString("shouldLimit", "").trim(),
                        item.optString("simpleRecipe", "").trim()));
            }
        } catch (Exception ignored) {
            return new ArrayList<>();
        }
        return recommendations;
    }

    @NonNull
    private static String buildReason(@NonNull FoodItem food, @NonNull HealthAnalysisResult analysis) {
        List<String> matchedTags = new ArrayList<>();
        for (String tag : food.getSuitableFor()) {
            if (containsNormalized(analysis.getTags(), tag)) {
                matchedTags.add(tag);
            }
        }
        if (matchedTags.isEmpty()) {
            return "Có thể phù hợp vì món này khá cân bằng và gần với gợi ý tham khảo hôm nay.";
        }
        return "Có thể phù hợp vì món này gần với các ưu tiên: " + join(matchedTags) + ".";
    }

    @NonNull
    private static List<HealthRecommendedFood> deduplicate(@NonNull List<HealthRecommendedFood> items) {
        List<HealthRecommendedFood> results = new ArrayList<>();
        Set<String> seen = new LinkedHashSet<>();
        for (HealthRecommendedFood item : items) {
            String key = normalize(item.getName());
            if (seen.contains(key)) {
                continue;
            }
            seen.add(key);
            results.add(item);
        }
        return results;
    }

    @NonNull
    private static List<HealthRecommendedFood> top(@NonNull List<HealthRecommendedFood> items, int limit) {
        List<HealthRecommendedFood> results = new ArrayList<>();
        for (int index = 0; index < items.size() && results.size() < limit; index++) {
            results.add(items.get(index));
        }
        return results;
    }

    @NonNull
    private static List<String> toStringList(@Nullable JSONArray array) {
        List<String> values = new ArrayList<>();
        if (array == null) {
            return values;
        }
        for (int index = 0; index < array.length(); index++) {
            String value = array.optString(index, "").trim();
            if (!value.isEmpty() && !values.contains(value)) {
                values.add(value);
            }
        }
        return values;
    }

    @NonNull
    private static List<String> extractNames(@NonNull List<HealthRecommendedFood> items) {
        List<String> names = new ArrayList<>();
        for (HealthRecommendedFood item : items) {
            names.add(item.getName());
        }
        return names;
    }

    @NonNull
    private static String join(@NonNull List<String> values) {
        return TextUtils.join(", ", values);
    }

    private static boolean containsAny(@NonNull List<String> values, @NonNull String... targets) {
        for (String target : targets) {
            if (containsNormalized(values, target)) {
                return true;
            }
        }
        return false;
    }

    private static boolean containsNormalized(@NonNull List<String> values, @NonNull String target) {
        String normalizedTarget = normalize(target);
        for (String value : values) {
            if (normalize(value).contains(normalizedTarget) || normalizedTarget.contains(normalize(value))) {
                return true;
            }
        }
        return false;
    }

    private static boolean containsAnyNormalized(@NonNull String value, @NonNull String... targets) {
        for (String target : targets) {
            if (value.contains(normalize(target))) {
                return true;
            }
        }
        return false;
    }

    private static boolean isNearMeaning(@NonNull String suitable, @NonNull String tag) {
        return (suitable.contains("mo nhiem mau") && tag.contains("han che dau mo"))
                || (suitable.contains("han che dau mo") && tag.contains("mo nhiem mau"))
                || (suitable.contains("an thanh dam") && tag.contains("an nhat"))
                || (suitable.contains("da day") && tag.contains("an nhe bung"))
                || (suitable.contains("nguoi can nhieu nang luong") && tag.contains("can nhieu nang luong"))
                || (suitable.contains("tang co bap") && tag.contains("tang co bap"))
                || (suitable.contains("an nhe bung") && tag.contains("nhip tim cao"))
                || (suitable.contains("de tieu") && tag.contains("an nhe bung"));
    }

    @NonNull
    private static String extractJsonPayload(@NonNull String rawText) {
        String trimmed = rawText.trim();
        int objectStart = trimmed.indexOf('{');
        int objectEnd = trimmed.lastIndexOf('}');
        if (objectStart >= 0 && objectEnd > objectStart) {
            return trimmed.substring(objectStart, objectEnd + 1);
        }
        return trimmed;
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

    @NonNull
    private static String escape(@NonNull String value) {
        return value.replace("\\", "\\\\").replace("\"", "\\\"");
    }

    private static final class ScoredFood {
        @NonNull
        final FoodItem food;
        final int score;

        ScoredFood(@NonNull FoodItem food, int score) {
            this.food = food;
            this.score = score;
        }

        int getScore() {
            return score;
        }
    }
}
