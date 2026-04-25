package com.coolcook.app.feature.social.ui;

import android.content.Context;
import android.content.Intent;
import android.graphics.Color;
import android.os.Bundle;
import android.view.View;
import android.view.ViewGroup;
import android.view.WindowManager;
import android.widget.TextView;

import androidx.activity.EdgeToEdge;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.graphics.Insets;
import androidx.core.view.ViewCompat;
import androidx.core.view.WindowInsetsCompat;
import androidx.recyclerview.widget.GridLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.coolcook.app.R;
import com.coolcook.app.feature.social.data.JournalFeedRepository;
import com.coolcook.app.feature.social.model.JournalFeedItem;
import com.coolcook.app.feature.social.ui.adapter.JournalHistoryGridAdapter;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.FirebaseUser;
import com.google.firebase.firestore.FirebaseFirestore;
import com.google.firebase.firestore.ListenerRegistration;

import java.util.List;

public class JournalHistoryGridActivity extends AppCompatActivity {

    private static final int GRID_LIMIT = 120;

    private View root;
    private View btnBack;
    private RecyclerView rvGrid;
    private TextView txtEmpty;
    private JournalHistoryGridAdapter adapter;
    private JournalFeedRepository repository;
    private ListenerRegistration listenerRegistration;

    public static Intent createIntent(@NonNull Context context) {
        return new Intent(context, JournalHistoryGridActivity.class);
    }

    @Override
    protected void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        EdgeToEdge.enable(this);
        getWindow().setStatusBarColor(Color.BLACK);
        getWindow().setNavigationBarColor(Color.BLACK);
        getWindow().setNavigationBarContrastEnforced(false);
        getWindow().setSoftInputMode(WindowManager.LayoutParams.SOFT_INPUT_ADJUST_NOTHING);
        setContentView(R.layout.activity_journal_history_grid);

        root = findViewById(R.id.journalHistoryGridRoot);
        btnBack = findViewById(R.id.btnJournalGridBack);
        rvGrid = findViewById(R.id.rvJournalHistoryGrid);
        txtEmpty = findViewById(R.id.txtJournalHistoryGridEmpty);
        repository = new JournalFeedRepository(FirebaseFirestore.getInstance());
        adapter = new JournalHistoryGridAdapter();
        rvGrid.setLayoutManager(new GridLayoutManager(this, 3));
        rvGrid.setAdapter(adapter);
        btnBack.setOnClickListener(v -> finish());
        applyInsets();
        listen();
    }

    @Override
    protected void onDestroy() {
        if (listenerRegistration != null) {
            listenerRegistration.remove();
            listenerRegistration = null;
        }
        super.onDestroy();
    }

    private void applyInsets() {
        final ViewGroup.MarginLayoutParams backParams = (ViewGroup.MarginLayoutParams) btnBack.getLayoutParams();
        final int backTop = backParams.topMargin;
        final int gridBottom = rvGrid.getPaddingBottom();
        ViewCompat.setOnApplyWindowInsetsListener(root, (v, insets) -> {
            Insets bars = insets.getInsets(WindowInsetsCompat.Type.systemBars());
            backParams.topMargin = backTop + bars.top;
            btnBack.setLayoutParams(backParams);
            rvGrid.setPadding(
                    rvGrid.getPaddingLeft(),
                    rvGrid.getPaddingTop(),
                    rvGrid.getPaddingRight(),
                    gridBottom + bars.bottom);
            return insets;
        });
        ViewCompat.requestApplyInsets(root);
    }

    private void listen() {
        FirebaseUser user = FirebaseAuth.getInstance().getCurrentUser();
        if (user == null) {
            showEmpty(true);
            return;
        }
        listenerRegistration = repository.listenToFeed(user.getUid(), GRID_LIMIT, new JournalFeedRepository.FeedCallback() {
            @Override
            public void onItems(@NonNull List<JournalFeedItem> items) {
                adapter.submitItems(items);
                showEmpty(items.isEmpty());
            }

            @Override
            public void onError(@NonNull Exception error) {
                showEmpty(true);
            }
        });
    }

    private void showEmpty(boolean empty) {
        txtEmpty.setVisibility(empty ? View.VISIBLE : View.GONE);
        rvGrid.setVisibility(empty ? View.GONE : View.VISIBLE);
    }
}
