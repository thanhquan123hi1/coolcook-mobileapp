package com.coolcook.app.feature.auth.ui;

import android.view.View;
import android.view.animation.AccelerateDecelerateInterpolator;
import android.view.animation.DecelerateInterpolator;
import android.view.animation.OvershootInterpolator;

import androidx.annotation.NonNull;
import androidx.core.widget.NestedScrollView;

final class AuthModeSwitcher {

    private final AuthActivity activity;
    private final NestedScrollView authScroll;
    private final View loginPanel;
    private final View registerPanel;

    AuthModeSwitcher(
            @NonNull AuthActivity activity,
            @NonNull NestedScrollView authScroll,
            @NonNull View loginPanel,
            @NonNull View registerPanel) {
        this.activity = activity;
        this.authScroll = authScroll;
        this.loginPanel = loginPanel;
        this.registerPanel = registerPanel;
    }

    void applyInitialState(boolean register) {
        authScroll.scrollTo(0, 0);
        if (register) {
            registerPanel.setVisibility(View.VISIBLE);
            registerPanel.setAlpha(1f);
            loginPanel.setVisibility(View.GONE);
            return;
        }

        loginPanel.setVisibility(View.VISIBLE);
        loginPanel.setAlpha(1f);
        registerPanel.setVisibility(View.GONE);
    }

    void switchMode(
            boolean showingRegister,
            boolean targetRegister,
            @NonNull Runnable onSwitchCompleted) {
        authScroll.scrollTo(0, 0);

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
                                    .withEndAction(onSwitchCompleted)
                                    .start())
                            .start();
                })
                .start();
    }

    private int dp(int value) {
        return Math.round(value * activity.getResources().getDisplayMetrics().density);
    }
}
