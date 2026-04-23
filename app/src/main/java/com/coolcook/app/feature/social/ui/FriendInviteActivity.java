package com.coolcook.app.feature.social.ui;

import android.content.Context;
import android.content.Intent;
import android.net.Uri;
import android.os.Bundle;
import android.text.TextUtils;
import android.view.View;
import android.widget.ImageView;
import android.widget.ProgressBar;
import android.widget.TextView;
import android.widget.Toast;

import androidx.activity.EdgeToEdge;
import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.graphics.Insets;
import androidx.core.view.ViewCompat;
import androidx.core.view.WindowInsetsCompat;

import com.bumptech.glide.Glide;
import com.coolcook.app.R;
import com.coolcook.app.feature.camera.ui.ScanFoodActivity;
import com.coolcook.app.feature.social.data.FriendInviteRepository;
import com.coolcook.app.feature.social.model.FriendInvite;
import com.coolcook.app.feature.auth.ui.AuthActivity;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.FirebaseUser;
import com.google.firebase.firestore.FirebaseFirestore;

public class FriendInviteActivity extends AppCompatActivity {

    private static final String PREFS_NAME = "coolcook.friend_invites";
    private static final String KEY_PENDING_INVITE_ID = "pending_invite_id";

    private View root;
    private View inviteCard;
    private ImageView imgInviteAvatar;
    private TextView txtInviteTitle;
    private TextView txtInviteSubtitle;
    private TextView txtInviteStatus;
    private TextView btnAcceptInvite;
    private TextView btnInviteClose;
    private ProgressBar inviteLoading;

    private FriendInviteRepository friendInviteRepository;
    private String inviteId = "";
    private FriendInvite currentInvite;
    private boolean isLoading;

    public static void savePendingInvite(@NonNull Context context, @NonNull String inviteId) {
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
                .edit()
                .putString(KEY_PENDING_INVITE_ID, inviteId)
                .apply();
    }

    @NonNull
    public static String consumePendingInvite(@NonNull Context context) {
        String inviteId = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
                .getString(KEY_PENDING_INVITE_ID, "");
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
                .edit()
                .remove(KEY_PENDING_INVITE_ID)
                .apply();
        return inviteId == null ? "" : inviteId;
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        EdgeToEdge.enable(this);
        setContentView(R.layout.activity_friend_invite);

        friendInviteRepository = new FriendInviteRepository(FirebaseFirestore.getInstance());

        bindViews();
        applyInsets();
        setupActions();

        inviteId = resolveInviteId(getIntent());
        loadInvite();
    }

    @Override
    protected void onNewIntent(Intent intent) {
        super.onNewIntent(intent);
        setIntent(intent);
        inviteId = resolveInviteId(intent);
        loadInvite();
    }

    private void bindViews() {
        root = findViewById(R.id.friendInviteRoot);
        inviteCard = findViewById(R.id.inviteCard);
        imgInviteAvatar = findViewById(R.id.imgInviteAvatar);
        txtInviteTitle = findViewById(R.id.txtInviteTitle);
        txtInviteSubtitle = findViewById(R.id.txtInviteSubtitle);
        txtInviteStatus = findViewById(R.id.txtInviteStatus);
        btnAcceptInvite = findViewById(R.id.btnAcceptInvite);
        btnInviteClose = findViewById(R.id.btnInviteClose);
        inviteLoading = findViewById(R.id.inviteLoading);
    }

