package com.coolcook.app.ui.home;

import android.animation.ArgbEvaluator;
import android.animation.AnimatorSet;
import android.animation.ObjectAnimator;
import android.animation.PropertyValuesHolder;
import android.animation.ValueAnimator;
import android.content.Intent;
import android.content.res.ColorStateList;
import android.os.Bundle;
import android.text.TextUtils;
import android.util.Log;
import android.view.View;
import android.view.ViewGroup;
import android.view.animation.AccelerateInterpolator;
import android.view.animation.DecelerateInterpolator;
import android.view.animation.OvershootInterpolator;
import android.widget.TextView;

import androidx.activity.EdgeToEdge;
import androidx.appcompat.widget.AppCompatImageView;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.content.ContextCompat;
import androidx.core.graphics.Insets;
import androidx.core.view.ViewCompat;
import androidx.core.view.WindowInsetsCompat;

import com.bumptech.glide.load.engine.DiskCacheStrategy;
import com.bumptech.glide.Glide;
import com.coolcook.app.R;
import com.coolcook.app.util.AvatarImageUtils;
import com.coolcook.app.ui.chatbot.ChatBotActivity;
import com.coolcook.app.ui.journal.JournalCalendarActivity;
import com.coolcook.app.ui.main.MainActivity;
import com.coolcook.app.ui.profile.EditProfileDialogFragment;
import com.coolcook.app.ui.scan.ScanFoodActivity;
import com.facebook.login.LoginManager;
import com.google.android.material.imageview.ShapeableImageView;
import com.google.android.gms.auth.api.signin.GoogleSignIn;
import com.google.android.gms.auth.api.signin.GoogleSignInClient;
import com.google.android.gms.auth.api.signin.GoogleSignInOptions;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.FirebaseUser;
import com.google.firebase.firestore.FirebaseFirestore;
import com.coolcook.app.ui.search.FoodCatalogActivity;

public class HomeActivity extends AppCompatActivity {

    private static final int TAB_HOME = 0;
    private static final int TAB_PROFILE = 1;
    private static final String TAG = "HomeActivity";
    private static final String PROFILE_COLLECTION = "users";
    private static final String PROFILE_RESULT_KEY = "edit_profile_result";
    private static final String PROFILE_RESULT_FULL_NAME = "result_full_name";
    private static final String PROFILE_RESULT_PHONE_NUMBER = "result_phone_number";
    private static final String PROFILE_RESULT_BIRTH_DATE = "result_birth_date";
    private static final String PROFILE_RESULT_AVATAR_URL = "result_avatar_url";
    private static final long NAV_TINT_ANIMATION_DURATION = 200L;
    private static final long NAV_ICON_ANIMATION_DURATION = 260L;
    private static final long NAV_TAP_ANIMATION_DURATION = 190L;
    private static final long NAV_TAP_ANIMATION_PRESS_DURATION = 70L;
    private static final float NAV_ICON_ACTIVE_SCALE = 1.14f;
    private static final float NAV_ICON_INACTIVE_SCALE = 1f;
    private static final float NAV_ICON_BOUNCE_COMPRESS_SCALE = 0.91f;
    private static final float NAV_ICON_BOUNCE_REBOUND_SCALE = 1.05f;
    private static final float NAV_TAP_COMPRESS_FACTOR = 0.9f;
    private static final float NAV_TAP_REBOUND_FACTOR = 1.04f;
    private static final long NAV_CAMERA_OPEN_DELAY_MS = 120L;
    private static final long QUICK_ACTION_PRESS_DURATION = 80L;
    private static final long QUICK_ACTION_RELEASE_DURATION = 100L;
    private static final float QUICK_ACTION_PRESS_SCALE = 0.95f;
    private static final long LOGOUT_NAVIGATION_FALLBACK_DELAY_MS = 1200L;

    private View homeScroll;
    private View profileScroll;
    private AppCompatImageView navIconHome;
    private AppCompatImageView navIconSearch;
    private AppCompatImageView navIconHistory;
    private AppCompatImageView navIconProfile;
    private AppCompatImageView navIconCamera;
    private TextView navLabelHome;
    private TextView navLabelSearch;
    private TextView navLabelHistory;
    private TextView navLabelProfile;
    private View navCameraButton;
    private View homeSearchCameraButton;
    private View homeFeatureActionButton;
    private View homeQuickScanCard;
    private View homeQuickSuggestCard;
    private View homeQuickHealthCard;
    private View homeQuickAiCard;
    private View btnProfileEdit;
    private View btnProfileLogout;
    private TextView txtHomeUserName;
    private TextView txtProfileName;
    private TextView txtProfileEmail;
    private ShapeableImageView imgHomeAvatar;
    private ShapeableImageView imgProfileAvatar;
    private FirebaseFirestore firestore;

