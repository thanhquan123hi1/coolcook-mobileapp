package com.coolcook.app.feature.search.ui;

import android.os.Bundle;
import android.text.Editable;
import android.text.TextWatcher;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.view.inputmethod.EditorInfo;
import android.widget.EditText;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.core.graphics.Insets;
import androidx.core.view.ViewCompat;
import androidx.core.view.WindowInsetsCompat;
import androidx.fragment.app.Fragment;
import androidx.recyclerview.widget.GridLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.coolcook.app.R;
import com.coolcook.app.feature.search.data.FavoriteFoodStore;
import com.coolcook.app.feature.search.data.FoodJsonRepository;
import com.coolcook.app.feature.search.model.FoodCatalogFilter;
import com.coolcook.app.feature.search.model.FoodItem;
import com.coolcook.app.feature.search.model.ParsedRecipe;
import com.coolcook.app.feature.search.parser.RecipeParser;
import com.coolcook.app.feature.search.ui.adapter.FoodAdapter;
import com.coolcook.app.feature.search.util.SearchTextUtils;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;

public class FoodCatalogFragment extends Fragment {

    private FoodJsonRepository repository;
    private FavoriteFoodStore favoriteFoodStore;
    private FoodAdapter adapter;
    private View root;
    private View content;
    private EditText edtSearch;
    private TextView chipAll;
    private TextView chipDry;
    private TextView chipSoup;
    private TextView chipLowFat;
    private TextView chipStomach;
    private TextView chipProtein;
    private TextView txtEmpty;
    private FoodCatalogFilter selectedFilter = FoodCatalogFilter.ALL;
    private String query = "";

    @Nullable
    @Override
    public View onCreateView(
            @NonNull LayoutInflater inflater,
            @Nullable ViewGroup container,
            @Nullable Bundle savedInstanceState) {
        return inflater.inflate(R.layout.activity_food_catalog, container, false);
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);

        root = view.findViewById(R.id.foodCatalogRoot);
        content = view.findViewById(R.id.foodCatalogContent);
        repository = new FoodJsonRepository(requireContext());
        favoriteFoodStore = new FavoriteFoodStore(requireContext());
        adapter = new FoodAdapter(favoriteFoodStore, this::openFoodDetail, this::toggleFavorite);

        View embeddedBottomNav = view.findViewById(R.id.homeBottomNav);
        if (embeddedBottomNav != null) {
            embeddedBottomNav.setVisibility(View.GONE);
        }

        edtSearch = view.findViewById(R.id.edtFoodSearch);
        chipAll = view.findViewById(R.id.chipFoodAll);
        chipDry = view.findViewById(R.id.chipFoodDry);
        chipSoup = view.findViewById(R.id.chipFoodSoup);
        chipLowFat = view.findViewById(R.id.chipFoodLowFat);
        chipStomach = view.findViewById(R.id.chipFoodStomach);
        chipProtein = view.findViewById(R.id.chipFoodProtein);
        txtEmpty = view.findViewById(R.id.txtFoodEmpty);

        setupRecyclerView(view);
        setupSearch();
        setupFilterChips();
        applyInsets();
        renderFoods();
    }

    @Override
    public void onResume() {
        super.onResume();
        renderFoods();
    }

    public void applyFilter(@NonNull FoodCatalogFilter filter) {
        selectedFilter = filter;
        if (isAdded()) {
            renderFoods();
        }
    }

    private void setupRecyclerView(@NonNull View view) {
        RecyclerView recyclerView = view.findViewById(R.id.recyclerFoods);
        recyclerView.setLayoutManager(new GridLayoutManager(requireContext(), 2));
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
            selectedFilter = FoodCatalogFilter.ALL;
            renderFoods();
        });
        chipDry.setOnClickListener(v -> {
            selectedFilter = FoodCatalogFilter.DRY;
            renderFoods();
        });
        chipSoup.setOnClickListener(v -> {
            selectedFilter = FoodCatalogFilter.SOUP;
            renderFoods();
        });
        chipLowFat.setOnClickListener(v -> {
            selectedFilter = FoodCatalogFilter.LOW_FAT;
            renderFoods();
        });
        chipStomach.setOnClickListener(v -> {
            selectedFilter = FoodCatalogFilter.STOMACH;
            renderFoods();
        });
        chipProtein.setOnClickListener(v -> {
            selectedFilter = FoodCatalogFilter.PROTEIN;
            renderFoods();
        });
    }

    private void renderFoods() {
        if (repository == null || adapter == null || txtEmpty == null) {
            return;
        }

        updateChipState();
        List<FoodItem> filteredFoods = new ArrayList<>();
        String normalizedQuery = SearchTextUtils.normalizeForSearch(query);

        for (FoodItem food : repository.getFoods()) {
            if (!selectedFilter.matches(food)) {
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
        if (chipAll == null || chipDry == null || chipSoup == null
                || chipLowFat == null || chipStomach == null || chipProtein == null) {
            return;
        }
        setChipSelected(chipAll, selectedFilter == FoodCatalogFilter.ALL);
        setChipSelected(chipDry, selectedFilter == FoodCatalogFilter.DRY);
        setChipSelected(chipSoup, selectedFilter == FoodCatalogFilter.SOUP);
        setChipSelected(chipLowFat, selectedFilter == FoodCatalogFilter.LOW_FAT);
        setChipSelected(chipStomach, selectedFilter == FoodCatalogFilter.STOMACH);
        setChipSelected(chipProtein, selectedFilter == FoodCatalogFilter.PROTEIN);
    }

    private void setChipSelected(@NonNull TextView chip, boolean selected) {
        chip.setSelected(selected);
        chip.setBackgroundResource(selected
                ? R.drawable.bg_food_filter_chip_selected
                : R.drawable.bg_food_filter_chip_unselected);
        chip.setTextColor(requireContext().getColor(selected ? R.color.on_primary : R.color.on_surface_variant));
    }

    private void openFoodDetail(@NonNull FoodItem foodItem) {
        startActivity(FoodDetailActivity.createIntent(requireContext(), foodItem.getId()));
        requireActivity().overridePendingTransition(R.anim.slide_in_right_scale, R.anim.slide_out_left_scale);
    }

    private void toggleFavorite(@NonNull FoodItem foodItem) {
        favoriteFoodStore.toggle(foodItem.getId());
        renderFoods();
    }

    private void applyInsets() {
        if (root == null || content == null) {
            return;
        }

        final int left = content.getPaddingLeft();
        final int top = content.getPaddingTop();
        final int right = content.getPaddingRight();
        final int bottom = content.getPaddingBottom();

        ViewCompat.setOnApplyWindowInsetsListener(root, (v, insets) -> {
            Insets systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars());
            content.setPadding(
                    left + systemBars.left,
                    top + systemBars.top,
                    right + systemBars.right,
                    bottom);
            return insets;
        });
        ViewCompat.requestApplyInsets(root);
    }
}
