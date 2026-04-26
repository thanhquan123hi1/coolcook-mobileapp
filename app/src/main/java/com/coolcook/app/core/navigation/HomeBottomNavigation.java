package com.coolcook.app.core.navigation;

import android.content.Intent;
import android.content.res.ColorStateList;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.app.AppCompatActivity;
import androidx.appcompat.widget.AppCompatImageView;
import androidx.core.content.ContextCompat;

import com.coolcook.app.R;
import com.coolcook.app.feature.home.ui.HomeActivity;
import com.coolcook.app.feature.journal.ui.JournalCalendarActivity;
import com.coolcook.app.feature.camera.ui.ScanFoodActivity;
import com.coolcook.app.feature.search.ui.FoodCatalogActivity;

public final class HomeBottomNavigation {

    public enum Tab {
        HOME,
        SEARCH,
        HISTORY,
        PROFILE
    }

    @NonNull
    private final AppCompatActivity activity;
    @NonNull
    private final Tab activeTab;
    @Nullable
    private final Runnable homeAction;
    @Nullable
    private final Runnable searchAction;
    @Nullable
    private final Runnable historyAction;
    @Nullable
    private final Runnable profileAction;

    private HomeBottomNavigation(
            @NonNull AppCompatActivity activity,
            @NonNull Tab activeTab,
            @Nullable Runnable homeAction,
            @Nullable Runnable searchAction,
            @Nullable Runnable historyAction,
            @Nullable Runnable profileAction) {
        this.activity = activity;
        this.activeTab = activeTab;
        this.homeAction = homeAction;
        this.searchAction = searchAction;
        this.historyAction = historyAction;
        this.profileAction = profileAction;
    }

    public static void bind(
            @NonNull AppCompatActivity activity,
            @NonNull Tab activeTab,
            @Nullable Runnable homeAction,
            @Nullable Runnable profileAction) {
        bind(activity, activeTab, homeAction, null, null, profileAction);
    }

    public static void bind(
            @NonNull AppCompatActivity activity,
            @NonNull Tab activeTab,
            @Nullable Runnable homeAction,
            @Nullable Runnable searchAction,
            @Nullable Runnable historyAction,
            @Nullable Runnable profileAction) {
        new HomeBottomNavigation(activity, activeTab, homeAction, searchAction, historyAction, profileAction).bind();
    }

    private void bind() {
        View navRoot = resolveNavigationRoot();
        View homeTab = findInNav(navRoot, R.id.homeNavItemHome);
        View searchTab = findInNav(navRoot, R.id.homeNavItemSearch);
        View cameraTab = findInNav(navRoot, R.id.homeNavItemCamera);
        View cameraButton = findInNav(navRoot, R.id.homeNavCameraButton);
        View historyTab = findInNav(navRoot, R.id.homeNavItemHistory);
        View profileTab = findInNav(navRoot, R.id.homeNavItemProfile);

        setClick(homeTab, this::openHome);
        setClick(searchTab, this::openSearch);
        setClick(cameraTab, this::openCamera);
        setClick(cameraButton, this::openCamera);
        setClick(historyTab, this::openHistory);
        setClick(profileTab, this::openProfile);

        updateState();
    }

    @Nullable
    private View resolveNavigationRoot() {
        View homeRoot = activity.findViewById(R.id.homeRoot);
        if (homeRoot instanceof ViewGroup) {
            View directNav = findDirectChildById((ViewGroup) homeRoot, R.id.homeBottomNav);
            if (directNav != null) {
                return directNav;
            }
        }
        return activity.findViewById(R.id.homeBottomNav);
    }

    @Nullable
    private View findDirectChildById(@NonNull ViewGroup parent, int viewId) {
        for (int index = 0; index < parent.getChildCount(); index++) {
            View child = parent.getChildAt(index);
            if (child.getId() == viewId) {
                return child;
            }
        }
        return null;
    }

    @Nullable
    private View findInNav(@Nullable View navRoot, int viewId) {
        if (navRoot == null) {
            return activity.findViewById(viewId);
        }
        return navRoot.findViewById(viewId);
    }

    private void setClick(@Nullable View view, @NonNull Runnable action) {
        if (view != null) {
            view.setOnClickListener(v -> action.run());
        }
    }