    private String currentPhoneNumber = "";
    private String currentBirthDate = "";
    private String currentAvatarUrl = "";
    private boolean isLogoutInProgress;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        EdgeToEdge.enable(this);
        setContentView(R.layout.activity_home);

        homeScroll = findViewById(R.id.homeScroll);
        profileScroll = findViewById(R.id.profileScroll);
        navIconHome = findViewById(R.id.homeNavIconHome);
        navIconSearch = findViewById(R.id.homeNavIconSearch);
        navIconHistory = findViewById(R.id.homeNavIconHistory);
        navIconProfile = findViewById(R.id.homeNavIconProfile);
        navIconCamera = findViewById(R.id.homeNavIconCamera);
        navLabelHome = findViewById(R.id.homeNavLabelHome);
        navLabelSearch = findViewById(R.id.homeNavLabelSearch);
        navLabelHistory = findViewById(R.id.homeNavLabelHistory);
        navLabelProfile = findViewById(R.id.homeNavLabelProfile);
        navCameraButton = findViewById(R.id.homeNavCameraButton);
        homeSearchCameraButton = findViewById(R.id.homeSearchCameraButton);
        homeFeatureActionButton = findViewById(R.id.homeFeatureActionButton);
        homeQuickScanCard = findViewById(R.id.homeQuickScanCard);
        homeQuickSuggestCard = findViewById(R.id.homeQuickSuggestCard);
        homeQuickHealthCard = findViewById(R.id.homeQuickHealthCard);
        homeQuickAiCard = findViewById(R.id.homeQuickAiCard);
        btnProfileEdit = findViewById(R.id.btnProfileEdit);
        btnProfileLogout = findViewById(R.id.btnProfileLogout);
        txtHomeUserName = findViewById(R.id.txtHomeUserName);
        txtProfileName = findViewById(R.id.txtProfileName);
        txtProfileEmail = findViewById(R.id.txtProfileEmail);
        imgHomeAvatar = findViewById(R.id.imgHomeAvatar);
        imgProfileAvatar = findViewById(R.id.imgProfileAvatar);
        firestore = FirebaseFirestore.getInstance();

