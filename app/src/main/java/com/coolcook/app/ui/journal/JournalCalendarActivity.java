package com.coolcook.app.ui.journal;

import android.content.Intent;
import android.os.Bundle;
import android.text.TextUtils;
import android.view.View;
import android.view.ViewGroup;
import android.view.animation.DecelerateInterpolator;
import android.view.animation.OvershootInterpolator;
import android.widget.TextView;

import androidx.activity.EdgeToEdge;
import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.graphics.Insets;
import androidx.core.view.ViewCompat;
import androidx.core.view.WindowInsetsCompat;
import androidx.lifecycle.ViewModelProvider;
import androidx.recyclerview.widget.GridLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.coolcook.app.R;
import com.coolcook.app.ui.journal.model.JournalDay;
import com.coolcook.app.ui.scan.ScanFoodActivity;
import com.google.android.material.floatingactionbutton.FloatingActionButton;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.FirebaseUser;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;

public class JournalCalendarActivity extends AppCompatActivity {

    private View root;
    private View topRow;
    private View btnBack;
    private View btnTopUtility;
    private View btnPrevMonth;
    private View btnNextMonth;
    private TextView txtMonth;
    private TextView txtEmptyState;
    private View progressLoading;
    private FloatingActionButton fabAddPhoto;
    private RecyclerView calendarRecycler;

    private JournalViewModel viewModel;
    private JournalCalendarAdapter calendarAdapter;
    @NonNull
    private List<JournalDay> currentDays = new ArrayList<>();
    private String latestUiMessage = "";

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        EdgeToEdge.enable(this);
        setContentView(R.layout.journal_calendar_screen);

        initViews();
        setupRecycler();
        setupActions();
        applyInsets();

