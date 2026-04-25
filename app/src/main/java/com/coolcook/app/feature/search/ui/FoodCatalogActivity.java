package com.coolcook.app.feature.search.ui;

import android.content.Context;
import android.content.Intent;
import android.os.Bundle;
import android.text.Editable;
import android.text.TextWatcher;
import android.view.View;
import android.view.ViewGroup;
import android.view.inputmethod.EditorInfo;
import android.widget.EditText;
import android.widget.TextView;

import androidx.activity.EdgeToEdge;
import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.graphics.Insets;
import androidx.core.view.ViewCompat;
import androidx.core.view.WindowInsetsCompat;
import androidx.recyclerview.widget.GridLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.coolcook.app.R;
import com.coolcook.app.feature.search.data.FavoriteFoodStore;
import com.coolcook.app.feature.search.data.FoodJsonRepository;
import com.coolcook.app.feature.search.model.FoodCategory;
import com.coolcook.app.feature.search.model.FoodItem;
import com.coolcook.app.feature.search.model.ParsedRecipe;
import com.coolcook.app.feature.search.parser.RecipeParser;
import com.coolcook.app.feature.search.ui.adapter.FoodAdapter;
import com.coolcook.app.feature.search.util.SearchTextUtils;
import com.coolcook.app.core.navigation.HomeBottomNavigation;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;

public class FoodCatalogActivity extends AppCompatActivity {

    private static final String EXTRA_SHOW_FAVORITES_ONLY = "extra_show_favorites_only";

    private FoodJsonRepository repository;
    private FavoriteFoodStore favoriteFoodStore;
    private FoodAdapter adapter;
    private EditText edtSearch;
    private TextView chipAll;
    private TextView chipDry;
    private TextView chipSoup;
    private TextView txtEmpty;
    private FoodCategory selectedCategory;
    private String query = "";
    private boolean showFavoritesOnly;

    @NonNull
    public static Intent createIntent(@NonNull Context context) {
        return new Intent(context, FoodCatalogActivity.class);
    }

    @NonNull
    public static Intent createFavoritesIntent(@NonNull Context context) {
        Intent intent = createIntent(context);
        intent.putExtra(EXTRA_SHOW_FAVORITES_ONLY, true);
        return intent;
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        EdgeToEdge.enable(this);
        setContentView(R.layout.activity_food_catalog);

        showFavoritesOnly = getIntent().getBooleanExtra(EXTRA_SHOW_FAVORITES_ONLY, false);
        repository = new FoodJsonRepository(this);
        favoriteFoodStore = new FavoriteFoodStore(this);
        adapter = new FoodAdapter(favoriteFoodStore, this::openFoodDetail, this::toggleFoodFavorite);

        edtSearch = findViewById(R.id.edtFoodSearch);
        chipAll = findViewById(R.id.chipFoodAll);
        chipDry = findViewById(R.id.chipFoodDry);
        chipSoup = findViewById(R.id.chipFoodSoup);
        txtEmpty = findViewById(R.id.txtFoodEmpty);

        setupRecyclerView();
        setupSearch();
        setupFilterChips();
        HomeBottomNavigation.bind(this, HomeBottomNavigation.Tab.SEARCH, null, null);
        applyInsets();
        renderFoods();
    }

    @Override
    protected void onResume() {
        super.onResume();
        renderFoods();
    }

    private void setupRecyclerView() {
        RecyclerView recyclerView = findViewById(R.id.recyclerFoods);
        recyclerView.setLayoutManager(new GridLayoutManager(this, 2));
        recyclerView.setHasFixedSize(false);
        recyclerView.setAdapter(adapter);
    }

    private void setupSearch() {
        edtSearch.setImeOptions(EditorInfo.IME_ACTION_SEARCH);
        edtSearch.addTextChangedListener(new TextWatcher() {
            @Override
            public void beforeTextChanged(CharSequence s, int start, int count, int after) {
            }

            @Override
            public void onTextChanged(CharSequence s, int start, int before, int count) {
                query = s == null ? "" : s.toString();
                renderFoods();
            }

            @Override
            public void afterTextChanged(Editable s) {
            }
        });
    }

    private void setupFilterChips() {
        chipAll.setOnClickListener(v -> {
            selectedCategory = null;
            renderFoods();
        });
        chipDry.setOnClickListener(v -> {
            selectedCategory = FoodCategory.DRY;
            renderFoods();
        });
        chipSoup.setOnClickListener(v -> {
            selectedCategory = FoodCategory.SOUP;
            renderFoods();
        });
    }

