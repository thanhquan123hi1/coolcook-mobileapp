package com.coolcook.app.ui.search;

import android.content.Context;
import android.content.Intent;
import android.os.Bundle;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.widget.LinearLayout;
import android.widget.TextView;

import androidx.activity.EdgeToEdge;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.app.AppCompatActivity;
import androidx.appcompat.widget.AppCompatImageView;
import androidx.core.content.res.ResourcesCompat;
import androidx.core.graphics.Insets;
import androidx.core.view.ViewCompat;
import androidx.core.view.WindowInsetsCompat;

import com.coolcook.app.R;
import com.coolcook.app.ui.search.data.FavoriteFoodStore;
import com.coolcook.app.ui.search.data.FoodJsonRepository;
import com.coolcook.app.ui.search.model.FoodItem;
import com.coolcook.app.ui.search.model.ParsedRecipe;
import com.coolcook.app.ui.search.parser.RecipeParser;
import com.google.android.material.chip.Chip;
import com.google.android.material.chip.ChipGroup;

import java.util.Locale;

public class FoodDetailActivity extends AppCompatActivity {

    private static final String EXTRA_FOOD_ID = "extra_food_id";

    private FavoriteFoodStore favoriteFoodStore;
    private FoodItem foodItem;
    private AppCompatImageView imgFoodHero;
    private TextView btnFavorite;

    @NonNull
    public static Intent createIntent(@NonNull Context context, @NonNull String foodId) {
        Intent intent = new Intent(context, FoodDetailActivity.class);
        intent.putExtra(EXTRA_FOOD_ID, foodId);
        return intent;
    }

    @Override
    protected void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        EdgeToEdge.enable(this);
        setContentView(R.layout.activity_food_detail);

        favoriteFoodStore = new FavoriteFoodStore(this);
        String foodId = getIntent().getStringExtra(EXTRA_FOOD_ID);
        foodItem = new FoodJsonRepository(this).findById(foodId == null ? "" : foodId);
        if (foodItem == null) {
            finish();
            return;
        }

