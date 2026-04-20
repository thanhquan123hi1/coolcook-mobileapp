package com.coolcook.app.ui.main;

import android.content.Intent;
import android.os.Bundle;
import android.widget.TextView;

import androidx.activity.EdgeToEdge;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.graphics.Insets;
import androidx.core.view.ViewCompat;
import androidx.core.view.WindowInsetsCompat;

import com.coolcook.app.R;
import com.coolcook.app.ui.auth.AuthActivity;
import com.coolcook.app.ui.home.HomeActivity;
import com.google.firebase.auth.FirebaseAuth;

public class MainActivity extends AppCompatActivity {

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        EdgeToEdge.enable(this);
        setContentView(R.layout.activity_main);
        ViewCompat.setOnApplyWindowInsetsListener(findViewById(R.id.main), (v, insets) -> {
            Insets systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars());
            v.setPadding(systemBars.left, systemBars.top, systemBars.right, systemBars.bottom);
            return insets;
        });

        findViewById(R.id.btnLogin).setOnClickListener(v -> openAuth(AuthActivity.MODE_LOGIN));

        TextView txtRegister = findViewById(R.id.txtRegister);
        txtRegister.setOnClickListener(v -> openAuth(AuthActivity.MODE_REGISTER));
    }

    @Override
    protected void onStart() {
        super.onStart();
        checkCurrentUser();
    }

    private void checkCurrentUser() {
        if (FirebaseAuth.getInstance().getCurrentUser() != null) {
            Intent intent = new Intent(this, HomeActivity.class);
            intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TASK);
            startActivity(intent);
            overridePendingTransition(R.anim.slide_in_right_scale, R.anim.slide_out_left_scale);
        }
    }

    private void openAuth(String mode) {
        Intent intent = AuthActivity.createIntent(this, mode);
        startActivity(intent);
        overridePendingTransition(R.anim.slide_in_right_scale, R.anim.slide_out_left_scale);
    }
}