    private void updateState() {
        int activeColor = ContextCompat.getColor(activity, R.color.home_nav_active);
        int inactiveColor = ContextCompat.getColor(activity, R.color.home_nav_inactive);

        applyItemState(R.id.homeNavIconHome, R.id.homeNavLabelHome, activeTab == Tab.HOME, activeColor, inactiveColor);
        applyItemState(R.id.homeNavIconSearch, R.id.homeNavLabelSearch, activeTab == Tab.SEARCH, activeColor, inactiveColor);
        applyCameraState();
        applyItemState(R.id.homeNavIconHistory, R.id.homeNavLabelHistory, activeTab == Tab.HISTORY, activeColor, inactiveColor);
        applyItemState(R.id.homeNavIconProfile, R.id.homeNavLabelProfile, activeTab == Tab.PROFILE, activeColor, inactiveColor);
    }

    private void applyItemState(int iconId, int labelId, boolean active, int activeColor, int inactiveColor) {
        View navRoot = resolveNavigationRoot();
        AppCompatImageView icon = (AppCompatImageView) findInNav(navRoot, iconId);
        TextView label = (TextView) findInNav(navRoot, labelId);
        int color = active ? activeColor : inactiveColor;

        if (icon != null) {
            icon.setSelected(active);
            icon.setImageResource(resolveNavIcon(iconId, active));
            icon.setImageTintList(null);
            icon.setScaleX(1f);
            icon.setScaleY(1f);
            icon.setTranslationY(0f);
        }
        if (label != null) {
            label.setTextColor(color);
        }
    }

    private void applyCameraState() {
        AppCompatImageView cameraIcon = (AppCompatImageView) findInNav(resolveNavigationRoot(), R.id.homeNavIconCamera);
        if (cameraIcon != null) {
            cameraIcon.setImageResource(R.drawable.ic_home_nav_camera_figma);
            cameraIcon.setImageTintList(null);
            cameraIcon.setScaleX(1f);
            cameraIcon.setScaleY(1f);
            cameraIcon.setTranslationY(0f);
        }
    }

    private int resolveNavIcon(int iconId, boolean active) {
        if (iconId == R.id.homeNavIconHome) {
            return active ? R.drawable.ic_home_nav_home_active_figma
                    : R.drawable.ic_home_nav_home_inactive_figma;
        }
        if (iconId == R.id.homeNavIconSearch) {
            return active ? R.drawable.ic_home_nav_search_active_figma
                    : R.drawable.ic_home_nav_search_inactive_figma;
        }
        if (iconId == R.id.homeNavIconHistory) {
            return active ? R.drawable.ic_home_nav_history_active_figma
                    : R.drawable.ic_home_nav_history_inactive_figma;
        }
        if (iconId == R.id.homeNavIconProfile) {
            return active ? R.drawable.ic_home_nav_profile_active_figma
                    : R.drawable.ic_home_nav_profile_inactive_figma;
        }
        return R.drawable.ic_home_nav_camera_figma;
    }

    private void openHome() {
        if (activeTab == Tab.HOME && homeAction != null) {
            homeAction.run();
            return;
        }
        if (homeAction != null && activity instanceof HomeActivity) {
            homeAction.run();
            return;
        }
        Intent intent = new Intent(activity, HomeActivity.class);
        intent.addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP | Intent.FLAG_ACTIVITY_SINGLE_TOP);
        activity.startActivity(intent);
    }

    private void openSearch() {
        if (searchAction != null) {
            searchAction.run();
            return;
        }
        if (activeTab == Tab.SEARCH || activity instanceof FoodCatalogActivity) {
            return;
        }
        activity.startActivity(FoodCatalogActivity.createIntent(activity));
    }

    private void openCamera() {
        activity.startActivity(ScanFoodActivity.createIntent(activity));
    }

    private void openHistory() {
        if (historyAction != null) {
            historyAction.run();
            return;
        }
        if (activeTab == Tab.HISTORY || activity instanceof JournalCalendarActivity) {
            return;
        }
        activity.startActivity(new Intent(activity, JournalCalendarActivity.class));
    }

    private void openProfile() {
        if (profileAction != null && activity instanceof HomeActivity) {
            profileAction.run();
            return;
        }
        Intent intent = new Intent(activity, HomeActivity.class);
        intent.addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP | Intent.FLAG_ACTIVITY_SINGLE_TOP);
        intent.putExtra(HomeActivity.EXTRA_OPEN_TAB, HomeActivity.EXTRA_TAB_PROFILE);
        activity.startActivity(intent);
    }
}
