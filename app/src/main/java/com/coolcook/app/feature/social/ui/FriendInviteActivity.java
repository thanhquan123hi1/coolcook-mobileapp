package com.coolcook.app.feature.social.ui;

import android.content.ClipData;
import android.content.ClipboardManager;
import android.content.Context;
import android.content.Intent;
import android.net.Uri;
import android.os.Bundle;
import android.text.TextUtils;
import android.view.View;
import android.widget.EditText;
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
import com.coolcook.app.feature.auth.ui.AuthActivity;
import com.coolcook.app.feature.camera.ui.ScanFoodActivity;
import com.coolcook.app.feature.social.data.FriendInviteRepository;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.FirebaseUser;
import com.google.firebase.firestore.FirebaseFirestore;

public class FriendInviteActivity extends AppCompatActivity {

    private static final String PREFS_NAME = "coolcook.friend_invites";
    private static final String KEY_PENDING_INVITE_ID = "pending_invite_id";
    public static final String PENDING_OPEN_SELF_TOKEN = "__open_self__";

    private View root;
    private View inviteCard;
    private ImageView imgInviteAvatar;
    private TextView txtInviteTitle;
    private TextView txtInviteSubtitle;
    private TextView txtInviteStatus;
    private TextView txtFriendInviteCode;
    private EditText edtFriendInviteCode;
    private TextView btnCreateInviteCode;
    private TextView btnShareInviteCode;
    private TextView btnAcceptInvite;
    private TextView btnInviteClose;
    private ProgressBar inviteLoading;

    private FriendInviteRepository friendInviteRepository;
    private String pendingFriendCode = "";
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

