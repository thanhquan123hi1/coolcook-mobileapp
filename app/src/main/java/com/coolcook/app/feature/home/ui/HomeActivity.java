package com.coolcook.app.feature.home.ui;

import android.content.Intent;
import android.os.Bundle;
import android.text.TextUtils;
import android.view.View;
import android.view.ViewGroup;
import android.view.animation.DecelerateInterpolator;
import android.widget.TextView;

import androidx.activity.EdgeToEdge;
import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.graphics.Insets;
import androidx.core.view.ViewCompat;
import androidx.core.view.WindowInsetsCompat;

import com.coolcook.app.R;
import com.coolcook.app.core.navigation.HomeBottomNavigation;
import com.coolcook.app.feature.chatbot.ui.ChatBotActivity;
import com.coolcook.app.feature.journal.ui.JournalCalendarFragment;
import com.coolcook.app.feature.camera.ui.ScanFoodActivity;
import com.coolcook.app.feature.profile.ui.HealthTrackingActivity;
import com.coolcook.app.feature.social.ui.FriendInviteActivity;
import com.coolcook.app.feature.search.data.FoodJsonRepository;
import com.coolcook.app.feature.search.model.FoodCatalogFilter;
import com.coolcook.app.feature.search.model.FoodItem;
import com.coolcook.app.feature.search.ui.FoodDetailActivity;
import com.coolcook.app.feature.search.ui.FoodCatalogFragment;
import com.google.android.material.imageview.ShapeableImageView;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.FirebaseUser;
import com.google.firebase.firestore.FirebaseFirestore;

import java.util.List;
import java.util.Locale;

public class HomeActivity extends AppCompatActivity {

    private static final int TAB_HOME = 0;
    private static final int TAB_SEARCH = 1;
    private static final int TAB_JOURNAL = 2;
    private static final int TAB_PROFILE = 3;
    private static final String SEARCH_FRAGMENT_TAG = "HomeActivity.SearchFragment";
    private static final String JOURNAL_FRAGMENT_TAG = "HomeActivity.JournalFragment";
    public static final String EXTRA_OPEN_TAB = "com.coolcook.app.EXTRA_OPEN_TAB";
    public static final String EXTRA_TAB_PROFILE = "profile";
    private static final long QUICK_ACTION_PRESS_DURATION = 80L;
    private static final long QUICK_ACTION_RELEASE_DURATION = 100L;
    private static final float QUICK_ACTION_PRESS_SCALE = 0.95f;

    private View homeScroll;
    private View homeSearchBar;
    private View searchContainer;
    private View journalContainer;
    private View profileScroll;
    private View homeSearchCameraButton;
    private View homeFeatureActionButton;
    private View homeQuickScanCard;
    private View homeQuickSuggestCard;
    private View homeQuickHealthCard;
    private View homeQuickAiCard;
    private View btnHomeSeeAllFoods;
    private View homeTodayFoodCard1;
    private View homeTodayFoodCard2;
    private View homeGoalLowFatCard;
    private View homeGoalStomachCard;
    private View homeGoalProteinCard;
    private View btnProfileEdit;
    private View btnProfileLogout;
    private TextView homeTodayFoodTitle1;
    private TextView homeTodayFoodTitle2;
    private TextView homeTodayFoodTag1;
    private TextView homeTodayFoodTag2;
    private TextView homeTodayFoodTime1;
    private TextView homeTodayFoodTime2;
    private TextView txtHomeUserName;
    private TextView txtProfileName;
    private TextView txtProfileEmail;
    private ShapeableImageView imgHomeAvatar;
    private ShapeableImageView imgProfileAvatar;
    private ShapeableImageView homeTodayFoodImage1;
    private ShapeableImageView homeTodayFoodImage2;
    private FirebaseFirestore firestore;
    private HomeProfileController profileController;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        EdgeToEdge.enable(this);
        setContentView(R.layout.activity_home);

