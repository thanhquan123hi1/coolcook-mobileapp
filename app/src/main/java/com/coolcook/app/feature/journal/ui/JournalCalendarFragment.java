package com.coolcook.app.feature.journal.ui;

import android.content.Intent;
import android.os.Bundle;
import android.text.TextUtils;
import android.view.Gravity;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.view.animation.DecelerateInterpolator;
import android.view.animation.OvershootInterpolator;
import android.widget.LinearLayout;
import android.widget.NumberPicker;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.core.graphics.Insets;
import androidx.core.view.ViewCompat;
import androidx.core.view.WindowInsetsCompat;
import androidx.fragment.app.Fragment;
import androidx.lifecycle.ViewModelProvider;
import androidx.recyclerview.widget.GridLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.coolcook.app.R;
import com.coolcook.app.core.util.ActivityTransitionUtils;
import com.coolcook.app.feature.journal.model.JournalDay;
import com.coolcook.app.feature.journal.ui.adapter.JournalCalendarAdapter;
import com.coolcook.app.feature.camera.ui.ScanFoodActivity;
import com.google.android.material.dialog.MaterialAlertDialogBuilder;
import com.google.android.material.floatingactionbutton.FloatingActionButton;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.FirebaseUser;

import java.time.YearMonth;
import java.util.ArrayList;
import java.util.List;

public class JournalCalendarFragment extends Fragment {

    private View root;
    private TextView txtTitle;
    private View bottomNav;
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

    @Nullable
    @Override
    public View onCreateView(
            @NonNull LayoutInflater inflater,
            @Nullable ViewGroup container,
            @Nullable Bundle savedInstanceState) {
        return inflater.inflate(R.layout.journal_calendar_screen, container, false);
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);

        initViews(view);
        setupRecycler();
        setupActions();
        applyInsets();

