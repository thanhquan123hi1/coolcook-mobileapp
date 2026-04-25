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
        List<String> analysisTags = canonicalizeAll(analysis.getTags());
        List<String> foodTags = canonicalizeAll(food.getSuitableFor());
        String recipe = canonicalizeText(food.getRecipe());

        for (String foodTag : foodTags) {
            for (String analysisTag : analysisTags) {
                if (foodTag.equals(analysisTag)) {
                    score += 3;
                } else if (isNearMeaning(foodTag, analysisTag)) {
                    score += 2;
                }
            }
        }

        if (containsTag(analysisTags, "huyet ap cao") || containsTag(analysisTags, "nhip tim cao")) {
            if (containsAny(recipe, "dau dieu", "gio heo", "mam ruoc", "nhieu muoi")) {
                score -= 3;
            }
            if (containsAny(recipe, "do chien", "ot", "sa te", "qua cay")) {
                score -= 2;
            }
        }

        if (containsTag(analysisTags, "can nhieu nang luong")) {
            if (containsAny(foodTags, "can nhieu nang luong", "tang co bap")) {
                score += 2;
            }
        }

        if (containsTag(analysisTags, "kiem soat can nang")) {
            if (containsAny(foodTags, "kiem soat can nang", "an thanh dam", "han che dau mo")) {
                score += 2;
            }
        }

        if (containsTag(analysisTags, "an nhe bung")) {
            if (containsAny(foodTags, "an nhe bung", "de tieu", "da day")) {
                score += 2;
            }
        }

        if (score == 0 && containsTag(analysisTags, "an can bang")) {
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
                + "    \"de xuat them toi da 3 mon moi, khong trung mon local\",\n"
                + "    \"chi dung ngon ngu an toan: co the phu hop, nen uu tien, nen han che, goi y tham khao\",\n"
                + "    \"khong duoc chan doan, dieu tri hay khang dinh benh\",\n"
                + "    \"mon phai de lam, thanh dam va phu hop boi canh Viet Nam\",\n"
                + "    \"neu khong chac phu hop thi tra ve danh sach rong\"\n"
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
        List<String> normalizedAnalysisTags = canonicalizeAll(analysis.getTags());
        for (String tag : food.getSuitableFor()) {
            String canonical = canonicalizeTag(tag);
            if (normalizedAnalysisTags.contains(canonical)) {
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
            String key = canonicalizeText(item.getName());
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

    @NonNull
    private static List<String> canonicalizeAll(@NonNull List<String> values) {
        List<String> results = new ArrayList<>();
        for (String value : values) {
            String canonical = canonicalizeTag(value);
            if (!canonical.isEmpty() && !results.contains(canonical)) {
                results.add(canonical);
            }
        }
        return results;
    }

    private static boolean containsTag(@NonNull List<String> values, @NonNull String target) {
        String normalizedTarget = canonicalizeTag(target);
        return values.contains(normalizedTarget);
    }

    private static boolean containsAny(@NonNull List<String> values, @NonNull String... targets) {
        for (String target : targets) {
            if (containsTag(values, target)) {
                return true;
            }
        }
        return false;
    }

    private static boolean containsAny(@NonNull String value, @NonNull String... targets) {
        for (String target : targets) {
            if (value.contains(canonicalizeText(target))) {
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
    private static String canonicalizeTag(@Nullable String raw) {
        String value = canonicalizeText(raw);
        if (value.contains("m nhi m m u") || value.contains("mau") && value.contains("nhiem")) {
            return "mo nhiem mau";
        }
        if (value.contains("han che dau mo")) {
            return "han che dau mo";
        }
        if (value.contains("an thanh dam") || value.contains("an thanh nh")) {
            return "an thanh dam";
        }
        if (value.contains("de tieu")) {
            return "de tieu";
        }
        if (value.contains("da day") || value.contains("d d y")) {
            return "da day";
        }
        if (value.contains("an nhe bung")) {
            return "an nhe bung";
        }
        if (value.contains("kiem soat can nang")) {
            return "kiem soat can nang";
        }
        if (value.contains("can nhieu nang luong") || value.contains("nguoi can nhieu nang luong")) {
            return "can nhieu nang luong";
        }
        if (value.contains("tang co bap")) {
            return "tang co bap";
        }
        if (value.contains("huyet ap cao")) {
            return "huyet ap cao";
        }
        if (value.contains("huyet ap thap")) {
            return "huyet ap thap";
        }
        if (value.contains("an nhat")) {
            return "an nhat";
        }
        if (value.contains("nhip tim cao")) {
            return "nhip tim cao";
        }
        if (value.contains("nhip tim thap")) {
            return "nhip tim thap";
        }
        if (value.contains("it dau mo")) {
            return "it dau mo";
        }
        if (value.contains("nhieu rau")) {
            return "nhieu rau";
        }
        if (value.contains("bo sung dam")) {
            return "bo sung dam";
        }
        if (value.contains("an can bang")) {
            return "an can bang";
        }
        return value;
    }

    @NonNull
    private static String canonicalizeText(@Nullable String input) {
        if (input == null) {
            return "";
        }
        return HealthAnalyzer.normalize(input)
                .replace("m n", "mon")
                .replace("n ng", "nuong")
                .replace("lu c", "luoc")
                .replace("h p", "hap")
                .replace("nhi u", "nhieu")
                .replace("mu i", "muoi")
                .replace("d u", "dau")
                .replace("m m", "mam")
                .replace("c n", "can")
                .replace("d m", "dam")
                .replace("thanh d m", "thanh dam")
                .replace("nh p", "nhip")
                .replace("huy t", "huyet")
                .replace("ki m", "kiem")
                .replace("n ng l ng", "nang luong")
                .replace("b ng", "bung")
                .replace("ti u", "tieu")
                .replace("d y", "day")
                .replace("m u", "mau")
                .replaceAll("\\s+", " ")
                .trim();
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