    private void renderFoods() {
        if (repository == null || adapter == null) {
            return;
        }

        updateChipState();
        List<FoodItem> filteredFoods = new ArrayList<>();
        String normalizedQuery = SearchTextUtils.normalizeForSearch(query);

        for (FoodItem food : repository.getFoods()) {
            if (showFavoritesOnly && !favoriteFoodStore.isFavorite(food.getId())) {
                continue;
            }
            if (selectedCategory != null && food.getCategory() != selectedCategory) {
                continue;
            }
            if (!matchesQuery(food, normalizedQuery)) {
                continue;
            }
            filteredFoods.add(food);
        }

        Collections.sort(filteredFoods, favoriteFirstComparator());
        adapter.submitFoods(filteredFoods);
        txtEmpty.setVisibility(filteredFoods.isEmpty() ? View.VISIBLE : View.GONE);
        if (txtEmpty != null) {
            txtEmpty.setText(showFavoritesOnly
                    ? "Bạn chưa có món ăn yêu thích nào."
                    : "Chưa tìm thấy món phù hợp");
        }
    }

    private boolean matchesQuery(@NonNull FoodItem food, @NonNull String normalizedQuery) {
        if (normalizedQuery.isEmpty()) {
            return true;
        }

        if (SearchTextUtils.normalizeForSearch(food.getName()).contains(normalizedQuery)) {
            return true;
        }

        for (String suitable : food.getSuitableFor()) {
            if (SearchTextUtils.normalizeForSearch(suitable).contains(normalizedQuery)) {
                return true;
            }
        }

        ParsedRecipe parsedRecipe = RecipeParser.parse(food.getRecipe());
        for (ParsedRecipe.Ingredient ingredient : parsedRecipe.getIngredients()) {
            if (SearchTextUtils.normalizeForSearch(ingredient.getName()).contains(normalizedQuery)) {
                return true;
            }
        }

        return SearchTextUtils.normalizeForSearch(food.getRecipe()).contains(normalizedQuery);
    }

    @NonNull
    private Comparator<FoodItem> favoriteFirstComparator() {
        return (left, right) -> {
            boolean leftFavorite = favoriteFoodStore.isFavorite(left.getId());
            boolean rightFavorite = favoriteFoodStore.isFavorite(right.getId());
            if (leftFavorite != rightFavorite) {
                return leftFavorite ? -1 : 1;
            }
            return left.getName().toLowerCase(Locale.ROOT).compareTo(right.getName().toLowerCase(Locale.ROOT));
        };
    }

    private void updateChipState() {
        setChipSelected(chipAll, selectedCategory == null);
        setChipSelected(chipDry, selectedCategory == FoodCategory.DRY);
        setChipSelected(chipSoup, selectedCategory == FoodCategory.SOUP);
    }

    private void setChipSelected(@NonNull TextView chip, boolean selected) {
        chip.setSelected(selected);
        chip.setBackgroundResource(selected
                ? R.drawable.bg_food_filter_chip_selected
                : R.drawable.bg_food_filter_chip_unselected);
        chip.setTextColor(getColor(selected ? R.color.on_primary : R.color.on_surface_variant));
    }

    private void openFoodDetail(@NonNull FoodItem foodItem) {
        startActivity(FoodDetailActivity.createIntent(this, foodItem.getId()));
        overridePendingTransition(R.anim.slide_in_right_scale, R.anim.slide_out_left_scale);
    }

    private void toggleFoodFavorite(@NonNull FoodItem foodItem) {
        favoriteFoodStore.toggle(foodItem.getId());
        renderFoods();
    }

    private void applyInsets() {
        View root = findViewById(R.id.foodCatalogRoot);
        View content = findViewById(R.id.foodCatalogContent);
        View bottomNav = findViewById(R.id.homeBottomNav);
        final int left = content.getPaddingLeft();
        final int top = content.getPaddingTop();
        final int right = content.getPaddingRight();
        final int bottom = content.getPaddingBottom();
        final ViewGroup.MarginLayoutParams bottomNavParams =
                bottomNav == null ? null : (ViewGroup.MarginLayoutParams) bottomNav.getLayoutParams();
        final int navBaseBottom = bottomNavParams == null ? 0 : bottomNavParams.bottomMargin;

        ViewCompat.setOnApplyWindowInsetsListener(root, (v, insets) -> {
            Insets systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars());
            content.setPadding(
                    left + systemBars.left,
                    top + systemBars.top,
                    right + systemBars.right,
                    bottom);
            if (bottomNav != null) {
                ViewGroup.MarginLayoutParams updatedBottomNavParams =
                        (ViewGroup.MarginLayoutParams) bottomNav.getLayoutParams();
                updatedBottomNavParams.bottomMargin = navBaseBottom + systemBars.bottom;
                bottomNav.setLayoutParams(updatedBottomNavParams);
            }
            return insets;
        });
    }
}
