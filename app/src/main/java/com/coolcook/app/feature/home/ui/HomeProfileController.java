package com.coolcook.app.feature.home.ui;

import android.content.Intent;
import android.text.TextUtils;
import android.util.Log;
import android.view.View;
import android.view.ViewGroup;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.bumptech.glide.Glide;
import com.bumptech.glide.load.engine.DiskCacheStrategy;
import com.coolcook.app.R;
import com.coolcook.app.core.theme.ThemeManager;
import com.coolcook.app.core.util.AvatarImageUtils;
import com.coolcook.app.feature.camera.ui.SavedScanDishesActivity;
import com.coolcook.app.feature.journal.ui.JournalCalendarActivity;
import com.coolcook.app.feature.main.ui.MainActivity;
import com.coolcook.app.feature.profile.ui.EditProfileDialogFragment;
import com.coolcook.app.feature.profile.ui.HealthTrackingActivity;
import com.coolcook.app.feature.search.ui.FoodCatalogActivity;
import com.facebook.login.LoginManager;
import com.google.android.gms.auth.api.signin.GoogleSignIn;
import com.google.android.gms.auth.api.signin.GoogleSignInClient;
import com.google.android.gms.auth.api.signin.GoogleSignInOptions;
import com.google.android.material.imageview.ShapeableImageView;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.FirebaseUser;
import com.google.firebase.firestore.FirebaseFirestore;
import com.google.firebase.firestore.DocumentSnapshot;

final class HomeProfileController {

    private static final String TAG = "HomeActivity";
    private static final String PROFILE_COLLECTION = "users";
    private static final String PROFILE_RESULT_KEY = "edit_profile_result";
    private static final String PROFILE_RESULT_FULL_NAME = "result_full_name";
    private static final String PROFILE_RESULT_PHONE_NUMBER = "result_phone_number";
    private static final String PROFILE_RESULT_BIRTH_DATE = "result_birth_date";
    private static final String PROFILE_RESULT_AVATAR_URL = "result_avatar_url";
    private static final long LOGOUT_NAVIGATION_FALLBACK_DELAY_MS = 1200L;

    private final HomeActivity activity;
    private final View homeScroll;
    private final View profileScroll;
    private final View btnProfileEdit;
    private final View btnProfileLogout;
    private final View btnHomeAvatar;
    private final View btnProfileAvatar;
    private final com.google.android.material.imageview.ShapeableImageView imgHomeAvatar;
    private final ShapeableImageView imgProfileAvatar;
    private final android.widget.TextView txtHomeUserName;
    private final android.widget.TextView txtProfileName;
    private final android.widget.TextView txtProfileEmail;

    private String currentPhoneNumber = "";
    private String currentBirthDate = "";
    private String currentAvatarUrl = "";
    private boolean isLogoutInProgress;

    HomeProfileController(
            @NonNull HomeActivity activity,
            @Nullable View homeScroll,
            @Nullable View profileScroll,
            @Nullable View btnProfileEdit,
            @Nullable View btnProfileLogout,
            @Nullable ShapeableImageView imgHomeAvatar,
            @Nullable ShapeableImageView imgProfileAvatar,
            @Nullable android.widget.TextView txtHomeUserName,
            @Nullable android.widget.TextView txtProfileName,
            @Nullable android.widget.TextView txtProfileEmail) {
        this.activity = activity;
        this.homeScroll = homeScroll;
        this.profileScroll = profileScroll;
        this.btnProfileEdit = btnProfileEdit;
        this.btnProfileLogout = btnProfileLogout;
        this.imgHomeAvatar = imgHomeAvatar;
        this.imgProfileAvatar = imgProfileAvatar;
        this.txtHomeUserName = txtHomeUserName;
        this.txtProfileName = txtProfileName;
        this.txtProfileEmail = txtProfileEmail;
        this.btnHomeAvatar = imgHomeAvatar;
        this.btnProfileAvatar = imgProfileAvatar;
    }

