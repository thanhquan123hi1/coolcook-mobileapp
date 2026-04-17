package com.coolcook.app.ui.auth;

import android.content.Intent;
import android.os.Bundle;
import android.text.method.HideReturnsTransformationMethod;
import android.text.method.PasswordTransformationMethod;
import android.view.View;
import android.view.animation.AccelerateDecelerateInterpolator;
import android.view.animation.DecelerateInterpolator;
import android.view.animation.OvershootInterpolator;
import android.widget.EditText;
import android.widget.TextView;

import androidx.activity.EdgeToEdge;
import androidx.annotation.IdRes;
import androidx.appcompat.app.AppCompatActivity;
import androidx.constraintlayout.widget.ConstraintLayout;
import androidx.constraintlayout.widget.Guideline;
import androidx.core.graphics.Insets;
import androidx.core.view.ViewCompat;
import androidx.core.view.WindowInsetsCompat;

import com.coolcook.app.R;
import com.coolcook.app.ui.home.HomeActivity;
import com.google.android.material.card.MaterialCardView;

public class AuthActivity extends AppCompatActivity {

    public static final String EXTRA_AUTH_MODE = "extra_auth_mode";
    public static final String MODE_LOGIN = "login";
    public static final String MODE_REGISTER = "register";

    private View loginPanel;
    private View registerPanel;
    private boolean showingRegister;
    private boolean isTransitioning;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        EdgeToEdge.enable(this);
        setContentView(R.layout.activity_auth);

        loginPanel = findViewById(R.id.loginPanel);
        registerPanel = findViewById(R.id.registerPanel);

        applyInsets();
        applyResponsiveTweaks();
        setupPasswordToggles();
        setupActions();