        pendingFriendCode = resolveInviteId(getIntent());
        refreshCurrentUserCode();
        if (!TextUtils.isEmpty(pendingFriendCode) && edtFriendInviteCode != null) {
            edtFriendInviteCode.setText(pendingFriendCode);
            edtFriendInviteCode.setSelection(edtFriendInviteCode.getText().length());
        }
    }

    @Override
    protected void onNewIntent(Intent intent) {
        super.onNewIntent(intent);
        setIntent(intent);
        pendingFriendCode = resolveInviteId(intent);
        if (!TextUtils.isEmpty(pendingFriendCode) && edtFriendInviteCode != null) {
            edtFriendInviteCode.setText(pendingFriendCode);
            edtFriendInviteCode.setSelection(edtFriendInviteCode.getText().length());
        }
    }

    @Override
    protected void onResume() {
        super.onResume();
        refreshCurrentUserCode();
    }

    private void bindViews() {
        root = findViewById(R.id.friendInviteRoot);
        inviteCard = findViewById(R.id.inviteCard);
        imgInviteAvatar = findViewById(R.id.imgInviteAvatar);
        txtInviteTitle = findViewById(R.id.txtInviteTitle);
        txtInviteSubtitle = findViewById(R.id.txtInviteSubtitle);
        txtInviteStatus = findViewById(R.id.txtInviteStatus);
        txtFriendInviteCode = findViewById(R.id.txtFriendInviteCode);
        edtFriendInviteCode = findViewById(R.id.edtFriendInviteCode);
        btnCreateInviteCode = findViewById(R.id.btnCreateInviteCode);
        btnShareInviteCode = findViewById(R.id.btnShareInviteCode);
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
        btnCreateInviteCode.setOnClickListener(v -> copyOwnFriendCode());
        btnShareInviteCode.setOnClickListener(v -> shareOwnFriendCode());
        btnAcceptInvite.setOnClickListener(v -> acceptInvite());
    }

    private void refreshCurrentUserCode() {
        FirebaseUser user = FirebaseAuth.getInstance().getCurrentUser();
        if (user == null) {
            setLoading(false);
            txtInviteTitle.setText("Đăng nhập để kết bạn");
            txtInviteStatus.setText("Đăng nhập để nhận mã kết bạn hoặc nhập mã của bạn bè.");
            txtFriendInviteCode.setText("--------");
            btnCreateInviteCode.setText("Đăng nhập để lấy mã");
            btnCreateInviteCode.setEnabled(true);
            btnShareInviteCode.setVisibility(View.GONE);
            Glide.with(this)
                    .load(R.drawable.img_home_profile)
                    .circleCrop()
                    .into(imgInviteAvatar);
            return;
        }

        setLoading(true);
        txtInviteTitle.setText("Kết bạn trên CoolCook");
        txtInviteStatus.setText("Đang tải mã kết bạn...");
        txtInviteSubtitle.setText("Mã này là nhận diện cố định của bạn trên CoolCook.");
        btnCreateInviteCode.setText("Sao chép mã");
        btnShareInviteCode.setVisibility(View.VISIBLE);
        Glide.with(this)
                .load(user.getPhotoUrl())
                .placeholder(R.drawable.img_home_profile)
                .error(R.drawable.img_home_profile)
                .circleCrop()
                .into(imgInviteAvatar);

        friendInviteRepository.ensureFriendCode(user, new FriendInviteRepository.EnsureFriendCodeCallback() {
            @Override
            public void onSuccess(@NonNull String friendCode) {
                runOnUiThread(() -> {
                    setLoading(false);
                    txtFriendInviteCode.setText(friendCode);
                    txtInviteStatus.setText("Mã kết bạn của bạn đã sẵn sàng.");
                    btnCreateInviteCode.setEnabled(true);
                });
            }

            @Override
            public void onError(@NonNull String message) {
                runOnUiThread(() -> {
                    setLoading(false);
                    txtInviteStatus.setText(message);
                    txtFriendInviteCode.setText("--------");
                    btnCreateInviteCode.setText("Thử lấy mã lại");
                    btnCreateInviteCode.setEnabled(true);
                    btnShareInviteCode.setVisibility(View.GONE);
                    Toast.makeText(FriendInviteActivity.this, message, Toast.LENGTH_SHORT).show();
                });
            }
        });
    }

    private void copyOwnFriendCode() {
        FirebaseUser user = FirebaseAuth.getInstance().getCurrentUser();
        if (user == null) {
            startActivity(AuthActivity.createIntent(this, AuthActivity.MODE_LOGIN));
            return;
        }

        String code = txtFriendInviteCode == null ? "" : String.valueOf(txtFriendInviteCode.getText()).trim();
        if (TextUtils.isEmpty(code) || "--------".contentEquals(code)) {
            refreshCurrentUserCode();
            return;
        }

        ClipboardManager clipboard = (ClipboardManager) getSystemService(Context.CLIPBOARD_SERVICE);
        if (clipboard != null) {
            clipboard.setPrimaryClip(ClipData.newPlainText("CoolCook friend code", code));
        }
        Toast.makeText(this, "Đã sao chép mã kết bạn", Toast.LENGTH_SHORT).show();
    }

    private void shareOwnFriendCode() {
        FirebaseUser user = FirebaseAuth.getInstance().getCurrentUser();
        if (user == null) {
            startActivity(AuthActivity.createIntent(this, AuthActivity.MODE_LOGIN));
            return;
        }

        String code = txtFriendInviteCode == null ? "" : String.valueOf(txtFriendInviteCode.getText()).trim();
        if (TextUtils.isEmpty(code) || "--------".contentEquals(code)) {
            refreshCurrentUserCode();
            return;
        }

        Intent shareIntent = new Intent(Intent.ACTION_SEND);
        shareIntent.setType("text/plain");
        shareIntent.putExtra(Intent.EXTRA_TEXT, "Mã kết bạn CoolCook của mình là: " + code);
        startActivity(Intent.createChooser(shareIntent, "Gửi lời mời kết bạn"));
    }

    private void acceptInvite() {
        if (isLoading) {
            return;
        }

        FirebaseUser user = FirebaseAuth.getInstance().getCurrentUser();
        if (user == null) {
            savePendingInvite(this, resolveInputCode());
            startActivity(AuthActivity.createIntent(this, AuthActivity.MODE_LOGIN));
            return;
        }

        String friendCode = resolveInputCode();
        if (TextUtils.isEmpty(friendCode)) {
            Toast.makeText(this, "Vui lòng nhập mã kết bạn", Toast.LENGTH_SHORT).show();
            return;
        }

        setLoading(true);
        txtInviteStatus.setText("Đang kết nối...");
        friendInviteRepository.acceptInvite(friendCode, user, new FriendInviteRepository.AcceptInviteCallback() {
            @Override
            public void onSuccess(@NonNull String message) {
                runOnUiThread(() -> {
                    setLoading(false);
                    txtInviteStatus.setText(message);
                    Toast.makeText(FriendInviteActivity.this, message, Toast.LENGTH_SHORT).show();
                    Intent intent = ScanFoodActivity.createJournalIntent(FriendInviteActivity.this);
                    intent.addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP | Intent.FLAG_ACTIVITY_SINGLE_TOP);
                    startActivity(intent);
                    finish();
                });
            }

            @Override
            public void onError(@NonNull String message) {
                runOnUiThread(() -> {
                    setLoading(false);
                    txtInviteStatus.setText(message);
                    Toast.makeText(FriendInviteActivity.this, message, Toast.LENGTH_SHORT).show();
                });
            }
        });
    }

    private void setLoading(boolean loading) {
        isLoading = loading;
        inviteLoading.setVisibility(loading ? View.VISIBLE : View.GONE);
        btnInviteClose.setEnabled(!loading);
        btnInviteClose.setAlpha(loading ? 0.65f : 1f);
        btnCreateInviteCode.setEnabled(!loading);
        btnCreateInviteCode.setAlpha(loading ? 0.65f : 1f);
        btnShareInviteCode.setEnabled(!loading);
        btnShareInviteCode.setAlpha(loading ? 0.65f : 1f);
        btnAcceptInvite.setEnabled(!loading);
        btnAcceptInvite.setAlpha(loading ? 0.65f : 1f);
    }

    @NonNull
    private String resolveInputCode() {
        return edtFriendInviteCode == null
                ? ""
                : String.valueOf(edtFriendInviteCode.getText()).trim().replace(" ", "");
    }

    @NonNull
    private String resolveInviteId(@NonNull Intent intent) {
        String extraInviteId = intent.getStringExtra("inviteId");
        if (!TextUtils.isEmpty(extraInviteId)) {
            return PENDING_OPEN_SELF_TOKEN.equals(extraInviteId) ? "" : extraInviteId;
        }
        Uri data = intent.getData();
        return FriendInviteRepository.parseInviteId(data);
    }
}