        viewModel = new ViewModelProvider(this).get(JournalViewModel.class);
        observeViewModel();
        bindUser();
    }

    @Override
    public void onResume() {
        super.onResume();
        if (viewModel != null) {
            viewModel.refreshMonth();
        }
    }

    private void initViews(@NonNull View view) {
        root = view.findViewById(R.id.journalCalendarRoot);
        txtTitle = view.findViewById(R.id.txtJournalCalendarTitle);
        bottomNav = view.findViewById(R.id.homeBottomNav);
        btnPrevMonth = view.findViewById(R.id.btnJournalPrevMonth);
        btnNextMonth = view.findViewById(R.id.btnJournalNextMonth);
        txtMonth = view.findViewById(R.id.txtJournalCurrentMonth);
        txtEmptyState = view.findViewById(R.id.txtJournalCalendarEmptyState);
        progressLoading = view.findViewById(R.id.journalCalendarLoading);
        fabAddPhoto = view.findViewById(R.id.fabJournalAddPhoto);
        calendarRecycler = view.findViewById(R.id.rvJournalCalendarDays);

        if (bottomNav != null) {
            bottomNav.setVisibility(View.GONE);
        }
    }

    private void setupRecycler() {
        calendarAdapter = new JournalCalendarAdapter(this::openDayDetail);

        GridLayoutManager layoutManager = new GridLayoutManager(requireContext(), 7, RecyclerView.VERTICAL, false);
        calendarRecycler.setLayoutManager(layoutManager);
        calendarRecycler.setAdapter(calendarAdapter);
        calendarRecycler.setHasFixedSize(false);
        calendarRecycler.setItemAnimator(null);
    }

    private void setupActions() {
        if (btnPrevMonth != null) {
            btnPrevMonth.setOnClickListener(v -> playBounceThen(v, () -> viewModel.goToPreviousMonth()));
        }

        if (btnNextMonth != null) {
            btnNextMonth.setOnClickListener(v -> playBounceThen(v, () -> viewModel.goToNextMonth()));
        }

        if (txtMonth != null) {
            txtMonth.setOnClickListener(v -> showMonthYearPicker());
        }

        if (fabAddPhoto != null) {
            fabAddPhoto.setOnClickListener(v -> playBounceThen(v, this::openScanInJournalMode));
        }
    }

    private void observeViewModel() {
        viewModel.getMonthLabel().observe(getViewLifecycleOwner(), monthTitle -> {
            if (txtMonth != null) {
                txtMonth.setText(monthTitle);
            }
        });

        viewModel.getCalendarDays().observe(getViewLifecycleOwner(), days -> {
            currentDays = days == null ? new ArrayList<>() : new ArrayList<>(days);
            calendarAdapter.submitDays(currentDays);
            calendarRecycler.scheduleLayoutAnimation();
            updateEmptyState();
        });

        viewModel.isLoading().observe(getViewLifecycleOwner(), isLoading -> {
            if (progressLoading != null) {
                progressLoading.setVisibility(Boolean.TRUE.equals(isLoading) ? View.VISIBLE : View.GONE);
            }
        });

        viewModel.getUiMessage().observe(getViewLifecycleOwner(), message -> {
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

    private void showMonthYearPicker() {
        if (viewModel == null) {
            return;
        }

        YearMonth selected = viewModel.getCurrentMonth().getValue();
        if (selected == null) {
            selected = YearMonth.now();
        }

        int currentYear = YearMonth.now().getYear();
        int minYear = 2020;
        int maxYear = currentYear + 2;

        NumberPicker monthPicker = new NumberPicker(requireContext());
        monthPicker.setMinValue(1);
        monthPicker.setMaxValue(12);
        monthPicker.setDisplayedValues(new String[]{
                "Thang 1", "Thang 2", "Thang 3", "Thang 4", "Thang 5", "Thang 6",
                "Thang 7", "Thang 8", "Thang 9", "Thang 10", "Thang 11", "Thang 12"
        });
        monthPicker.setValue(selected.getMonthValue());
        monthPicker.setWrapSelectorWheel(true);

        NumberPicker yearPicker = new NumberPicker(requireContext());
        yearPicker.setMinValue(minYear);
        yearPicker.setMaxValue(maxYear);
        yearPicker.setValue(Math.max(minYear, Math.min(maxYear, selected.getYear())));
        yearPicker.setWrapSelectorWheel(false);

        LinearLayout pickerRow = new LinearLayout(requireContext());
        pickerRow.setOrientation(LinearLayout.HORIZONTAL);
        pickerRow.setGravity(Gravity.CENTER);
        int horizontalPadding = getResources().getDimensionPixelSize(R.dimen.journal_calendar_card_padding_horizontal);
        pickerRow.setPadding(horizontalPadding, horizontalPadding, horizontalPadding, 0);
        pickerRow.addView(monthPicker, new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f));
        pickerRow.addView(yearPicker, new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f));

        new MaterialAlertDialogBuilder(requireContext())
                .setTitle("Chon thang")
                .setView(pickerRow)
                .setNegativeButton("Huy", null)
                .setPositiveButton("Xong", (dialog, which) ->
                        viewModel.setCurrentMonth(YearMonth.of(yearPicker.getValue(), monthPicker.getValue())))
                .show();
    }

    private void openDayDetail(@NonNull JournalDay day) {
        if (day.getDate() == null) {
            return;
        }
        Intent intent = JournalDayDetailActivity.createIntent(requireContext(), day.getDate());
        startActivity(intent);
        ActivityTransitionUtils.applyOpenTransition(
                requireActivity(),
                R.anim.slide_in_right_scale,
                R.anim.slide_out_left_scale);
    }

    private void openScanInJournalMode() {
        startActivity(ScanFoodActivity.createJournalIntent(requireContext()));
        ActivityTransitionUtils.applyOpenTransition(
                requireActivity(),
                R.anim.slide_in_left_scale,
                R.anim.slide_out_right_scale);
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
        if (root == null || txtTitle == null) {
            return;
        }

        ViewGroup.MarginLayoutParams titleParams = (ViewGroup.MarginLayoutParams) txtTitle.getLayoutParams();
        final int baseTopMargin = titleParams.topMargin;

        final ViewGroup.MarginLayoutParams fabParams = fabAddPhoto == null
                ? null
                : (ViewGroup.MarginLayoutParams) fabAddPhoto.getLayoutParams();
        final int baseFabEnd = fabParams == null ? 0 : fabParams.rightMargin;

        ViewCompat.setOnApplyWindowInsetsListener(root, (v, insets) -> {
            Insets bars = insets.getInsets(WindowInsetsCompat.Type.systemBars());
            v.setPadding(bars.left, 0, bars.right, 0);

            ViewGroup.MarginLayoutParams updatedTitleParams = (ViewGroup.MarginLayoutParams) txtTitle.getLayoutParams();
            updatedTitleParams.topMargin = baseTopMargin + bars.top;
            txtTitle.setLayoutParams(updatedTitleParams);

            if (fabAddPhoto != null) {
                ViewGroup.MarginLayoutParams updatedFabParams =
                        (ViewGroup.MarginLayoutParams) fabAddPhoto.getLayoutParams();
                updatedFabParams.rightMargin = baseFabEnd + bars.right;
                fabAddPhoto.setLayoutParams(updatedFabParams);
            }

            return insets;
        });
        ViewCompat.requestApplyInsets(root);
    }
}
