package com.coolcook.app.feature.camera.ui;

import android.content.Context;
import android.content.Intent;
import android.os.Bundle;
import android.view.View;

import androidx.activity.EdgeToEdge;
import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.graphics.Insets;
import androidx.core.view.ViewCompat;
import androidx.core.view.WindowInsetsCompat;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.coolcook.app.R;
import com.coolcook.app.feature.camera.data.ScanSavedDishStore;
import com.coolcook.app.feature.camera.model.ScanDishItem;
import com.coolcook.app.feature.camera.ui.adapter.ScanDishSuggestionAdapter;

import java.util.List;

public class SavedScanDishesActivity extends AppCompatActivity {

    private ScanSavedDishStore scanSavedDishStore;
    private ScanDishSuggestionAdapter adapter;

    @NonNull
    public static Intent createIntent(@NonNull Context context) {
        return new Intent(context, SavedScanDishesActivity.class);
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        EdgeToEdge.enable(this);
        setContentView(R.layout.activity_saved_scan_dishes);

        scanSavedDishStore = new ScanSavedDishStore(this);
        adapter = new ScanDishSuggestionAdapter(new ScanDishSuggestionAdapter.DishActionListener() {
            @Override
            public void onDishClicked(@NonNull ScanDishItem item) {
                ScanDishRecipeBottomSheet.show(SavedScanDishesActivity.this, item);
            }

            @Override
            public void onSaveDishClicked(@NonNull ScanDishItem item) {
            }

            @Override
            public void onAddToJournalClicked(@NonNull ScanDishItem item) {
            }
        }, false);

        RecyclerView recyclerView = findViewById(R.id.rvSavedDishes);
        recyclerView.setLayoutManager(new LinearLayoutManager(this));
        recyclerView.setAdapter(adapter);

        findViewById(R.id.btnSavedDishesBack).setOnClickListener(v -> finish());
        applyInsets();
        renderSavedDishes();
    }

    @Override
    protected void onResume() {
        super.onResume();
        renderSavedDishes();
    }

    private void renderSavedDishes() {
        List<ScanDishItem> items = scanSavedDishStore.getSavedDishes();
        adapter.submitItems(items);
        findViewById(R.id.txtSavedDishesEmpty).setVisibility(items.isEmpty() ? View.VISIBLE : View.GONE);
    }

    private void applyInsets() {
        View root = findViewById(android.R.id.content);
        View topBar = findViewById(R.id.savedDishesTopBar);
        RecyclerView recyclerView = findViewById(R.id.rvSavedDishes);

        final int topBarLeft = topBar.getPaddingLeft();
        final int topBarTop = topBar.getPaddingTop();
        final int topBarRight = topBar.getPaddingRight();
        final int topBarBottom = topBar.getPaddingBottom();
        final int listLeft = recyclerView.getPaddingLeft();
        final int listTop = recyclerView.getPaddingTop();
        final int listRight = recyclerView.getPaddingRight();
        final int listBottom = recyclerView.getPaddingBottom();

        ViewCompat.setOnApplyWindowInsetsListener(root, (v, insets) -> {
            Insets systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars());
            topBar.setPadding(
                    topBarLeft + systemBars.left,
                    topBarTop + systemBars.top,
                    topBarRight + systemBars.right,
                    topBarBottom);
            recyclerView.setPadding(
                    listLeft + systemBars.left,
                    listTop,
                    listRight + systemBars.right,
                    listBottom + systemBars.bottom);
            return insets;
        });
    }
}
