package com.coolcook.app.feature.journal.ui;

import android.animation.ArgbEvaluator;
import android.animation.AnimatorSet;
import android.animation.ObjectAnimator;
import android.animation.PropertyValuesHolder;
import android.animation.ValueAnimator;
import android.content.Intent;
import android.content.res.ColorStateList;
import android.os.Bundle;
import android.text.TextUtils;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.view.animation.AccelerateInterpolator;
import android.view.animation.DecelerateInterpolator;
import android.view.animation.OvershootInterpolator;
import android.widget.LinearLayout;
import android.widget.NumberPicker;
import android.widget.TextView;

import androidx.activity.EdgeToEdge;
import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;
import androidx.appcompat.widget.AppCompatImageView;
import androidx.core.content.ContextCompat;
import androidx.core.graphics.Insets;
import androidx.core.view.ViewCompat;
import androidx.core.view.WindowInsetsCompat;
import androidx.lifecycle.ViewModelProvider;
import androidx.recyclerview.widget.GridLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.coolcook.app.R;
import com.coolcook.app.feature.journal.model.JournalDay;
import com.coolcook.app.feature.journal.ui.adapter.JournalCalendarAdapter;
import com.coolcook.app.core.navigation.HomeBottomNavigation;
import com.coolcook.app.feature.home.ui.HomeActivity;
import com.coolcook.app.feature.camera.ui.ScanFoodActivity;
import com.google.android.material.dialog.MaterialAlertDialogBuilder;
import com.google.android.material.floatingactionbutton.FloatingActionButton;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.FirebaseUser;

import java.time.YearMonth;
import java.util.ArrayList;
import java.util.List;

public class JournalCalendarActivity extends AppCompatActivity {

    private static final long NAV_TINT_ANIMATION_DURATION = 200L;
    private static final long NAV_ICON_ANIMATION_DURATION = 260L;
    private static final long NAV_TAP_ANIMATION_DURATION = 190L;
    private static final long NAV_TAP_ANIMATION_PRESS_DURATION = 70L;
    private static final long NAV_NAVIGATION_DELAY_MS = 120L;
    private static final float NAV_ACTIVE_ICON_SCALE = 1.14f;
    private static final float NAV_INACTIVE_ICON_SCALE = 1f;
    private static final float NAV_ICON_BOUNCE_COMPRESS_SCALE = 0.91f;
    private static final float NAV_ICON_BOUNCE_REBOUND_SCALE = 1.05f;
    private static final float NAV_TAP_COMPRESS_FACTOR = 0.9f;
    private static final float NAV_TAP_REBOUND_FACTOR = 1.04f;

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
    private AppCompatImageView navIconHome;
    private AppCompatImageView navIconSearch;
    private AppCompatImageView navIconHistory;
    private AppCompatImageView navIconProfile;
    private TextView navLabelHome;
    private TextView navLabelSearch;
    private TextView navLabelHistory;
    private TextView navLabelProfile;
    private View navCameraButton;

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
        setupBottomNavigation();
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
        txtTitle = findViewById(R.id.txtJournalCalendarTitle);
        bottomNav = findViewById(R.id.homeBottomNav);
        btnPrevMonth = findViewById(R.id.btnJournalPrevMonth);
        btnNextMonth = findViewById(R.id.btnJournalNextMonth);
        txtMonth = findViewById(R.id.txtJournalCurrentMonth);
        txtEmptyState = findViewById(R.id.txtJournalCalendarEmptyState);
        progressLoading = findViewById(R.id.journalCalendarLoading);
        fabAddPhoto = findViewById(R.id.fabJournalAddPhoto);
        calendarRecycler = findViewById(R.id.rvJournalCalendarDays);
        navIconHome = findViewById(R.id.homeNavIconHome);
        navIconSearch = findViewById(R.id.homeNavIconSearch);
        navIconHistory = findViewById(R.id.homeNavIconHistory);
        navIconProfile = findViewById(R.id.homeNavIconProfile);
        navLabelHome = findViewById(R.id.homeNavLabelHome);
        navLabelSearch = findViewById(R.id.homeNavLabelSearch);
        navLabelHistory = findViewById(R.id.homeNavLabelHistory);
        navLabelProfile = findViewById(R.id.homeNavLabelProfile);
        navCameraButton = findViewById(R.id.homeNavCameraButton);
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