        homeScroll = findViewById(R.id.homeScroll);
        homeSearchBar = findViewById(R.id.homeSearchBar);
        searchContainer = findViewById(R.id.searchContainer);
        journalContainer = findViewById(R.id.journalContainer);
        profileScroll = findViewById(R.id.profileScroll);
        homeSearchCameraButton = findViewById(R.id.homeSearchCameraButton);
        homeFeatureActionButton = findViewById(R.id.homeFeatureActionButton);
        homeQuickScanCard = findViewById(R.id.homeQuickScanCard);
        homeQuickSuggestCard = findViewById(R.id.homeQuickSuggestCard);
        homeQuickHealthCard = findViewById(R.id.homeQuickHealthCard);
        homeQuickAiCard = findViewById(R.id.homeQuickAiCard);
        btnHomeSeeAllFoods = findViewById(R.id.btnHomeSeeAllFoods);
        homeTodayFoodCard1 = findViewById(R.id.homeTodayFoodCard1);
        homeTodayFoodCard2 = findViewById(R.id.homeTodayFoodCard2);
        homeGoalLowFatCard = findViewById(R.id.homeGoalLowFatCard);
        homeGoalStomachCard = findViewById(R.id.homeGoalStomachCard);
        homeGoalProteinCard = findViewById(R.id.homeGoalProteinCard);
        btnProfileEdit = findViewById(R.id.btnProfileEdit);
        btnProfileLogout = findViewById(R.id.btnProfileLogout);
        homeTodayFoodTitle1 = findViewById(R.id.homeTodayFoodTitle1);
        homeTodayFoodTitle2 = findViewById(R.id.homeTodayFoodTitle2);
        homeTodayFoodTag1 = findViewById(R.id.homeTodayFoodTag1);
        homeTodayFoodTag2 = findViewById(R.id.homeTodayFoodTag2);
        homeTodayFoodTime1 = findViewById(R.id.homeTodayFoodTime1);
        homeTodayFoodTime2 = findViewById(R.id.homeTodayFoodTime2);
        txtHomeUserName = findViewById(R.id.txtHomeUserName);
        txtProfileName = findViewById(R.id.txtProfileName);
        txtProfileEmail = findViewById(R.id.txtProfileEmail);
        imgHomeAvatar = findViewById(R.id.imgHomeAvatar);
        imgProfileAvatar = findViewById(R.id.imgProfileAvatar);
        homeTodayFoodImage1 = findViewById(R.id.homeTodayFoodImage1);
        homeTodayFoodImage2 = findViewById(R.id.homeTodayFoodImage2);
        firestore = FirebaseFirestore.getInstance();
        profileController = new HomeProfileController(
                this,
                homeScroll,
                profileScroll,
                btnProfileEdit,
                btnProfileLogout,
                imgHomeAvatar,
                imgProfileAvatar,
                txtHomeUserName,
                txtProfileName,
                txtProfileEmail);

