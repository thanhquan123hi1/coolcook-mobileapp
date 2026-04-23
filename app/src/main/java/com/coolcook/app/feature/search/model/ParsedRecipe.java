package com.coolcook.app.feature.search.model;

import androidx.annotation.NonNull;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

public class ParsedRecipe {

    @NonNull
    private final String serving;
    @NonNull
    private final List<Ingredient> ingredients;
    @NonNull
    private final List<String> steps;
    @NonNull
    private final List<String> tips;
    @NonNull
    private final String rawText;
    private final boolean parsed;

    public ParsedRecipe(
            @NonNull String serving,
            @NonNull List<Ingredient> ingredients,
            @NonNull List<String> steps,
            @NonNull List<String> tips,
            @NonNull String rawText,
            boolean parsed) {
        this.serving = serving;
        this.ingredients = new ArrayList<>(ingredients);
        this.steps = new ArrayList<>(steps);
        this.tips = new ArrayList<>(tips);
        this.rawText = rawText;
        this.parsed = parsed;
    }

    @NonNull
    public String getServing() {
        return serving;
    }

    @NonNull
    public List<Ingredient> getIngredients() {
        return Collections.unmodifiableList(ingredients);
    }

    @NonNull
    public List<String> getSteps() {
        return Collections.unmodifiableList(steps);
    }

    @NonNull
    public List<String> getTips() {
        return Collections.unmodifiableList(tips);
    }

    @NonNull
    public String getRawText() {
        return rawText;
    }

    public boolean isParsed() {
        return parsed;
    }

    public static class Ingredient {
        @NonNull
        private final String name;
        @NonNull
        private final String amount;

        public Ingredient(@NonNull String name, @NonNull String amount) {
            this.name = name;
            this.amount = amount;
        }

        @NonNull
        public String getName() {
            return name;
        }

        @NonNull
        public String getAmount() {
            return amount;
        }
    }
}
