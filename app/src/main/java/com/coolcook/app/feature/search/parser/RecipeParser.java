package com.coolcook.app.feature.search.parser;

import androidx.annotation.NonNull;

import com.coolcook.app.feature.search.model.ParsedRecipe;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public final class RecipeParser {

    private static final String SERVING_LABEL = "**Khẩu phần:**";
    private static final String INGREDIENT_LABEL = "**Nguyên liệu:**";
    private static final String STEP_LABEL = "**Các bước thực hiện:**";
    private static final String TIP_LABEL = "**Mẹo tối ưu:**";
    private static final Pattern COOK_MINUTE_PATTERN =
            Pattern.compile("(\\d+)(?:\\s*-\\s*(\\d+))?\\s*phút", Pattern.CASE_INSENSITIVE);

    private RecipeParser() {
    }

    @NonNull
    public static ParsedRecipe parse(@NonNull String recipe) {
        try {
            String serving = parseServing(recipe);
            List<ParsedRecipe.Ingredient> ingredients = parseIngredients(extractBlock(recipe, INGREDIENT_LABEL, STEP_LABEL));
            List<String> steps = parseNumberedLines(extractBlock(recipe, STEP_LABEL, TIP_LABEL));
            List<String> tips = parseBulletLines(extractBlock(recipe, TIP_LABEL, null));
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
            if (trimmed.startsWith(SERVING_LABEL)) {
                return trimmed.substring(SERVING_LABEL.length()).trim();
            }
        }
        return "";
    }

    @NonNull
    private static String extractBlock(@NonNull String recipe, @NonNull String startLabel, String endLabel) {
        int start = recipe.indexOf(startLabel);
        if (start < 0) {
            return "";
        }

        int contentStart = start + startLabel.length();
        int end = endLabel == null ? recipe.length() : recipe.indexOf(endLabel, contentStart);
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

    private static int parseIntSafely(String value) {
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
