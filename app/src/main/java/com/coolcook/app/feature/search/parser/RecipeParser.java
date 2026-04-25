package com.coolcook.app.feature.search.parser;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.coolcook.app.feature.profile.data.HealthAnalyzer;
import com.coolcook.app.feature.search.model.ParsedRecipe;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public final class RecipeParser {

    private static final String[] SERVING_LABELS = {
            "**Khẩu phần:**",
            "**Kháº©u pháº§n:**",
            "**Kh?u ph?n:**"
    };
    private static final String[] INGREDIENT_LABELS = {
            "**Nguyên liệu:**",
            "**NguyÃªn liá»‡u:**",
            "**Nguyï¿½n li?u:**"
    };
    private static final String[] STEP_LABELS = {
            "**Các bước thực hiện:**",
            "**CÃ¡c bÆ°á»›c thá»±c hiá»‡n:**",
            "**Cï¿½c bu?c th?c hi?n:**"
    };
    private static final String[] TIP_LABELS = {
            "**Mẹo tối ưu:**",
            "**Máº¹o tá»‘i Æ°u:**",
            "**M?o t?i uu:**"
    };
    private static final Pattern COOK_MINUTE_PATTERN =
            Pattern.compile("(\\d+)(?:\\s*-\\s*(\\d+))?\\s*ph(?:ú|u|ï¿½)?t", Pattern.CASE_INSENSITIVE);

    private RecipeParser() {
    }

    @NonNull
    public static ParsedRecipe parse(@NonNull String recipe) {
        try {
            String serving = parseServing(recipe);
            String ingredientsBlock = extractBlock(recipe, INGREDIENT_LABELS, STEP_LABELS);
            String stepsBlock = extractBlock(recipe, STEP_LABELS, TIP_LABELS);
            String tipsBlock = extractBlock(recipe, TIP_LABELS, null);

            List<ParsedRecipe.Ingredient> ingredients = parseIngredients(ingredientsBlock);
            List<String> steps = parseNumberedLines(stepsBlock);
            List<String> tips = parseBulletLines(tipsBlock);
            boolean parsed = !serving.isEmpty() || !ingredients.isEmpty() || !steps.isEmpty() || !tips.isEmpty();
            return new ParsedRecipe(serving, ingredients, steps, tips, recipe, parsed);
        } catch (Exception error) {
            return new ParsedRecipe("", new ArrayList<>(), new ArrayList<>(), new ArrayList<>(), recipe, false);
        }
    }

    public static int inferCookTimeMinutes(@NonNull String recipe) {
        Matcher matcher = COOK_MINUTE_PATTERN.matcher(recipe.toLowerCase(Locale.ROOT));
        int total = 0;
        while (matcher.find()) {
            int first = parseIntSafely(matcher.group(1));
            int second = parseIntSafely(matcher.group(2));
            total += Math.max(first, second);
        }

        if (total <= 0) {
            return 30;
        }
        return Math.max(10, ((total + 4) / 5) * 5);
    }

    @NonNull
    private static String parseServing(@NonNull String recipe) {
        for (String line : recipe.split("\\R")) {
            String trimmed = line.trim();
            if (startsWithAny(trimmed, SERVING_LABELS)) {
                String label = matchedLabel(trimmed, SERVING_LABELS);
                return trimmed.substring(label.length()).trim();
            }
        }
        return "";
    }

    @NonNull
    private static String extractBlock(
            @NonNull String recipe,
            @NonNull String[] startLabels,
            @Nullable String[] endLabels) {
        int start = indexOfAny(recipe, startLabels);
        if (start < 0) {
            return "";
        }

        String startLabel = matchedLabel(recipe.substring(start), startLabels);
        int contentStart = start + startLabel.length();
        int end = endLabels == null ? recipe.length() : indexOfAny(recipe, endLabels, contentStart);
        if (end < 0) {
            end = recipe.length();
        }
        return recipe.substring(contentStart, end).trim();
    }

    @NonNull
    private static List<ParsedRecipe.Ingredient> parseIngredients(@NonNull String block) {
        List<ParsedRecipe.Ingredient> ingredients = new ArrayList<>();
        for (String line : block.split("\\R")) {
            String value = stripBullet(line);
            if (value.isEmpty()) {
                continue;
            }
            ingredients.add(splitIngredient(value));
        }
        return ingredients;
    }

    @NonNull
    private static ParsedRecipe.Ingredient splitIngredient(@NonNull String value) {
        int firstDigitIndex = -1;
        for (int index = 0; index < value.length(); index++) {
            if (Character.isDigit(value.charAt(index))) {
                firstDigitIndex = index;
                break;
            }
        }

        if (firstDigitIndex <= 0) {
            return new ParsedRecipe.Ingredient(value, "");
        }

        String name = value.substring(0, firstDigitIndex).trim();
        String amount = value.substring(firstDigitIndex).trim();
        return new ParsedRecipe.Ingredient(name.isEmpty() ? value : name, amount);
    }

    @NonNull
    private static List<String> parseNumberedLines(@NonNull String block) {
        List<String> lines = new ArrayList<>();
        for (String line : block.split("\\R")) {
            String trimmed = line.trim();
            if (trimmed.matches("^\\d+\\.\\s+.*")) {
                lines.add(trimmed.replaceFirst("^\\d+\\.\\s+", "").trim());
            }
        }
        return lines;
    }

    @NonNull
    private static List<String> parseBulletLines(@NonNull String block) {
        List<String> lines = new ArrayList<>();
        for (String line : block.split("\\R")) {
            String value = stripBullet(line);
            if (!value.isEmpty()) {
                lines.add(value);
            }
        }
        return lines;
    }

    @NonNull
    private static String stripBullet(@NonNull String line) {
        String trimmed = line.trim();
        if (!trimmed.startsWith("- ")) {
            return "";
        }
        return trimmed.substring(2).trim();
    }

    private static boolean startsWithAny(@NonNull String value, @NonNull String[] labels) {
        return matchedLabel(value, labels) != null;
    }

    @Nullable
    private static String matchedLabel(@NonNull String value, @NonNull String[] labels) {
        for (String label : labels) {
            if (value.startsWith(label)) {
                return label;
            }
        }

        String normalizedValue = HealthAnalyzer.normalize(value);
        for (String label : labels) {
            if (normalizedValue.startsWith(HealthAnalyzer.normalize(label))) {
                return label;
            }
        }
        return null;
    }

    private static int indexOfAny(@NonNull String value, @NonNull String[] labels) {
        return indexOfAny(value, labels, 0);
    }

    private static int indexOfAny(@NonNull String value, @NonNull String[] labels, int fromIndex) {
        int best = -1;
        for (String label : labels) {
            int index = value.indexOf(label, fromIndex);
            if (index >= 0 && (best < 0 || index < best)) {
                best = index;
            }
        }
        return best;
    }

    private static int parseIntSafely(@Nullable String value) {
        if (value == null || value.trim().isEmpty()) {
            return 0;
        }
        try {
            return Integer.parseInt(value.trim());
        } catch (NumberFormatException ignored) {
            return 0;
        }
    }
}