        setupQuickActions();
        profileController.setupProfileActions();
        profileController.observeProfileUpdates();
        profileController.displayUserInfo();
        profileController.loadProfileFromFirestore(firestore);
        profileController.loadStats(firestore);
        bindTodayFoodCards();
        showInitialTab(getIntent());
        applyInsets();
        openPendingInviteIfNeeded();
    }

    @Override
    protected void onNewIntent(Intent intent) {
        super.onNewIntent(intent);
        setIntent(intent);
        showInitialTab(intent);
        openPendingInviteIfNeeded();
    }

    @Override
    protected void onResume() {
        super.onResume();
        profileController.displayUserInfo();
        profileController.loadProfileFromFirestore(firestore);
        profileController.loadStats(firestore);
    }

    private void showInitialTab(Intent intent) {
        if (intent != null && EXTRA_TAB_PROFILE.equals(intent.getStringExtra(EXTRA_OPEN_TAB))) {
            showTab(TAB_PROFILE);
            return;
        }
        showTab(TAB_HOME);
    }

    private void openPendingInviteIfNeeded() {
        FirebaseUser user = FirebaseAuth.getInstance().getCurrentUser();
        if (user == null) {
            return;
        }

        String pendingInviteId = FriendInviteActivity.consumePendingInvite(this);
        if (TextUtils.isEmpty(pendingInviteId)) {
            return;
        }

        Intent intent = new Intent(this, FriendInviteActivity.class);
        intent.putExtra("inviteId", pendingInviteId);
        startActivity(intent);
    }

    private void setupQuickActions() {
        setupSearchEntryAction(homeSearchBar);
        setupCameraEntryAction(homeSearchCameraButton);
        setupCameraEntryAction(homeFeatureActionButton);
        setupQuickActionCard(homeQuickScanCard, this::launchScanFoodScreen);
        setupQuickActionCard(homeQuickSuggestCard, this::launchFoodCatalogScreen);
        setupQuickActionCard(homeQuickHealthCard, () -> startActivity(HealthTrackingActivity.createIntent(this)));
        setupQuickActionCard(homeQuickAiCard, () -> startActivity(ChatBotActivity.createIntent(this)));
        setupGoalFilterCards();
        setupSeeAllFoodsAction();
    }

    private void setupGoalFilterCards() {
        setupQuickActionCard(homeGoalLowFatCard, () -> openFoodCatalogWithFilter(FoodCatalogFilter.LOW_FAT));
        setupQuickActionCard(homeGoalStomachCard, () -> openFoodCatalogWithFilter(FoodCatalogFilter.STOMACH));
        setupQuickActionCard(homeGoalProteinCard, () -> openFoodCatalogWithFilter(FoodCatalogFilter.PROTEIN));
    }

    private void setupSeeAllFoodsAction() {
        if (btnHomeSeeAllFoods == null) {
            return;
        }
        btnHomeSeeAllFoods.setOnClickListener(v -> animateQuickActionPress(v, this::launchFoodCatalogScreen));
    }

    private void openFoodCatalogWithFilter(@NonNull FoodCatalogFilter filter) {
        if (isFinishing() || isDestroyed()) {
            return;
        }
        showTab(TAB_SEARCH);
        getSupportFragmentManager().executePendingTransactions();
        FoodCatalogFragment fragment = getSearchFragment();
        if (fragment != null) {
            fragment.applyFilter(filter);
        }
    }

    private void setupSearchEntryAction(View entryView) {
        if (entryView == null) {
            return;
        }
        entryView.setOnClickListener(v -> animateQuickActionPress(v, this::launchFoodCatalogScreen));
    }

    private void setupCameraEntryAction(View entryView) {
        if (entryView == null) {
            return;
        }
        entryView.setOnClickListener(v -> animateQuickActionPress(v, this::launchScanFoodScreen));
    }

    private void launchScanFoodScreen() {
        if (isFinishing() || isDestroyed()) {
            return;
        }
        startActivity(ScanFoodActivity.createIntent(this));
        overridePendingTransition(R.anim.slide_in_left_scale, R.anim.slide_out_right_scale);
    }

    private void launchJournalScreen() {
        if (isFinishing() || isDestroyed()) {
            return;
        }
        showTab(TAB_JOURNAL);
    }

    private void launchFoodCatalogScreen() {
        if (isFinishing() || isDestroyed()) {
            return;
        }
        showTab(TAB_SEARCH);
    }

    private void bindTodayFoodCards() {
        List<FoodItem> foods = new FoodJsonRepository(this).getFoods();
        bindTodayFoodCard(homeTodayFoodCard1, homeTodayFoodImage1, homeTodayFoodTitle1, homeTodayFoodTag1, homeTodayFoodTime1, foods, 0);
        bindTodayFoodCard(homeTodayFoodCard2, homeTodayFoodImage2, homeTodayFoodTitle2, homeTodayFoodTag2, homeTodayFoodTime2, foods, 1);
    }

    private void bindTodayFoodCard(
            View card,
            ShapeableImageView imageView,
            TextView titleView,
            TextView tagView,
            TextView timeView,
            List<FoodItem> foods,
            int index) {
        if (card == null || imageView == null || titleView == null || tagView == null || timeView == null) {
            return;
        }
        if (foods == null || index < 0 || index >= foods.size()) {
            card.setVisibility(View.GONE);
            return;
        }

        FoodItem food = foods.get(index);
        card.setVisibility(View.VISIBLE);
        imageView.setImageResource(food.resolveImageResId(this));
        titleView.setText(food.getName());
        tagView.setText(food.getHomeCardTag());
        timeView.setText(String.format(Locale.US, "%d MIN", food.getCookTimeMinutes()));
        card.setOnClickListener(v -> openFoodDetail(food));
    }

    private void openFoodDetail(@NonNull FoodItem food) {
        if (isFinishing() || isDestroyed()) {
            return;
        }
        startActivity(FoodDetailActivity.createIntent(this, food.getId()));
        overridePendingTransition(R.anim.slide_in_right_scale, R.anim.slide_out_left_scale);
    }

    private void setupQuickActionCard(View card, Runnable action) {
        if (card == null) {
            return;
        }
        card.setOnClickListener(v -> animateQuickActionPress(v, action));
    }

    private void animateQuickActionPress(View target, Runnable action) {
        if (target == null || !target.isEnabled()) {
            return;
        }

        target.setEnabled(false);
        target.animate().cancel();
        target.animate()
                .scaleX(QUICK_ACTION_PRESS_SCALE)
                .scaleY(QUICK_ACTION_PRESS_SCALE)
                .setDuration(QUICK_ACTION_PRESS_DURATION)
                .setInterpolator(new DecelerateInterpolator())
                .withEndAction(() -> target.animate()
                        .scaleX(1f)
                        .scaleY(1f)
                        .setDuration(QUICK_ACTION_RELEASE_DURATION)
                        .setInterpolator(new DecelerateInterpolator())
                        .withEndAction(() -> {
                            target.setEnabled(true);
                            if (action != null) {
                                action.run();
                            }
                        })
                        .start())
                .start();
    }

    private void showTab(int tab) {
        if (tab == TAB_SEARCH) {
            ensureSearchFragment();
        } else if (tab == TAB_JOURNAL) {
            ensureJournalFragment();
        }

        homeScroll.setVisibility(tab == TAB_HOME ? View.VISIBLE : View.GONE);
        searchContainer.setVisibility(tab == TAB_SEARCH ? View.VISIBLE : View.GONE);
        journalContainer.setVisibility(tab == TAB_JOURNAL ? View.VISIBLE : View.GONE);
        profileScroll.setVisibility(tab == TAB_PROFILE ? View.VISIBLE : View.GONE);
        HomeBottomNavigation.bind(
                this,
                toBottomNavTab(tab),
                () -> showTab(TAB_HOME),
                () -> showTab(TAB_SEARCH),
                () -> showTab(TAB_JOURNAL),
                () -> showTab(TAB_PROFILE));
    }

    private void ensureSearchFragment() {
        if (getSearchFragment() != null) {
            return;
        }
        getSupportFragmentManager()
                .beginTransaction()
                .add(R.id.searchContainer, new FoodCatalogFragment(), SEARCH_FRAGMENT_TAG)
                .commit();
    }

    private FoodCatalogFragment getSearchFragment() {
        return (FoodCatalogFragment) getSupportFragmentManager().findFragmentByTag(SEARCH_FRAGMENT_TAG);
    }

    private void ensureJournalFragment() {
        if (getSupportFragmentManager().findFragmentByTag(JOURNAL_FRAGMENT_TAG) != null) {
            return;
        }
        getSupportFragmentManager()
                .beginTransaction()
                .add(R.id.journalContainer, new JournalCalendarFragment(), JOURNAL_FRAGMENT_TAG)
                .commit();
    }

    private HomeBottomNavigation.Tab resolveCurrentBottomNavTab() {
        if (profileScroll != null && profileScroll.getVisibility() == View.VISIBLE) {
            return HomeBottomNavigation.Tab.PROFILE;
        }
        if (searchContainer != null && searchContainer.getVisibility() == View.VISIBLE) {
            return HomeBottomNavigation.Tab.SEARCH;
        }
        if (journalContainer != null && journalContainer.getVisibility() == View.VISIBLE) {
            return HomeBottomNavigation.Tab.HISTORY;
        }
        return HomeBottomNavigation.Tab.HOME;
    }

    private HomeBottomNavigation.Tab toBottomNavTab(int tab) {
        if (tab == TAB_SEARCH) {
            return HomeBottomNavigation.Tab.SEARCH;
        }
        if (tab == TAB_JOURNAL) {
            return HomeBottomNavigation.Tab.HISTORY;
        }
        if (tab == TAB_PROFILE) {
            return HomeBottomNavigation.Tab.PROFILE;
        }
        return HomeBottomNavigation.Tab.HOME;
    }

    private void applyInsets() {
        View root = findViewById(R.id.homeRoot);
        View homeContent = findViewById(R.id.homeContentContainer);
        View profileContent = findViewById(R.id.profileContentContainer);
        View bottomNav = findViewById(R.id.homeBottomNav);

        final int homeContentLeft = homeContent.getPaddingLeft();
        final int homeContentTop = homeContent.getPaddingTop();
        final int homeContentRight = homeContent.getPaddingRight();
        final int homeContentBottom = homeContent.getPaddingBottom();

        final int profileContentLeft = profileContent.getPaddingLeft();
        final int profileContentTop = profileContent.getPaddingTop();
        final int profileContentRight = profileContent.getPaddingRight();
        final int profileContentBottom = profileContent.getPaddingBottom();

        final int navLeft = bottomNav.getPaddingLeft();
        final int navTop = bottomNav.getPaddingTop();
        final int navRight = bottomNav.getPaddingRight();
        final int navBottom = bottomNav.getPaddingBottom();
        final ViewGroup.MarginLayoutParams navLayoutParams = (ViewGroup.MarginLayoutParams) bottomNav.getLayoutParams();
        final int navMarginLeft = navLayoutParams.leftMargin;
        final int navMarginRight = navLayoutParams.rightMargin;
        final int navMarginBottom = navLayoutParams.bottomMargin;

        ViewCompat.setOnApplyWindowInsetsListener(root, (v, insets) -> {
            Insets systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars());

            homeContent.setPadding(
                    homeContentLeft + systemBars.left,
                    homeContentTop + systemBars.top,
                    homeContentRight + systemBars.right,
                    homeContentBottom);

            profileContent.setPadding(
                    profileContentLeft + systemBars.left,
                    profileContentTop + systemBars.top,
                    profileContentRight + systemBars.right,
                    profileContentBottom);

            ViewGroup.MarginLayoutParams updatedLayoutParams = (ViewGroup.MarginLayoutParams) bottomNav
                    .getLayoutParams();
            updatedLayoutParams.leftMargin = navMarginLeft + systemBars.left;
            updatedLayoutParams.rightMargin = navMarginRight + systemBars.right;
            updatedLayoutParams.bottomMargin = navMarginBottom + systemBars.bottom;
            bottomNav.setLayoutParams(updatedLayoutParams);

            bottomNav.setPadding(
                    navLeft,
                    navTop,
                    navRight,
                    navBottom);

            return insets;
        });
    }
}
