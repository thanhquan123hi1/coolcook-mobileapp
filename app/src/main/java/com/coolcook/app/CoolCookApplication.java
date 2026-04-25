package com.coolcook.app;

import android.app.Application;

import com.coolcook.app.core.theme.ThemeManager;

public class CoolCookApplication extends Application {

    @Override
    public void onCreate() {
        super.onCreate();
        ThemeManager.applySavedTheme(this);
    }
}