        viewModel = new ViewModelProvider(this).get(JournalViewModel.class);
        observeViewModel();
        bindUser();
    }

    @Override
    protected void onResume() {
        super.onResume();
        if (viewModel != null) {
            viewModel.refreshMonth();
        }
    }

    private void initViews() {
        root = findViewById(R.id.journalCalendarRoot);
        topRow = findViewById(R.id.journalCalendarTopRow);
        btnBack = findViewById(R.id.btnJournalBack);
        btnTopUtility = findViewById(R.id.btnJournalTopUtility);
        btnPrevMonth = findViewById(R.id.btnJournalPrevMonth);
        btnNextMonth = findViewById(R.id.btnJournalNextMonth);
        txtMonth = findViewById(R.id.txtJournalCurrentMonth);
        txtEmptyState = findViewById(R.id.txtJournalCalendarEmptyState);
        progressLoading = findViewById(R.id.journalCalendarLoading);
        fabAddPhoto = findViewById(R.id.fabJournalAddPhoto);
        calendarRecycler = findViewById(R.id.rvJournalCalendarDays);
    }

    private void setupRecycler() {
        calendarAdapter = new JournalCalendarAdapter(this::openDayDetail);

        GridLayoutManager layoutManager = new GridLayoutManager(this, 7, RecyclerView.VERTICAL, false);
        calendarRecycler.setLayoutManager(layoutManager);
        calendarRecycler.setAdapter(calendarAdapter);
        calendarRecycler.setHasFixedSize(false);
        calendarRecycler.setItemAnimator(null);
    }

    private void setupActions() {
        if (btnBack != null) {
            btnBack.setOnClickListener(v -> finishWithSlideBack());
        }

        if (btnTopUtility != null) {
            btnTopUtility.setOnClickListener(v -> playBounceThen(v, this::openScanInJournalMode));
        }

        if (btnPrevMonth != null) {
            btnPrevMonth.setOnClickListener(v -> playBounceThen(v, () -> viewModel.goToPreviousMonth()));
        }

        if (btnNextMonth != null) {
            btnNextMonth.setOnClickListener(v -> playBounceThen(v, () -> viewModel.goToNextMonth()));
        }

        if (fabAddPhoto != null) {
            fabAddPhoto.setOnClickListener(v -> playBounceThen(v, this::openScanInJournalMode));
        }
    }

    private void observeViewModel() {
        viewModel.getMonthLabel().observe(this, monthTitle -> {
            if (txtMonth != null) {
                txtMonth.setText(monthTitle);
            }
        });

        viewModel.getCalendarDays().observe(this, days -> {
            currentDays = days == null ? new ArrayList<>() : new ArrayList<>(days);
            calendarAdapter.submitDays(currentDays);
            calendarRecycler.scheduleLayoutAnimation();
            updateEmptyState();
        });

        viewModel.isLoading().observe(this, isLoading -> {
            if (progressLoading != null) {
                progressLoading.setVisibility(Boolean.TRUE.equals(isLoading) ? View.VISIBLE : View.GONE);
            }
        });

        viewModel.getUiMessage().observe(this, message -> {
            latestUiMessage = message == null ? "" : message;
            updateEmptyState();
        });
    }

    private void bindUser() {
        FirebaseUser user = FirebaseAuth.getInstance().getCurrentUser();
        String uid = user == null ? "" : user.getUid();
        viewModel.setUserId(uid);
        viewModel.refreshMonth();
    }

    private void updateEmptyState() {
        if (txtEmptyState == null) {
            return;
        }

        boolean hasEntries = false;
        for (JournalDay day : currentDays) {
            if (day.isInCurrentMonth() && day.getTotalEntryCount() > 0) {
                hasEntries = true;
                break;
            }
        }

        if (hasEntries) {
            txtEmptyState.setVisibility(View.GONE);
            return;
        }

        txtEmptyState.setVisibility(View.VISIBLE);
        if (!TextUtils.isEmpty(latestUiMessage)) {
            txtEmptyState.setText(latestUiMessage);
        } else {
            txtEmptyState.setText(R.string.journal_empty_month);
        }
    }

    private void openDayDetail(@NonNull JournalDay day) {
        LocalDate date = day.getDate();
        if (date == null) {
            return;
        }
        Intent intent = JournalDayDetailActivity.createIntent(this, date);
        startActivity(intent);
        overridePendingTransition(R.anim.slide_in_right_scale, R.anim.slide_out_left_scale);
    }

    private void openScanInJournalMode() {
        startActivity(ScanFoodActivity.createJournalIntent(this));
        overridePendingTransition(R.anim.slide_in_left_scale, R.anim.slide_out_right_scale);
    }

    private void finishWithSlideBack() {
        finish();
        overridePendingTransition(R.anim.slide_in_left_scale, R.anim.slide_out_right_scale);
    }

    private void playBounceThen(@NonNull View target, @NonNull Runnable action) {
        target.setEnabled(false);
        target.animate().cancel();
        target.animate()
                .scaleX(0.9f)
                .scaleY(0.9f)
                .setDuration(90L)
                .setInterpolator(new DecelerateInterpolator())
                .withEndAction(() -> target.animate()
                        .scaleX(1f)
                        .scaleY(1f)
                        .setDuration(220L)
                        .setInterpolator(new OvershootInterpolator(0.95f))
                        .withEndAction(() -> {
                            target.setEnabled(true);
                            action.run();
                        })
                        .start())
                .start();
    }

    private void applyInsets() {
        if (root == null || topRow == null || fabAddPhoto == null) {
            return;
        }

        ViewGroup.MarginLayoutParams topRowParams = (ViewGroup.MarginLayoutParams) topRow.getLayoutParams();
        final int baseTopMargin = topRowParams.topMargin;

        ViewGroup.MarginLayoutParams fabParams = (ViewGroup.MarginLayoutParams) fabAddPhoto.getLayoutParams();
        final int baseFabBottom = fabParams.bottomMargin;
        final int baseFabEnd = fabParams.rightMargin;

        ViewCompat.setOnApplyWindowInsetsListener(root, (v, insets) -> {
            Insets bars = insets.getInsets(WindowInsetsCompat.Type.systemBars());
            v.setPadding(bars.left, 0, bars.right, 0);

            ViewGroup.MarginLayoutParams updatedTopRowParams = (ViewGroup.MarginLayoutParams) topRow.getLayoutParams();
            updatedTopRowParams.topMargin = baseTopMargin + bars.top;
            topRow.setLayoutParams(updatedTopRowParams);

            ViewGroup.MarginLayoutParams updatedFabParams = (ViewGroup.MarginLayoutParams) fabAddPhoto.getLayoutParams();
            updatedFabParams.bottomMargin = baseFabBottom + bars.bottom;
            updatedFabParams.rightMargin = baseFabEnd + bars.right;
            fabAddPhoto.setLayoutParams(updatedFabParams);

            return insets;
        });
        ViewCompat.requestApplyInsets(root);
    }
}
