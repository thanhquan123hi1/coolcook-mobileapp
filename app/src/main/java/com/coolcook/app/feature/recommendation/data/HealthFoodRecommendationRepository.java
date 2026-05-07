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

            requestAiRecommendations(profile, callback);
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

    private void requestAiRecommendations(
            @NonNull HealthProfile profile,
            @NonNull Callback callback) {
        HealthAnalysisResult fallbackAnalysis = HealthAnalyzer.analyze(profile);
        List<HealthRecommendedFood> localRecommendations = buildLocalRecommendations(fallbackAnalysis);
        String prompt = buildPrompt(profile, fallbackAnalysis, localRecommendations);
        geminiRepository.requestStructuredResponse(
                "Bạn là chuyên gia dinh dưỡng và ẩm thực của ứng dụng CoolCook. "
                        + "Chỉ trả về JSON thuần túy hợp lệ, không markdown, không chào hỏi, không giải thích ngoài JSON.",
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
                        AiRecommendationResult aiResult = parseAiResult(finalText);
                        if (!aiResult.recommendations.isEmpty()) {
                            callback.onComplete(
                                    profile,
                                    aiResult.analysis == null ? fallbackAnalysis : aiResult.analysis,
                                    top(deduplicate(aiResult.recommendations), 5),
                                    null);
                            return;
                        }
                        callback.onComplete(profile, fallbackAnalysis, top(localRecommendations, 5), null);
                    }

                    @Override
                    public void onError(@NonNull String friendlyError) {
                        callback.onComplete(profile, fallbackAnalysis, top(localRecommendations, 5), null);
                    }
                });
    }

    @NonNull
    private String buildPrompt(
            @NonNull HealthProfile profile,
            @NonNull HealthAnalysisResult analysis,
            @NonNull List<HealthRecommendedFood> localRecommendations) {
        return "{\n"
                + "  \"role\": \"Bạn là chuyên gia dinh dưỡng và ẩm thực của ứng dụng CoolCook.\",\n"
                + "  \"task\": \"Phân tích chỉ số sức khỏe người dùng, sau đó chọn hoặc tự sáng tạo 3-5 món ăn phù hợp nhất.\",\n"
                + "  \"userProfile\": {\n"
                + "    \"weightKg\": " + profile.getWeightKg() + ",\n"
                + "    \"bloodPressure\": \"" + profile.getSystolicBp() + "/" + profile.getDiastolicBp() + "\",\n"
                + "    \"systolicBp\": " + profile.getSystolicBp() + ",\n"
                + "    \"diastolicBp\": " + profile.getDiastolicBp() + ",\n"
                + "    \"heartRateBpm\": " + profile.getHeartRateBpm() + ",\n"
                + "    \"goal\": \"" + escape(profile.getGoal()) + "\"\n"
                + "  },\n"
                + "  \"localMenu\": " + buildLocalMenuJson() + ",\n"
                + "  \"alreadySelectedLocalDishes\": " + new JSONArray(extractNames(localRecommendations)) + ",\n"
                + "  \"healthHintsFromApp\": {\n"
                + "    \"summary\": \"" + escape(analysis.getSummary()) + "\",\n"
                + "    \"tags\": " + new JSONArray(analysis.getTags()) + ",\n"
                + "    \"shouldEat\": " + new JSONArray(analysis.getShouldEat()) + ",\n"
                + "    \"shouldLimit\": " + new JSONArray(analysis.getShouldLimit()) + "\n"
                + "  },\n"
                + "  \"mandatoryRules\": [\n"
                + "    \"Đánh giá sức khỏe dựa trên cân nặng, huyết áp, nhịp tim và mục tiêu; nêu nên ăn gì và nên hạn chế gì.\",\n"
                + "    \"Nếu huyết áp từ 140/90 trở lên, ưu tiên ăn nhạt, hấp, luộc, nhiều rau; hạn chế nhiều muối, dầu mỡ, quá cay.\",\n"
                + "    \"Nếu nhịp tim trên 100 bpm, ưu tiên món nhẹ bụng, dễ tiêu, ít cay, ít dầu.\",\n"
                + "    \"Nếu cân nặng cao, ưu tiên món nhiều rau, đạm nạc, ít dầu để hỗ trợ kiểm soát cân nặng.\",\n"
                + "    \"Trích xuất món phù hợp nhất từ localMenu trước. Với món local, giữ nguyên id, name, image và recipe; chỉ tự viết reason.\",\n"
                + "    \"Nếu localMenu không có hoặc không đủ món phù hợp, tự tạo món mới ngay. Món tự tạo phải có id là generated, image là ic_launcher, và recipe chi tiết gồm Nguyên liệu và Các bước.\",\n"
                + "    \"Không chẩn đoán là bệnh; chỉ dùng từ an toàn như có thể phù hợp, nên hạn chế, gợi ý tham khảo.\",\n"
                + "    \"Trả về tổng cộng 3-5 recommendations, ưu tiên bổ sung các món chưa có trong alreadySelectedLocalDishes.\"\n"
                + "  ],\n"
                + "  \"outputSchema\": {\n"
                + "    \"analysis\": {\n"
                + "      \"summary\": \"Đánh giá ngắn gọn 1 câu về tình trạng và gợi ý ăn uống chung.\",\n"
                + "      \"shouldEat\": [\"luộc\", \"hấp\", \"nhiều rau\"],\n"
                + "      \"shouldLimit\": [\"chiên xào\", \"nhiều muối\"]\n"
                + "    },\n"
                + "    \"recommendations\": [\n"
                + "      {\n"
                + "        \"id\": \"id local hoặc generated\",\n"
                + "        \"name\": \"Tên món ăn\",\n"
                + "        \"image\": \"tên ảnh local hoặc ic_launcher\",\n"
                + "        \"reason\": \"Lý do món này có thể phù hợp với người dùng.\",\n"
                + "        \"suitableFor\": [\"nhãn 1\", \"nhãn 2\"],\n"
                + "        \"shouldLimit\": \"Lưu ý nhỏ khi ăn món này\",\n"
                + "        \"recipe\": \"Công thức nấu chi tiết\"\n"
                + "      }\n"
                + "    ]\n"
                + "  },\n"
                + "  \"responseFormat\": \"Chỉ trả về JSON thuần túy theo outputSchema, tuyệt đối không markdown.\"\n"
                + "}";
    }

    @NonNull
    private AiRecommendationResult parseAiResult(@NonNull String rawText) {
        List<HealthRecommendedFood> recommendations = new ArrayList<>();
        HealthAnalysisResult analysis = null;
        try {
            JSONObject root = new JSONObject(extractJsonPayload(rawText));
            JSONObject analysisObject = root.optJSONObject("analysis");
            if (analysisObject != null) {
                String summary = analysisObject.optString("summary", "").trim();
                if (!summary.isEmpty()) {
                    analysis = new HealthAnalysisResult(
                            summary,
                            new ArrayList<>(),
                            toStringList(analysisObject.optJSONArray("shouldEat")),
                            toStringList(analysisObject.optJSONArray("shouldLimit")),
                            "");
                }
            }
            JSONArray array = root.optJSONArray("recommendations");
            if (array == null) {
                return new AiRecommendationResult(analysis, recommendations);
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
                String id = item.optString("id", "generated").trim();
                FoodItem localFood = findLocalFood(id, name);
                if (localFood != null) {
                    recommendations.add(new HealthRecommendedFood(
                            localFood,
                            localFood.getName(),
                            item.optString("reason", "").trim(),
                            localFood.getSuitableFor(),
                            item.optString("shouldLimit", "").trim(),
                            localFood.getRecipe()));
                    continue;
                }

                String imageName = item.optString("image", "ic_launcher").trim();
                String recipe = normalizeRecipePayload(name, item.opt("recipe"));
                if (recipe.isEmpty()) {
                    recipe = item.optString("simpleRecipe", "").trim();
                }
                recommendations.add(new HealthRecommendedFood(
                        null,
                        id.isEmpty() ? "generated" : id,
                        imageName.isEmpty() ? "ic_launcher" : imageName,
                        name,
                        item.optString("reason", "").trim(),
                        toStringList(item.optJSONArray("suitableFor")),
                        item.optString("shouldLimit", "").trim(),
                        recipe));
            }
        } catch (Exception ignored) {
            return new AiRecommendationResult(null, new ArrayList<>());
        }
        return new AiRecommendationResult(analysis, recommendations);
    }

    @NonNull
    private static String normalizeRecipePayload(@NonNull String name, @Nullable Object rawRecipe) {
        if (rawRecipe == null || rawRecipe == JSONObject.NULL) {
            return "";
        }
        if (rawRecipe instanceof JSONObject) {
            return recipeObjectToMarkdown(name, (JSONObject) rawRecipe);
        }
        String recipe = String.valueOf(rawRecipe).trim();
        if (recipe.startsWith("{") && recipe.endsWith("}")) {
            try {
                return recipeObjectToMarkdown(name, new JSONObject(recipe));
            } catch (Exception ignored) {
                return recipe;
            }
        }
        return recipe;
    }

    @NonNull
    private static String recipeObjectToMarkdown(@NonNull String name, @NonNull JSONObject recipeObject) {
        StringBuilder builder = new StringBuilder();
        builder.append("### ").append(name).append('\n');
        builder.append("**Khẩu phần:** 2-3 người\n");

        JSONObject ingredients = recipeObject.optJSONObject("Nguyên liệu");
        if (ingredients == null) {
            ingredients = recipeObject.optJSONObject("nguyên liệu");
        }
        if (ingredients != null) {
            builder.append("**Nguyên liệu:**\n");
            JSONArray ingredientNames = ingredients.names();
            if (ingredientNames != null) {
                for (int index = 0; index < ingredientNames.length(); index++) {
                    String ingredientName = ingredientNames.optString(index, "").trim();
                    String amount = ingredients.optString(ingredientName, "").trim();
                    if (!ingredientName.isEmpty()) {
                        builder.append("- ").append(ingredientName);
                        if (!amount.isEmpty()) {
                            builder.append(' ').append(amount);
                        }
                        builder.append('\n');
                    }
                }
            }
        }

        JSONArray steps = recipeObject.optJSONArray("Các bước");
        if (steps == null) {
            steps = recipeObject.optJSONArray("Các bước thực hiện");
        }
        if (steps == null) {
            steps = recipeObject.optJSONArray("các bước");
        }
        if (steps != null) {
            builder.append("\n**Các bước thực hiện:**\n");
            for (int index = 0; index < steps.length(); index++) {
                String step = steps.optString(index, "").trim();
                if (!step.isEmpty()) {
                    builder.append(index + 1).append(". ").append(step).append('\n');
                }
            }
        }

        JSONArray tips = recipeObject.optJSONArray("Mẹo tối ưu");
        if (tips != null) {
            builder.append("\n**Mẹo tối ưu:**\n");
            for (int index = 0; index < tips.length(); index++) {
                String tip = tips.optString(index, "").trim();
                if (!tip.isEmpty()) {
                    builder.append("- ").append(tip).append('\n');
                }
            }
        }
        return builder.toString().trim();
    }

    @NonNull
    private JSONArray buildLocalMenuJson() {
        JSONArray array = new JSONArray();
        for (FoodItem food : foodJsonRepository.getFoods()) {
            try {
                JSONObject item = new JSONObject();
                item.put("id", food.getId());
                item.put("name", food.getName());
                item.put("image", food.getImage());
                item.put("suitableFor", new JSONArray(food.getSuitableFor()));
                item.put("recipe", food.getRecipe());
                array.put(item);
            } catch (Exception ignored) {
                // Skip malformed items so one bad dish cannot break AI fallback generation.
            }
        }
        return array;
    }

    @Nullable
    private FoodItem findLocalFood(@NonNull String id, @NonNull String name) {
        if (!id.isEmpty() && !"generated".equalsIgnoreCase(id)) {
            FoodItem byId = foodJsonRepository.findById(id);
            if (byId != null) {
                return byId;
            }
        }

        String normalizedName = canonicalizeText(name);
        for (FoodItem food : foodJsonRepository.getFoods()) {
            if (canonicalizeText(food.getName()).equals(normalizedName)) {
                return food;
            }
        }
        return null;
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

    private static final class AiRecommendationResult {
        @Nullable
        final HealthAnalysisResult analysis;
        @NonNull
        final List<HealthRecommendedFood> recommendations;

        AiRecommendationResult(
                @Nullable HealthAnalysisResult analysis,
                @NonNull List<HealthRecommendedFood> recommendations) {
            this.analysis = analysis;
            this.recommendations = recommendations;
        }
    }
}