    void setupProfileActions() {
        if (btnProfileEdit != null) {
            btnProfileEdit.setOnClickListener(v -> showEditProfileDialog());
        }

        if (btnProfileLogout != null) {
            btnProfileLogout.setOnClickListener(v -> performLogout());
        }

        View favoritesAction = activity.findViewById(R.id.actionProfileFavorites);
        if (favoritesAction != null) {
            favoritesAction.setOnClickListener(v -> openFavoriteFoods());
        }

        View savedFoodsAction = activity.findViewById(R.id.actionProfileSavedFoods);
        if (savedFoodsAction != null) {
            savedFoodsAction.setOnClickListener(v -> openSavedDishes());
        }

        View journalAction = activity.findViewById(R.id.actionProfileJournal);
        if (journalAction != null) {
            journalAction.setOnClickListener(v -> openJournal());
        }

        View healthAction = activity.findViewById(R.id.actionProfileHealth);
        if (healthAction != null) {
            healthAction.setOnClickListener(v -> openHealthTracking());
        }

        View darkModeAction = activity.findViewById(R.id.actionProfileDarkMode);
        if (darkModeAction != null) {
            darkModeAction.setOnClickListener(v -> toggleDarkMode());
        }

        syncDarkModeToggle();
    }

    void observeProfileUpdates() {
        activity.getSupportFragmentManager().setFragmentResultListener(
                PROFILE_RESULT_KEY,
                activity,
                (requestKey, result) -> {
                    String fullName = result.getString(PROFILE_RESULT_FULL_NAME, "");
                    String phoneNumber = result.getString(PROFILE_RESULT_PHONE_NUMBER, "");
                    String birthDate = result.getString(PROFILE_RESULT_BIRTH_DATE, "");
                    String avatarUrl = result.getString(PROFILE_RESULT_AVATAR_URL, "");

                    updateDisplayedProfileName(fullName);
                    currentPhoneNumber = phoneNumber;
                    currentBirthDate = birthDate;
                    currentAvatarUrl = avatarUrl;
                    renderAvatar(currentAvatarUrl);
                });
    }

    void displayUserInfo() {
        FirebaseUser user = FirebaseAuth.getInstance().getCurrentUser();
        if (user == null) {
            return;
        }

        String name = user.getDisplayName();
        String email = user.getEmail();

        if (name == null || name.trim().isEmpty()) {
            name = activity.getString(R.string.home_user_name);
        }

        updateDisplayedProfileName(name);
        if (txtProfileEmail != null && email != null) {
            txtProfileEmail.setText(email);
        }
    }

    void loadProfileFromFirestore(@NonNull FirebaseFirestore firestore) {
        FirebaseUser user = FirebaseAuth.getInstance().getCurrentUser();
        if (user == null) {
            return;
        }

        firestore.collection(PROFILE_COLLECTION)
                .document(user.getUid())
                .get()
                .addOnSuccessListener(activity, snapshot -> applyProfileSnapshot(snapshot))
                .addOnFailureListener(activity,
                        error -> Log.w(TAG, "Khong the tai thong tin profile tu Firestore", error));
    }

    private void applyProfileSnapshot(@NonNull DocumentSnapshot snapshot) {
        if (!isActivityAliveForUi() || !snapshot.exists()) {
            return;
        }

        String fullName = snapshot.getString("fullName");
        String phoneNumber = snapshot.getString("phoneNumber");
        String birthDate = snapshot.getString("birthDate");
        String avatarUrl = snapshot.getString("avatarUrl");

        updateDisplayedProfileName(fullName);
        currentPhoneNumber = phoneNumber != null ? phoneNumber : "";
        currentBirthDate = birthDate != null ? birthDate : "";
        currentAvatarUrl = avatarUrl != null ? avatarUrl : "";
        renderAvatar(currentAvatarUrl);
    }

    private void updateDisplayedProfileName(@Nullable String fullName) {
        if (TextUtils.isEmpty(fullName)) {
            return;
        }
        if (txtHomeUserName != null) {
            txtHomeUserName.setText(fullName);
        }
        if (txtProfileName != null) {
            txtProfileName.setText(fullName);
        }
    }