        bindViews();
        bindFoodDetail();
        applyInsets();
    }

    @Override
    public void finish() {
        super.finish();
        overridePendingTransition(R.anim.slide_in_left_scale, R.anim.slide_out_right_scale);
    }

    private void bindViews() {
        imgFoodHero = findViewById(R.id.imgFoodHero);
        btnFavorite = findViewById(R.id.btnFoodFavorite);

        findViewById(R.id.btnFoodBack).setOnClickListener(v -> finish());
        btnFavorite.setOnClickListener(v -> toggleFavorite());
    }

    private void bindFoodDetail() {
        ParsedRecipe parsedRecipe = RecipeParser.parse(foodItem.getRecipe());

        imgFoodHero.setImageResource(foodItem.resolveImageResId(this));
        ((TextView) findViewById(R.id.txtFoodDetailTitle)).setText(foodItem.getName());
        ((TextView) findViewById(R.id.txtFoodDetailCategory)).setText(foodItem.getCategory().name());
        ((TextView) findViewById(R.id.txtFoodDetailTime)).setText(
                String.format(Locale.US, "%d MIN", foodItem.getCookTimeMinutes()));
        ((TextView) findViewById(R.id.txtFoodDetailServing)).setText(
                formatServing(parsedRecipe.getServing()));

        bindFavoriteState();
        bindSuitableChips((ChipGroup) findViewById(R.id.groupFoodSuitable));

        if (!parsedRecipe.isParsed()) {
            showRawRecipe(parsedRecipe.getRawText());
            return;
        }

        bindIngredients(parsedRecipe);
        bindSteps(parsedRecipe);
        bindTips(parsedRecipe);
    }

    @NonNull
    private String formatServing(@NonNull String serving) {
        if (serving.isEmpty()) {
            return "2-3 PORTIONS";
        }
        return serving.toUpperCase(Locale.forLanguageTag("vi-VN")).replace("NGƯỜI", "PORTIONS");
    }

    private void bindSuitableChips(@NonNull ChipGroup chipGroup) {
        chipGroup.removeAllViews();
        for (String suitable : foodItem.getSuitableFor()) {
            Chip chip = new Chip(this);
            chip.setText(suitable);
            chip.setCheckable(false);
            chip.setClickable(false);
            chip.setTextColor(getColor(R.color.on_surface_variant));
            chip.setChipBackgroundColorResource(R.color.surface_container_low);
            chip.setChipStrokeColorResource(R.color.home_card_border_soft);
            chip.setChipStrokeWidth(dpToPx(1));
            chip.setTextSize(12);
            chip.setTypeface(getResources().getFont(R.font.be_vietnam_pro_medium));
            chipGroup.addView(chip);
        }
    }

    private void bindIngredients(@NonNull ParsedRecipe parsedRecipe) {
        LinearLayout container = findViewById(R.id.listFoodIngredients);
        container.removeAllViews();

        for (ParsedRecipe.Ingredient ingredient : parsedRecipe.getIngredients()) {
            LinearLayout row = new LinearLayout(this);
            row.setOrientation(LinearLayout.HORIZONTAL);
            row.setGravity(Gravity.CENTER_VERTICAL);
            row.setPadding(0, dpToPx(8), 0, dpToPx(8));

            TextView name = createBodyText(ingredient.getName(), 14, R.color.on_surface);
            name.setTypeface(getResources().getFont(R.font.be_vietnam_pro_medium));
            row.addView(name, new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f));

            if (!ingredient.getAmount().isEmpty()) {
                TextView amount = createBodyText(ingredient.getAmount(), 12, R.color.home_accent);
                amount.setGravity(Gravity.CENTER);
                amount.setBackgroundResource(R.drawable.bg_food_ingredient_quantity);
                amount.setPadding(dpToPx(10), dpToPx(5), dpToPx(10), dpToPx(5));
                row.addView(amount, new LinearLayout.LayoutParams(
                        ViewGroup.LayoutParams.WRAP_CONTENT,
                        ViewGroup.LayoutParams.WRAP_CONTENT));
            }
            container.addView(row, new LinearLayout.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT,
                    ViewGroup.LayoutParams.WRAP_CONTENT));
        }
    }

    private void bindSteps(@NonNull ParsedRecipe parsedRecipe) {
        LinearLayout container = findViewById(R.id.listFoodSteps);
        container.removeAllViews();

        for (int index = 0; index < parsedRecipe.getSteps().size(); index++) {
            LinearLayout row = new LinearLayout(this);
            row.setOrientation(LinearLayout.HORIZONTAL);
            row.setGravity(Gravity.TOP);
            row.setPadding(0, dpToPx(8), 0, dpToPx(10));

            TextView number = createBodyText(String.valueOf(index + 1), 13, R.color.on_primary);
            number.setGravity(Gravity.CENTER);
            number.setTypeface(getResources().getFont(R.font.be_vietnam_pro_bold));
            number.setBackgroundResource(R.drawable.bg_food_step_number);
            row.addView(number, new LinearLayout.LayoutParams(dpToPx(30), dpToPx(30)));

            TextView step = createBodyText(parsedRecipe.getSteps().get(index), 14, R.color.on_surface_variant);
            step.setLineSpacing(dpToPx(2), 1f);
            LinearLayout.LayoutParams stepParams = new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f);
            stepParams.setMarginStart(dpToPx(12));
            row.addView(step, stepParams);

            container.addView(row, new LinearLayout.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT,
                    ViewGroup.LayoutParams.WRAP_CONTENT));
        }
    }

    private void bindTips(@NonNull ParsedRecipe parsedRecipe) {
        LinearLayout container = findViewById(R.id.listFoodTips);
        container.removeAllViews();

        for (String tip : parsedRecipe.getTips()) {
            TextView view = createBodyText("• " + tip, 14, R.color.on_surface_variant);
            view.setLineSpacing(dpToPx(2), 1f);
            view.setPadding(0, dpToPx(4), 0, dpToPx(4));
            container.addView(view, new LinearLayout.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT,
                    ViewGroup.LayoutParams.WRAP_CONTENT));
        }
    }

    private void showRawRecipe(@NonNull String rawRecipe) {
        findViewById(R.id.cardFoodIngredients).setVisibility(View.GONE);
        findViewById(R.id.cardFoodSteps).setVisibility(View.GONE);
        findViewById(R.id.cardFoodTips).setVisibility(View.GONE);

        TextView raw = findViewById(R.id.txtFoodRawRecipe);
        raw.setVisibility(View.VISIBLE);
        raw.setText(rawRecipe);
    }

    @NonNull
    private TextView createBodyText(@NonNull String text, int textSizeSp, int textColorRes) {
        TextView textView = new TextView(this);
        textView.setText(text);
        textView.setTextSize(textSizeSp);
        textView.setTextColor(getColor(textColorRes));
        textView.setTypeface(ResourcesCompat.getFont(this, R.font.be_vietnam_pro));
        textView.setIncludeFontPadding(true);
        return textView;
    }

    private void toggleFavorite() {
        favoriteFoodStore.toggle(foodItem.getId());
        bindFavoriteState();
    }

    private void bindFavoriteState() {
        boolean favorite = favoriteFoodStore.isFavorite(foodItem.getId());
        btnFavorite.setText("favorite");
        btnFavorite.setTextColor(getColor(favorite ? R.color.error : R.color.on_surface_variant));
        btnFavorite.setFontVariationSettings(favorite ? "'FILL' 1, 'wght' 600" : "'FILL' 0, 'wght' 600");
        btnFavorite.setContentDescription(favorite ? "Bỏ yêu thích" : "Yêu thích");
    }

    private int dpToPx(float dp) {
        return Math.round(dp * getResources().getDisplayMetrics().density);
    }

    private void applyInsets() {
        View root = findViewById(R.id.foodDetailRoot);
        View topControls = findViewById(R.id.foodDetailTopControls);
        View content = findViewById(R.id.foodDetailContent);

        final int controlsLeft = topControls.getPaddingLeft();
        final int controlsTop = topControls.getPaddingTop();
        final int controlsRight = topControls.getPaddingRight();
        final int controlsBottom = topControls.getPaddingBottom();
        final int contentLeft = content.getPaddingLeft();
        final int contentTop = content.getPaddingTop();
        final int contentRight = content.getPaddingRight();
        final int contentBottom = content.getPaddingBottom();

        ViewCompat.setOnApplyWindowInsetsListener(root, (v, insets) -> {
            Insets systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars());
            topControls.setPadding(
                    controlsLeft + systemBars.left,
                    controlsTop + systemBars.top,
                    controlsRight + systemBars.right,
                    controlsBottom);
            content.setPadding(
                    contentLeft + systemBars.left,
                    contentTop,
                    contentRight + systemBars.right,
                    contentBottom + systemBars.bottom);
            return insets;
        });
    }
}