    private void setupBottomNavigation() {
        HomeBottomNavigation.bind(this, HomeBottomNavigation.Tab.HISTORY, null, null);
    }

    private void updateBottomNavigationState() {
        int activeColor = ContextCompat.getColor(this, R.color.primary);
        int inactiveColor = ContextCompat.getColor(this, R.color.home_nav_inactive);

        applyNavItemState(navIconHome, navLabelHome, false, activeColor, inactiveColor);
        applyNavItemState(navIconSearch, navLabelSearch, false, activeColor, inactiveColor);
        applyNavItemState(navIconHistory, navLabelHistory, true, activeColor, inactiveColor);
        applyNavItemState(navIconProfile, navLabelProfile, false, activeColor, inactiveColor);
    }

    private void applyNavItemState(
            AppCompatImageView icon,
            TextView label,
            boolean active,
            int activeColor,
            int inactiveColor) {
        int targetColor = active ? activeColor : inactiveColor;
        if (icon != null) {
            icon.setSelected(active);
            animateIconTint(icon, targetColor);
            animateIconTransform(icon, active);
        }
        if (label != null) {
            animateLabelColor(label, targetColor);
        }
    }

    private void animateLabelColor(TextView label, int targetColor) {
        int startColor = label.getCurrentTextColor();
        if (startColor == targetColor) {
            label.setTextColor(targetColor);
            return;
        }

        ValueAnimator tintAnimator = ValueAnimator.ofObject(new ArgbEvaluator(), startColor, targetColor);
        tintAnimator.setDuration(NAV_TINT_ANIMATION_DURATION);
        tintAnimator.setInterpolator(new DecelerateInterpolator());
        tintAnimator.addUpdateListener(animation -> label.setTextColor((int) animation.getAnimatedValue()));
        tintAnimator.start();
    }

    private void animateIconTint(@NonNull AppCompatImageView icon, int targetColor) {
        ColorStateList tintList = icon.getImageTintList();
        int startColor = tintList != null ? tintList.getDefaultColor() : targetColor;
        if (startColor == targetColor) {
            icon.setImageTintList(ColorStateList.valueOf(targetColor));
            return;
        }

        ValueAnimator tintAnimator = ValueAnimator.ofObject(new ArgbEvaluator(), startColor, targetColor);
        tintAnimator.setDuration(NAV_TINT_ANIMATION_DURATION);
        tintAnimator.setInterpolator(new DecelerateInterpolator());
        tintAnimator.addUpdateListener(animation ->
                icon.setImageTintList(ColorStateList.valueOf((int) animation.getAnimatedValue())));
        tintAnimator.start();
    }