        String initialMode = getIntent().getStringExtra(EXTRA_AUTH_MODE);
        showingRegister = MODE_REGISTER.equals(initialMode);
        applyInitialState(showingRegister);
    }

    public static Intent createIntent(AppCompatActivity activity, String mode) {
        Intent intent = new Intent(activity, AuthActivity.class);
        intent.putExtra(EXTRA_AUTH_MODE, mode);
        return intent;
    }

    private void setupActions() {
        findViewById(R.id.btnBack).setOnClickListener(v -> {
            finish();
            overridePendingTransition(R.anim.slide_in_left_scale, R.anim.slide_out_right_scale);
        });

        findViewById(R.id.txtSwitchToRegister).setOnClickListener(v -> switchMode(true));
        findViewById(R.id.txtSwitchToLogin).setOnClickListener(v -> switchMode(false));
        findViewById(R.id.btnSubmitLogin).setOnClickListener(v -> openHomeDemo());
    }

    private void openHomeDemo() {
        Intent intent = new Intent(this, HomeActivity.class);
        intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TASK);
        startActivity(intent);
        overridePendingTransition(R.anim.slide_in_right_scale, R.anim.slide_out_left_scale);
    }

    private void applyInsets() {
        View root = findViewById(R.id.authRoot);
        View header = findViewById(R.id.authHeader);
        MaterialCardView card = findViewById(R.id.authCard);

        final int baseHeaderTop = dp(12);
        final int baseCardBottom = dp(8);

        ViewCompat.setOnApplyWindowInsetsListener(root, (v, insets) -> {
            Insets systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars());

            header.setPadding(
                    header.getPaddingLeft(),
                    baseHeaderTop + systemBars.top,
                    header.getPaddingRight(),
                    header.getPaddingBottom());

            card.setPadding(
                    card.getPaddingLeft(),
                    card.getPaddingTop(),
                    card.getPaddingRight(),
                    baseCardBottom + systemBars.bottom);

            return insets;
        });
    }

    private void applyResponsiveTweaks() {
        float density = getResources().getDisplayMetrics().density;
        float heightDp = getResources().getDisplayMetrics().heightPixels / density;

        if (heightDp <= 700f) {
            setGuidelinePercent(R.id.guideCardTop, 0.24f);
            setGuidelinePercent(R.id.guideImageBottom, 0.50f);
            View content = findViewById(R.id.authContentContainer);
            content.setPadding(dp(24), dp(20), dp(24), dp(12));
        } else if (heightDp >= 880f) {
            setGuidelinePercent(R.id.guideCardTop, 0.32f);
            setGuidelinePercent(R.id.guideImageBottom, 0.57f);
        }
    }

    private void setGuidelinePercent(@IdRes int guidelineId, float percent) {
        Guideline guideline = findViewById(guidelineId);
        ConstraintLayout.LayoutParams params = (ConstraintLayout.LayoutParams) guideline.getLayoutParams();
        params.guidePercent = percent;
        guideline.setLayoutParams(params);
    }

    private void setupPasswordToggles() {
        setupPasswordToggle(R.id.loginPassword, R.id.toggleLoginPassword);
        setupPasswordToggle(R.id.registerPassword, R.id.toggleRegisterPassword);
        setupPasswordToggle(R.id.registerConfirmPassword, R.id.toggleRegisterConfirmPassword);
    }

    private void setupPasswordToggle(@IdRes int inputId, @IdRes int toggleId) {
        EditText input = findViewById(inputId);
        TextView toggle = findViewById(toggleId);
        toggle.setTag(Boolean.FALSE);

        toggle.setOnClickListener(v -> {
            boolean isVisible = Boolean.TRUE.equals(toggle.getTag());
            if (isVisible) {
                input.setTransformationMethod(PasswordTransformationMethod.getInstance());
                toggle.setText(R.string.icon_visibility_off);
            } else {
                input.setTransformationMethod(HideReturnsTransformationMethod.getInstance());
                toggle.setText(R.string.icon_visibility_on);
            }
            toggle.setTag(!isVisible);
            input.setSelection(input.getText().length());
        });
    }

    private void applyInitialState(boolean register) {
        if (register) {
            registerPanel.setVisibility(View.VISIBLE);
            registerPanel.setAlpha(1f);
            loginPanel.setVisibility(View.GONE);
        } else {
            loginPanel.setVisibility(View.VISIBLE);
            loginPanel.setAlpha(1f);
            registerPanel.setVisibility(View.GONE);
        }
    }

    private void switchMode(boolean targetRegister) {
        if (isTransitioning || targetRegister == showingRegister) {
            return;
        }

        isTransitioning = true;

        final View outgoing = showingRegister ? registerPanel : loginPanel;
        final View incoming = targetRegister ? registerPanel : loginPanel;

        outgoing.animate().cancel();
        incoming.animate().cancel();

        outgoing.animate()
                .alpha(0f)
                .scaleX(0.97f)
                .scaleY(0.97f)
                .translationY(dp(12))
                .setDuration(140)
                .setInterpolator(new AccelerateDecelerateInterpolator())
                .withEndAction(() -> {
                    outgoing.setVisibility(View.GONE);
                    outgoing.setAlpha(1f);
                    outgoing.setScaleX(1f);
                    outgoing.setScaleY(1f);
                    outgoing.setTranslationY(0f);

                    incoming.setVisibility(View.VISIBLE);
                    incoming.setAlpha(0f);
                    incoming.setScaleX(0.98f);
                    incoming.setScaleY(0.98f);
                    incoming.setTranslationY(dp(18));

                    incoming.animate()
                            .alpha(1f)
                            .translationY(0f)
                            .scaleX(1.01f)
                            .scaleY(1.01f)
                            .setDuration(180)
                            .setInterpolator(new OvershootInterpolator(0.45f))
                            .withEndAction(() -> incoming.animate()
                                    .scaleX(1f)
                                    .scaleY(1f)
                                    .setDuration(70)
                                    .setInterpolator(new DecelerateInterpolator())
                                    .withEndAction(() -> {
                                        showingRegister = targetRegister;
                                        isTransitioning = false;
                                    })
                                    .start())
                            .start();
                })
                .start();
    }

    private int dp(int value) {
        return Math.round(value * getResources().getDisplayMetrics().density);
    }
}