        setupBottomNavigation();
        setupQuickActions();
        setupProfileActions();
        observeProfileUpdates();
        displayUserInfo();
        loadProfileFromFirestore();
        showTab(TAB_HOME);
        applyInsets();
    }

    private void displayUserInfo() {
        FirebaseUser user = FirebaseAuth.getInstance().getCurrentUser();
        if (user != null) {
            String name = user.getDisplayName();
            String email = user.getEmail();

            if (name == null || name.trim().isEmpty()) {
                name = getString(R.string.home_user_name);
            }

            if (txtHomeUserName != null) {
                txtHomeUserName.setText(name);
            }
            if (txtProfileName != null) {
                txtProfileName.setText(name);
            }
            if (txtProfileEmail != null && email != null) {
                txtProfileEmail.setText(email);
            }
        }
    }

    private void loadProfileFromFirestore() {
        FirebaseUser user = FirebaseAuth.getInstance().getCurrentUser();
        if (user == null) {
            return;
        }

        firestore.collection(PROFILE_COLLECTION)
                .document(user.getUid())
                .get()
                .addOnSuccessListener(this, documentSnapshot -> {
                    if (!isActivityAliveForUi()) {
                        return;
                    }

                    if (!documentSnapshot.exists()) {
                        return;
                    }

                    String fullName = documentSnapshot.getString("fullName");
                    String phoneNumber = documentSnapshot.getString("phoneNumber");
                    String birthDate = documentSnapshot.getString("birthDate");
                    String avatarUrl = documentSnapshot.getString("avatarUrl");

                    if (!TextUtils.isEmpty(fullName)) {
                        if (txtHomeUserName != null) {
                            txtHomeUserName.setText(fullName);
                        }
                        if (txtProfileName != null) {
                            txtProfileName.setText(fullName);
                        }
                    }

                    currentPhoneNumber = phoneNumber != null ? phoneNumber : "";
                    currentBirthDate = birthDate != null ? birthDate : "";
                    currentAvatarUrl = avatarUrl != null ? avatarUrl : "";
                    renderAvatar(currentAvatarUrl);
                })
                .addOnFailureListener(this, error -> Log.w(TAG, "Khong the tai thong tin profile tu Firestore", error));
    }

    private void observeProfileUpdates() {
        getSupportFragmentManager().setFragmentResultListener(
                PROFILE_RESULT_KEY,
                this,
                (requestKey, result) -> {
                    String fullName = result.getString(PROFILE_RESULT_FULL_NAME, "");
                    String phoneNumber = result.getString(PROFILE_RESULT_PHONE_NUMBER, "");
                    String birthDate = result.getString(PROFILE_RESULT_BIRTH_DATE, "");
                    String avatarUrl = result.getString(PROFILE_RESULT_AVATAR_URL, "");

                    if (!TextUtils.isEmpty(fullName)) {
                        if (txtHomeUserName != null) {
                            txtHomeUserName.setText(fullName);
                        }
                        if (txtProfileName != null) {
                            txtProfileName.setText(fullName);
                        }
                    }

                    currentPhoneNumber = phoneNumber;
                    currentBirthDate = birthDate;
                    currentAvatarUrl = avatarUrl;
                    renderAvatar(currentAvatarUrl);
                });
    }

    private void renderAvatar(String avatarUrl) {
        if (!isActivityAliveForUi()) {
            return;
        }

        if (TextUtils.isEmpty(avatarUrl)) {
            if (imgHomeAvatar != null) {
                imgHomeAvatar.setImageResource(R.drawable.img_home_profile);
            }
            if (imgProfileAvatar != null) {
                imgProfileAvatar.setImageResource(R.drawable.img_home_profile);
            }
            return;
        }

        if (imgHomeAvatar != null) {
            String optimizedHomeAvatar = AvatarImageUtils.buildOptimizedAvatarUrl(
                    avatarUrl,
                    resolveAvatarTargetSize(imgHomeAvatar, 40));
            Glide.with(this)
                    .load(optimizedHomeAvatar)
                    .diskCacheStrategy(DiskCacheStrategy.AUTOMATIC)
                    .placeholder(R.drawable.img_home_profile)
                    .error(R.drawable.img_home_profile)
                    .into(imgHomeAvatar);
        }

        if (imgProfileAvatar != null) {
            String optimizedProfileAvatar = AvatarImageUtils.buildOptimizedAvatarUrl(
                    avatarUrl,
                    resolveAvatarTargetSize(imgProfileAvatar, 88));
            Glide.with(this)
                    .load(optimizedProfileAvatar)
                    .diskCacheStrategy(DiskCacheStrategy.AUTOMATIC)
                    .placeholder(R.drawable.img_home_profile)
                    .error(R.drawable.img_home_profile)
                    .into(imgProfileAvatar);
        }
    }

    private boolean isActivityAliveForUi() {
        return !isFinishing() && !isDestroyed();
    }

    private int resolveAvatarTargetSize(View avatarView, int fallbackDp) {
        ViewGroup.LayoutParams layoutParams = avatarView.getLayoutParams();
        if (layoutParams != null && layoutParams.width > 0) {
            return layoutParams.width;
        }
        float density = getResources().getDisplayMetrics().density;
        return Math.round(fallbackDp * density);
    }

    private void setupProfileActions() {
        if (btnProfileEdit != null) {
            btnProfileEdit.setOnClickListener(v -> showEditProfileDialog());
        }

        if (btnProfileLogout != null) {
            btnProfileLogout.setOnClickListener(v -> performLogout());
        }
    }

    private void showEditProfileDialog() {
        if (!getSupportFragmentManager().isStateSaved()) {
            String fullName = txtProfileName != null ? txtProfileName.getText().toString() : "";
            EditProfileDialogFragment.newInstance(
                    fullName,
                    currentPhoneNumber,
                    currentBirthDate,
                    currentAvatarUrl).show(getSupportFragmentManager(), "EditProfileDialog");
        }
    }

    private void performLogout() {
        if (isLogoutInProgress) {
            return;
        }

        isLogoutInProgress = true;
        if (btnProfileLogout != null) {
            btnProfileLogout.setEnabled(false);
            btnProfileLogout.setClickable(false);
            btnProfileLogout.setAlpha(0.6f);
        }

        FirebaseAuth.getInstance().signOut();
        signOutGoogleAsync();
        signOutFacebookAsync();

        View anchorView = profileScroll != null ? profileScroll : homeScroll;
        if (anchorView != null) {
            anchorView.postDelayed(this::finishLogoutIfNeeded, LOGOUT_NAVIGATION_FALLBACK_DELAY_MS);
        }
    }

    private void signOutGoogleAsync() {
        GoogleSignInOptions gso = new GoogleSignInOptions.Builder(GoogleSignInOptions.DEFAULT_SIGN_IN).build();
        GoogleSignInClient googleSignInClient = GoogleSignIn.getClient(getApplicationContext(), gso);
        googleSignInClient.signOut().addOnCompleteListener(this, task -> finishLogoutIfNeeded());
    }

    private void signOutFacebookAsync() {
        Thread facebookLogoutThread = new Thread(() -> {
            try {
                LoginManager.getInstance().logOut();
            } catch (Exception error) {
                Log.w(TAG, "Khong the dang xuat phien Facebook", error);
            }
        }, "facebook-logout-worker");
        facebookLogoutThread.start();
    }

    private void finishLogoutIfNeeded() {
        if (!isLogoutInProgress || isFinishing() || isDestroyed()) {
            return;
        }

        isLogoutInProgress = false;
        Intent intent = new Intent(this, MainActivity.class);
        intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TASK);
        startActivity(intent);
        overridePendingTransition(R.anim.slide_in_left_scale, R.anim.slide_out_right_scale);
        finish();
    }

    private void setupBottomNavigation() {
        View homeTab = findViewById(R.id.homeNavItemHome);
        View searchTab = findViewById(R.id.homeNavItemSearch);
        View cameraTab = findViewById(R.id.homeNavItemCamera);
        View historyTab = findViewById(R.id.homeNavItemHistory);
        View profileTab = findViewById(R.id.homeNavItemProfile);
        View.OnClickListener cameraClickListener = v -> openScanFromNavigation(
                navCameraButton != null ? navCameraButton : cameraTab);

        homeTab.setOnClickListener(v -> showTab(TAB_HOME));
        searchTab.setOnClickListener(v -> openFoodCatalogFromNavigation(searchTab));
        cameraTab.setOnClickListener(cameraClickListener);
        if (navCameraButton != null) {
            navCameraButton.setOnClickListener(cameraClickListener);
        }
        historyTab.setOnClickListener(v -> openJournalFromNavigation(historyTab));
        profileTab.setOnClickListener(v -> showTab(TAB_PROFILE));
    }

    private void setupQuickActions() {
        setupCameraEntryAction(homeSearchCameraButton);
        setupCameraEntryAction(homeFeatureActionButton);
        setupQuickActionCard(homeQuickScanCard, this::launchScanFoodScreen);
        setupQuickActionCard(homeQuickSuggestCard, this::launchFoodCatalogScreen);
        setupQuickActionCard(homeQuickHealthCard, null);
        setupQuickActionCard(homeQuickAiCard, () -> startActivity(ChatBotActivity.createIntent(this)));
    }

    private void setupCameraEntryAction(View entryView) {
        if (entryView == null) {
            return;
        }
        entryView.setOnClickListener(v -> animateQuickActionPress(v, this::launchScanFoodScreen));
    }

    private void openScanFromNavigation(View tapTarget) {
        if (tapTarget == null) {
            launchScanFoodScreen();
            return;
        }
        playTapFeedback(tapTarget);
        tapTarget.postDelayed(this::launchScanFoodScreen, NAV_CAMERA_OPEN_DELAY_MS);
    }

    private void openJournalFromNavigation(View tapTarget) {
        if (tapTarget != null) {
            playTapFeedback(tapTarget);
            tapTarget.postDelayed(this::launchJournalScreen, NAV_CAMERA_OPEN_DELAY_MS);
            return;
        }
        launchJournalScreen();
    }

    private void openFoodCatalogFromNavigation(View tapTarget) {
        if (tapTarget != null) {
            playTapFeedback(tapTarget);
            tapTarget.postDelayed(this::launchFoodCatalogScreen, NAV_CAMERA_OPEN_DELAY_MS);
            return;
        }
        launchFoodCatalogScreen();
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
        startActivity(new Intent(this, JournalCalendarActivity.class));
        overridePendingTransition(R.anim.slide_in_left_scale, R.anim.slide_out_right_scale);
    }

    private void launchFoodCatalogScreen() {
        if (isFinishing() || isDestroyed()) {
            return;
        }
        startActivity(FoodCatalogActivity.createIntent(this));
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
        boolean showProfile = tab == TAB_PROFILE;
        homeScroll.setVisibility(showProfile ? View.GONE : View.VISIBLE);
        profileScroll.setVisibility(showProfile ? View.VISIBLE : View.GONE);
        updateBottomNavigationState(tab);
    }

    private void updateBottomNavigationState(int activeTab) {
        int activeColor = ContextCompat.getColor(this, R.color.primary);
        int inactiveColor = ContextCompat.getColor(this, R.color.home_nav_inactive);

        applyNavItemState(navIconHome, navLabelHome, activeTab == TAB_HOME, activeColor, inactiveColor);
        applyNavItemState(navIconSearch, navLabelSearch, false, activeColor, inactiveColor);
        applyCameraIconState();
        applyNavItemState(navIconHistory, navLabelHistory, false, activeColor, inactiveColor);
        applyNavItemState(navIconProfile, navLabelProfile, activeTab == TAB_PROFILE, activeColor, inactiveColor);
    }

    private void applyNavItemState(
            AppCompatImageView icon,
            TextView label,
            boolean active,
            int activeColor,
            int inactiveColor) {
        if (icon == null) {
            return;
        }

        icon.setSelected(active);
        int targetColor = active ? activeColor : inactiveColor;
        animateIconTint(icon, targetColor);
        animateIconTransform(icon, active);
        animateLabelColor(label, targetColor);
    }

    private void animateLabelColor(TextView label, int targetColor) {
        if (label == null) {
            return;
        }

        int startColor = label.getCurrentTextColor();
        if (startColor == targetColor) {
            label.setTextColor(targetColor);
            return;
        }

        ValueAnimator tintAnimator = ValueAnimator.ofObject(new ArgbEvaluator(), startColor, targetColor);
        tintAnimator.setDuration(NAV_TINT_ANIMATION_DURATION);
        tintAnimator.setInterpolator(new DecelerateInterpolator());
        tintAnimator.addUpdateListener(animation -> {
            int color = (int) animation.getAnimatedValue();
            label.setTextColor(color);
        });
        tintAnimator.start();
    }

    private void applyCameraIconState() {
        if (navIconCamera != null) {
            navIconCamera.setImageTintList(ColorStateList.valueOf(ContextCompat.getColor(this, R.color.on_primary)));
            navIconCamera.animate().cancel();
            navIconCamera.animate()
                    .scaleX(1f)
                    .scaleY(1f)
                    .translationY(0f)
                    .setDuration(NAV_ICON_ANIMATION_DURATION)
                    .setInterpolator(new DecelerateInterpolator())
                    .start();
        }
    }

    private void animateIconTint(AppCompatImageView icon, int targetColor) {
        ColorStateList tintList = icon.getImageTintList();
        int startColor = tintList != null ? tintList.getDefaultColor() : targetColor;
        if (startColor == targetColor) {
            icon.setImageTintList(ColorStateList.valueOf(targetColor));
            return;
        }

        ValueAnimator tintAnimator = ValueAnimator.ofObject(new ArgbEvaluator(), startColor, targetColor);
        tintAnimator.setDuration(NAV_TINT_ANIMATION_DURATION);
        tintAnimator.setInterpolator(new DecelerateInterpolator());
        tintAnimator.addUpdateListener(animation -> {
            int color = (int) animation.getAnimatedValue();
            icon.setImageTintList(ColorStateList.valueOf(color));
        });
        tintAnimator.start();
    }

    private void animateIconTransform(AppCompatImageView icon, boolean active) {
        float targetScale = active ? NAV_ICON_ACTIVE_SCALE : NAV_ICON_INACTIVE_SCALE;
        float targetLift = active ? -getResources().getDimension(R.dimen.home_bottom_nav_icon_lift) : 0f;
        float startScaleX = icon.getScaleX();
        float startScaleY = icon.getScaleY();
        float startLift = icon.getTranslationY();

        float compressScale = active
                ? NAV_ICON_BOUNCE_COMPRESS_SCALE
                : Math.min(startScaleX, startScaleY) * 1.01f;
        float reboundScale = active
                ? NAV_ICON_BOUNCE_REBOUND_SCALE
                : 1.01f;
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
