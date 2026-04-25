package com.coolcook.app.feature.splash.ui;

import android.content.Intent;
import android.os.Bundle;

import androidx.appcompat.app.AppCompatActivity;
import androidx.core.splashscreen.SplashScreen;

import com.coolcook.app.feature.home.ui.HomeActivity;
import com.coolcook.app.feature.main.ui.MainActivity;
import com.google.firebase.auth.FirebaseAuth;

public class SplashActivity extends AppCompatActivity {

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        SplashScreen.installSplashScreen(this);
        super.onCreate(savedInstanceState);

        Intent intent = checkCurrentUser()
                ? new Intent(this, HomeActivity.class)
                : new Intent(this, MainActivity.class);
        intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TASK);
        startActivity(intent);
        finish();
    }

    private boolean checkCurrentUser() {
        return FirebaseAuth.getInstance().getCurrentUser() != null;
    }
}