    private void applyInsets() {
        final int baseCardTop = inviteCard.getPaddingTop();
        final int baseCardBottom = inviteCard.getPaddingBottom();

        ViewCompat.setOnApplyWindowInsetsListener(root, (v, insets) -> {
            Insets systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars());
            v.setPadding(systemBars.left, systemBars.top, systemBars.right, systemBars.bottom);
            inviteCard.setPadding(
                    inviteCard.getPaddingLeft(),
                    baseCardTop,
                    inviteCard.getPaddingRight(),
                    baseCardBottom);
            return insets;
        });
        ViewCompat.requestApplyInsets(root);
    }

    private void setupActions() {
        btnInviteClose.setOnClickListener(v -> finish());
        btnAcceptInvite.setOnClickListener(v -> acceptInvite());
    }

    private void loadInvite() {
        if (TextUtils.isEmpty(inviteId)) {
            renderError("Link mời không hợp lệ.");
            return;
        }

        setLoading(true);
        txtInviteStatus.setText("");
        txtInviteSubtitle.setText("Đang tải thông tin lời mời...");

        friendInviteRepository.loadInvite(inviteId, new FriendInviteRepository.LoadInviteCallback() {
            @Override
            public void onSuccess(@NonNull FriendInvite invite) {
                currentInvite = invite;
                setLoading(false);
                renderInvite(invite);
            }

            @Override
            public void onError(@NonNull String message) {
                currentInvite = null;
                setLoading(false);
                renderError(message);
            }
        });
    }

    private void renderInvite(@NonNull FriendInvite invite) {
        txtInviteTitle.setText(invite.getCreatedByName());
        txtInviteSubtitle.setText("muốn kết bạn với bạn trên CoolCook.");
        txtInviteStatus.setText(invite.isActive() ? "Link còn hiệu lực" : "Link không còn hiệu lực");
        btnAcceptInvite.setEnabled(true);
        btnAcceptInvite.setAlpha(1f);

        Glide.with(this)
                .load(invite.getCreatedByAvatarUrl())
                .placeholder(R.drawable.img_home_profile)
                .error(R.drawable.img_home_profile)
                .circleCrop()
                .into(imgInviteAvatar);
    }

    private void renderError(@NonNull String message) {
        txtInviteTitle.setText("Lời mời kết bạn");
        txtInviteSubtitle.setText(message);
        txtInviteStatus.setText("");
        btnAcceptInvite.setEnabled(false);
        btnAcceptInvite.setAlpha(0.55f);
        imgInviteAvatar.setImageResource(R.drawable.img_home_profile);
        Toast.makeText(this, message, Toast.LENGTH_SHORT).show();
    }

    private void acceptInvite() {
        if (isLoading) {
            return;
        }
        if (currentInvite == null) {
            renderError("Link mời không còn dùng được.");
            return;
        }

        FirebaseUser user = FirebaseAuth.getInstance().getCurrentUser();
        if (user == null) {
            savePendingInvite(this, inviteId);
            Toast.makeText(this, "Vui lòng đăng nhập để xác nhận lời mời.", Toast.LENGTH_SHORT).show();
            startActivity(AuthActivity.createIntent(this, AuthActivity.MODE_LOGIN));
            return;
        }

        setLoading(true);
        txtInviteStatus.setText("Đang xác nhận...");
        friendInviteRepository.acceptInvite(inviteId, user, new FriendInviteRepository.AcceptInviteCallback() {
            @Override
            public void onSuccess(@NonNull String message) {
                setLoading(false);
                Toast.makeText(FriendInviteActivity.this, message, Toast.LENGTH_SHORT).show();
                Intent intent = ScanFoodActivity.createJournalIntent(FriendInviteActivity.this);
                intent.addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP | Intent.FLAG_ACTIVITY_SINGLE_TOP);
                startActivity(intent);
                finish();
            }

            @Override
            public void onError(@NonNull String message) {
                setLoading(false);
                txtInviteStatus.setText(message);
                Toast.makeText(FriendInviteActivity.this, message, Toast.LENGTH_SHORT).show();
            }
        });
    }

    private void setLoading(boolean loading) {
        isLoading = loading;
        inviteLoading.setVisibility(loading ? View.VISIBLE : View.GONE);
        btnInviteClose.setEnabled(!loading);
        btnInviteClose.setAlpha(loading ? 0.65f : 1f);
        btnAcceptInvite.setEnabled(!loading && currentInvite != null);
        btnAcceptInvite.setAlpha((loading || currentInvite == null) ? 0.65f : 1f);
    }

    @NonNull
    private String resolveInviteId(@NonNull Intent intent) {
        String extraInviteId = intent.getStringExtra("inviteId");
        if (!TextUtils.isEmpty(extraInviteId)) {
            return extraInviteId;
        }
        Uri data = intent.getData();
        return FriendInviteRepository.parseInviteId(data);
    }
}
