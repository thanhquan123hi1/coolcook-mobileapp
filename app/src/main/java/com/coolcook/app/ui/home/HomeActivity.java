package com.coolcook.app.ui.home;

import android.os.Bundle;
import android.view.View;

import androidx.activity.EdgeToEdge;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.graphics.Insets;
import androidx.core.view.ViewCompat;
import androidx.core.view.WindowInsetsCompat;

import com.coolcook.app.R;

public class HomeActivity extends AppCompatActivity {

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        EdgeToEdge.enable(this);
        setContentView(R.layout.activity_home);
        applyInsets();
    }

    private void applyInsets() {
        View root = findViewById(R.id.homeRoot);
        View content = findViewById(R.id.homeContentContainer);
        View bottomNav = findViewById(R.id.homeBottomNav);

        final int contentLeft = content.getPaddingLeft();
        final int contentTop = content.getPaddingTop();
        final int contentRight = content.getPaddingRight();
        final int contentBottom = content.getPaddingBottom();

        final int navLeft = bottomNav.getPaddingLeft();
        final int navTop = bottomNav.getPaddingTop();
        final int navRight = bottomNav.getPaddingRight();
        final int navBottom = bottomNav.getPaddingBottom();

        ViewCompat.setOnApplyWindowInsetsListener(root, (v, insets) -> {
            Insets systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars());

            content.setPadding(
                    contentLeft + systemBars.left,
                    contentTop + systemBars.top,
                    contentRight + systemBars.right,
                    contentBottom);

            bottomNav.setPadding(
                    navLeft + systemBars.left,
                    navTop,
                    navRight + systemBars.right,
                    navBottom + systemBars.bottom);

            return insets;
        });
    }
}