    private void renderAvatar(@Nullable String avatarUrl) {
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
                    resolveAvatarTargetSize(btnHomeAvatar, 40));
            Glide.with(activity)
                    .load(optimizedHomeAvatar)
                    .diskCacheStrategy(DiskCacheStrategy.AUTOMATIC)
                    .placeholder(R.drawable.img_home_profile)
                    .error(R.drawable.img_home_profile)
                    .into(imgHomeAvatar);
        }

        if (imgProfileAvatar != null) {
            String optimizedProfileAvatar = AvatarImageUtils.buildOptimizedAvatarUrl(
                    avatarUrl,
                    resolveAvatarTargetSize(btnProfileAvatar, 88));
            Glide.with(activity)
                    .load(optimizedProfileAvatar)
                    .diskCacheStrategy(DiskCacheStrategy.AUTOMATIC)
                    .placeholder(R.drawable.img_home_profile)
                    .error(R.drawable.img_home_profile)
                    .into(imgProfileAvatar);
        }
    }

    private int resolveAvatarTargetSize(@Nullable View avatarView, int fallbackDp) {
        if (avatarView != null) {
            ViewGroup.LayoutParams layoutParams = avatarView.getLayoutParams();
            if (layoutParams != null && layoutParams.width > 0) {
                return layoutParams.width;
            }
        }
        float density = activity.getResources().getDisplayMetrics().density;
        return Math.round(fallbackDp * density);
    }

    private void showEditProfileDialog() {
        if (activity.getSupportFragmentManager().isStateSaved()) {
            return;
        }

        String fullName = txtProfileName != null ? txtProfileName.getText().toString() : "";
        EditProfileDialogFragment.newInstance(
                fullName,
                currentPhoneNumber,
                currentBirthDate,
                currentAvatarUrl).show(activity.getSupportFragmentManager(), "EditProfileDialog");
    }

    private void openFavoriteFoods() {
        Intent intent = FoodCatalogActivity.createFavoritesIntent(activity);
        activity.startActivity(intent);
        activity.overridePendingTransition(R.anim.slide_in_right_scale, R.anim.slide_out_left_scale);
    }

    private void openSavedDishes() {
        Intent intent = SavedScanDishesActivity.createIntent(activity);
        activity.startActivity(intent);
        activity.overridePendingTransition(R.anim.slide_in_right_scale, R.anim.slide_out_left_scale);
    }

    private void openJournal() {
        Intent intent = new Intent(activity, JournalCalendarActivity.class);
        activity.startActivity(intent);
        activity.overridePendingTransition(R.anim.slide_in_right_scale, R.anim.slide_out_left_scale);
    }

    private void openHealthTracking() {
        Intent intent = HealthTrackingActivity.createIntent(activity);
        activity.startActivity(intent);
        activity.overridePendingTransition(R.anim.slide_in_right_scale, R.anim.slide_out_left_scale);
    }

    private void toggleDarkMode() {
        String nextMode = ThemeManager.isDarkActive(activity)
                ? ThemeManager.MODE_LIGHT
                : ThemeManager.MODE_DARK;
        ThemeManager.persistAndApply(activity, nextMode);
        syncDarkModeToggle();
        activity.recreate();
    }

    private void syncDarkModeToggle() {
        View thumb = activity.findViewById(R.id.viewProfileDarkModeThumb);
        View track = activity.findViewById(R.id.layoutProfileDarkModeToggle);
        if (thumb == null || track == null) {
            return;
        }

        track.post(() -> {
            int available = track.getWidth() - thumb.getWidth() - dpToPx(6);
            thumb.setTranslationX(ThemeManager.isDarkActive(activity) ? 0f : -Math.max(available, 0));
            thumb.setAlpha(1f);
        });
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
        GoogleSignInClient googleSignInClient = GoogleSignIn.getClient(activity.getApplicationContext(), gso);
        googleSignInClient.signOut().addOnCompleteListener(activity, task -> finishLogoutIfNeeded());
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
        if (!isLogoutInProgress || activity.isFinishing() || activity.isDestroyed()) {
            return;
        }

        isLogoutInProgress = false;
        Intent intent = new Intent(activity, MainActivity.class);
        intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TASK);
        activity.startActivity(intent);
        activity.overridePendingTransition(R.anim.slide_in_left_scale, R.anim.slide_out_right_scale);
        activity.finish();
    }

    private boolean isActivityAliveForUi() {
        return !activity.isFinishing() && !activity.isDestroyed();
    }

    private int dpToPx(int value) {
        return Math.round(value * activity.getResources().getDisplayMetrics().density);
    }
}
