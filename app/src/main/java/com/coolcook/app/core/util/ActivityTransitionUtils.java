package com.coolcook.app.core.util;

import android.app.Activity;
import android.os.Build;

import androidx.annotation.AnimRes;
import androidx.annotation.NonNull;

public final class ActivityTransitionUtils {

    private ActivityTransitionUtils() {
    }

    public static void applyOpenTransition(
            @NonNull Activity activity,
            @AnimRes int enterAnim,
            @AnimRes int exitAnim) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
            activity.overrideActivityTransition(Activity.OVERRIDE_TRANSITION_OPEN, enterAnim, exitAnim);
            return;
        }
        applyLegacyTransition(activity, enterAnim, exitAnim);
    }

    public static void applyCloseTransition(
            @NonNull Activity activity,
            @AnimRes int enterAnim,
            @AnimRes int exitAnim) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
            activity.overrideActivityTransition(Activity.OVERRIDE_TRANSITION_CLOSE, enterAnim, exitAnim);
            return;
        }
        applyLegacyTransition(activity, enterAnim, exitAnim);
    }

    @SuppressWarnings("deprecation")
    private static void applyLegacyTransition(
            @NonNull Activity activity,
            @AnimRes int enterAnim,
            @AnimRes int exitAnim) {
        activity.overridePendingTransition(enterAnim, exitAnim);
    }
}