    private void animateIconTransform(@NonNull AppCompatImageView icon, boolean active) {
        float targetScale = active ? NAV_ACTIVE_ICON_SCALE : NAV_INACTIVE_ICON_SCALE;
        float targetLift = active ? -getResources().getDimension(R.dimen.home_bottom_nav_icon_lift) : 0f;
        float startScaleX = icon.getScaleX();
        float startScaleY = icon.getScaleY();
        float startLift = icon.getTranslationY();

        float compressScale = active
                ? NAV_ICON_BOUNCE_COMPRESS_SCALE
                : Math.min(startScaleX, startScaleY) * 1.01f;
        float reboundScale = active ? NAV_ICON_BOUNCE_REBOUND_SCALE : 1.01f;
        float compressLift = active ? targetLift * 0.4f : startLift * 0.25f;
        float reboundLift = active
                ? targetLift - getResources().getDimension(R.dimen.home_bottom_nav_bounce_lift_extra)
                : 0f;

        icon.animate().cancel();

        ObjectAnimator pressAnimator = ObjectAnimator.ofPropertyValuesHolder(icon,
                PropertyValuesHolder.ofFloat(View.SCALE_X, startScaleX, compressScale),
                PropertyValuesHolder.ofFloat(View.SCALE_Y, startScaleY, compressScale),
                PropertyValuesHolder.ofFloat(View.TRANSLATION_Y, startLift, compressLift));
        pressAnimator.setDuration(NAV_TAP_ANIMATION_PRESS_DURATION);
        pressAnimator.setInterpolator(new AccelerateInterpolator());

        ObjectAnimator settleAnimator = ObjectAnimator.ofPropertyValuesHolder(icon,
                PropertyValuesHolder.ofFloat(View.SCALE_X, compressScale, reboundScale, targetScale),
                PropertyValuesHolder.ofFloat(View.SCALE_Y, compressScale, reboundScale, targetScale),
                PropertyValuesHolder.ofFloat(View.TRANSLATION_Y, compressLift, reboundLift, targetLift));
        settleAnimator.setDuration(NAV_ICON_ANIMATION_DURATION - NAV_TAP_ANIMATION_PRESS_DURATION);
        settleAnimator.setInterpolator(active
                ? new OvershootInterpolator(0.8f)
                : new DecelerateInterpolator());

        AnimatorSet iconAnimator = new AnimatorSet();
        iconAnimator.playSequentially(pressAnimator, settleAnimator);
        iconAnimator.start();
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

        NumberPicker monthPicker = new NumberPicker(this);
        monthPicker.setMinValue(1);
        monthPicker.setMaxValue(12);
        monthPicker.setDisplayedValues(new String[]{
                "Tháng 1", "Tháng 2", "Tháng 3", "Tháng 4", "Tháng 5", "Tháng 6",
                "Tháng 7", "Tháng 8", "Tháng 9", "Tháng 10", "Tháng 11", "Tháng 12"
        });
        monthPicker.setValue(selected.getMonthValue());
        monthPicker.setWrapSelectorWheel(true);

        NumberPicker yearPicker = new NumberPicker(this);
        yearPicker.setMinValue(minYear);
        yearPicker.setMaxValue(maxYear);
        yearPicker.setValue(Math.max(minYear, Math.min(maxYear, selected.getYear())));
        yearPicker.setWrapSelectorWheel(false);

        LinearLayout pickerRow = new LinearLayout(this);
        pickerRow.setOrientation(LinearLayout.HORIZONTAL);
        pickerRow.setGravity(Gravity.CENTER);
        int horizontalPadding = getResources().getDimensionPixelSize(R.dimen.journal_calendar_card_padding_horizontal);
        pickerRow.setPadding(horizontalPadding, horizontalPadding, horizontalPadding, 0);
        pickerRow.addView(monthPicker, new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f));
        pickerRow.addView(yearPicker, new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f));

        new MaterialAlertDialogBuilder(this)
                .setTitle("Chọn tháng")
                .setView(pickerRow)
                .setNegativeButton("Hủy", null)
                .setPositiveButton("Xong", (dialog, which) ->
                        viewModel.setCurrentMonth(YearMonth.of(yearPicker.getValue(), monthPicker.getValue())))
                .show();
    }

    private void openDayDetail(@NonNull JournalDay day) {
        if (day.getDate() == null) {
            return;
        }
        Intent intent = JournalDayDetailActivity.createIntent(this, day.getDate());
        startActivity(intent);
        overridePendingTransition(R.anim.slide_in_right_scale, R.anim.slide_out_left_scale);
    }

    private void openScanInJournalMode() {
        startActivity(ScanFoodActivity.createJournalIntent(this));
        overridePendingTransition(R.anim.slide_in_left_scale, R.anim.slide_out_right_scale);
    }

    private void openHomeFromNavigation(boolean openProfile, @NonNull View tapTarget) {
        playTapFeedback(tapTarget);
        tapTarget.postDelayed(() -> {
            Intent intent = new Intent(this, HomeActivity.class);
            intent.addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP | Intent.FLAG_ACTIVITY_SINGLE_TOP);
            if (openProfile) {
                intent.putExtra(HomeActivity.EXTRA_OPEN_TAB, HomeActivity.EXTRA_TAB_PROFILE);
            }
            startActivity(intent);
            overridePendingTransition(R.anim.slide_in_left_scale, R.anim.slide_out_right_scale);
        }, NAV_NAVIGATION_DELAY_MS);
    }

    private void finishWithSlideBack() {
        finish();
        overridePendingTransition(R.anim.slide_in_left_scale, R.anim.slide_out_right_scale);
    }

    private void playTapFeedback(View target) {
        if (target == null) {
            return;
        }

        float startScaleX = target.getScaleX();
        float startScaleY = target.getScaleY();
        float startTranslationY = target.getTranslationY();
        float compressedScaleX = startScaleX * NAV_TAP_COMPRESS_FACTOR;
        float compressedScaleY = startScaleY * NAV_TAP_COMPRESS_FACTOR;
        float reboundScaleX = startScaleX * NAV_TAP_REBOUND_FACTOR;
        float reboundScaleY = startScaleY * NAV_TAP_REBOUND_FACTOR;
        float pressedTranslationY = startTranslationY
                + getResources().getDimension(R.dimen.home_bottom_nav_tap_press_offset);
        float reboundTranslationY = startTranslationY
                - getResources().getDimension(R.dimen.home_bottom_nav_tap_rebound_offset);

        target.animate().cancel();

        ObjectAnimator pressAnimator = ObjectAnimator.ofPropertyValuesHolder(target,
                PropertyValuesHolder.ofFloat(View.SCALE_X, startScaleX, compressedScaleX),
                PropertyValuesHolder.ofFloat(View.SCALE_Y, startScaleY, compressedScaleY),
                PropertyValuesHolder.ofFloat(View.TRANSLATION_Y, startTranslationY, pressedTranslationY));
        pressAnimator.setDuration(NAV_TAP_ANIMATION_PRESS_DURATION);
        pressAnimator.setInterpolator(new AccelerateInterpolator());

        ObjectAnimator reboundAnimator = ObjectAnimator.ofPropertyValuesHolder(target,
                PropertyValuesHolder.ofFloat(View.SCALE_X, compressedScaleX, reboundScaleX, startScaleX),
                PropertyValuesHolder.ofFloat(View.SCALE_Y, compressedScaleY, reboundScaleY, startScaleY),
                PropertyValuesHolder.ofFloat(View.TRANSLATION_Y, pressedTranslationY, reboundTranslationY,
                        startTranslationY));
        reboundAnimator.setDuration(NAV_TAP_ANIMATION_DURATION);
        reboundAnimator.setInterpolator(new OvershootInterpolator(0.95f));

        AnimatorSet tapAnimator = new AnimatorSet();
        tapAnimator.playSequentially(pressAnimator, reboundAnimator);
        tapAnimator.start();
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

        final ViewGroup.MarginLayoutParams bottomNavParams = bottomNav == null
                ? null
                : (ViewGroup.MarginLayoutParams) bottomNav.getLayoutParams();
        final int baseBottomNavBottom = bottomNavParams == null ? 0 : bottomNavParams.bottomMargin;

        final ViewGroup.MarginLayoutParams fabParams = fabAddPhoto == null
                ? null
                : (ViewGroup.MarginLayoutParams) fabAddPhoto.getLayoutParams();
        final int baseFabBottom = fabParams == null ? 0 : fabParams.bottomMargin;
        final int baseFabEnd = fabParams == null ? 0 : fabParams.rightMargin;

        ViewCompat.setOnApplyWindowInsetsListener(root, (v, insets) -> {
            Insets bars = insets.getInsets(WindowInsetsCompat.Type.systemBars());
            v.setPadding(bars.left, 0, bars.right, 0);

            ViewGroup.MarginLayoutParams updatedTitleParams = (ViewGroup.MarginLayoutParams) txtTitle.getLayoutParams();
            updatedTitleParams.topMargin = baseTopMargin + bars.top;
            txtTitle.setLayoutParams(updatedTitleParams);

            if (bottomNav != null) {
                ViewGroup.MarginLayoutParams updatedBottomNavParams =
                        (ViewGroup.MarginLayoutParams) bottomNav.getLayoutParams();
                updatedBottomNavParams.bottomMargin = baseBottomNavBottom + bars.bottom;
                bottomNav.setLayoutParams(updatedBottomNavParams);
            }

            if (fabAddPhoto != null) {
                ViewGroup.MarginLayoutParams updatedFabParams =
                        (ViewGroup.MarginLayoutParams) fabAddPhoto.getLayoutParams();
                updatedFabParams.bottomMargin = baseFabBottom;
                updatedFabParams.rightMargin = baseFabEnd + bars.right;
                fabAddPhoto.setLayoutParams(updatedFabParams);
            }

            return insets;
        });
        ViewCompat.requestApplyInsets(root);
    }
}
