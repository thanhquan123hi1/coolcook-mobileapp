package com.coolcook.app.feature.chatbot.ui;

import android.animation.ObjectAnimator;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.net.Uri;
import android.os.Bundle;
import android.text.TextUtils;
import android.util.Base64;
import android.view.View;
import android.view.ViewGroup;
import android.view.inputmethod.InputMethodManager;
import android.widget.EditText;
import android.widget.TextView;
import android.widget.Toast;
import android.transition.AutoTransition;
import android.transition.TransitionManager;

import androidx.activity.EdgeToEdge;
import androidx.activity.OnBackPressedCallback;
import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.graphics.Insets;
import androidx.core.view.GravityCompat;
import androidx.core.view.ViewCompat;
import androidx.core.view.WindowInsetsCompat;
import androidx.drawerlayout.widget.DrawerLayout;
import androidx.lifecycle.ViewModelProvider;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.coolcook.app.R;
import com.coolcook.app.feature.chatbot.data.GeminiRepository;
import com.coolcook.app.feature.chatbot.model.ChatMessage;
import com.coolcook.app.feature.chatbot.model.ChatSession;
import com.coolcook.app.feature.chatbot.ui.adapter.ChatMessageAdapter;
import com.coolcook.app.feature.chatbot.ui.adapter.ChatSessionAdapter;
import com.google.android.material.dialog.MaterialAlertDialogBuilder;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.FirebaseUser;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.List;

public class ChatBotActivity extends AppCompatActivity {

    private static final int MAX_IMAGE_BYTES = 4 * 1024 * 1024;

    private DrawerLayout drawer;
    private View root;
    private View header;
    private View intro;
    private RecyclerView rvMessages;
    private View inputCard;
    private EditText edtPrompt;
    private TextView txtAttachedImage;
    private View typingIndicatorGroup;
    private TextView dot1;
    private TextView dot2;
    private TextView dot3;
    private View btnUpload;
    private View btnSend;

    private ChatViewModel viewModel;
    private ChatMessageAdapter messageAdapter;

    private ActivityResultLauncher<String> imagePickerLauncher;
    private ChatSessionAdapter sessionAdapter;
    private TextView txtNoSessions;
    private RecyclerView rvSessions;

    private ObjectAnimator dot1Anim;
    private ObjectAnimator dot2Anim;
    private ObjectAnimator dot3Anim;
    private boolean isChatMode;
    private boolean isLoadingState;

    private String pendingImageMimeType;
    private String pendingImageBase64;

    public static Intent createIntent(Context context) {
        return new Intent(context, ChatBotActivity.class);
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        EdgeToEdge.enable(this);
        setContentView(R.layout.activity_chatbot);

        bindViews();
        setupBackHandling();
        setupRecyclerView();
        setupImagePicker();
        setupQuickPrompts();
        setupActions();
        setupSessionPanel();
        setupViewModel();
        applyInsets();
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        if (dot1Anim != null) dot1Anim.cancel();
        if (dot2Anim != null) dot2Anim.cancel();
        if (dot3Anim != null) dot3Anim.cancel();
    }

    private void bindViews() {
        drawer = findViewById(R.id.chatbotDrawer);
        root = findViewById(R.id.chatbotRoot);
        header = findViewById(R.id.chatbotHeader);
        intro = findViewById(R.id.chatbotIntro);
        rvMessages = findViewById(R.id.rvChatMessages);
        inputCard = findViewById(R.id.chatbotInputCard);
        edtPrompt = findViewById(R.id.edtChatbotPrompt);
        txtAttachedImage = findViewById(R.id.txtAttachedImage);
        typingIndicatorGroup = findViewById(R.id.typingIndicatorGroup);
        dot1 = findViewById(R.id.dot1);
        dot2 = findViewById(R.id.dot2);
        dot3 = findViewById(R.id.dot3);
        txtNoSessions = findViewById(R.id.txtNoSessions);
        rvSessions = findViewById(R.id.rvChatSessions);
    }

    private void setupBackHandling() {
        getOnBackPressedDispatcher().addCallback(this, new OnBackPressedCallback(true) {
            @Override
            public void handleOnBackPressed() {
                if (drawer != null && drawer.isDrawerOpen(GravityCompat.END)) {
                    drawer.closeDrawer(GravityCompat.END);
                    return;
                }

                setEnabled(false);
                getOnBackPressedDispatcher().onBackPressed();
                setEnabled(true);
            }
        });
    }

    private void setupRecyclerView() {
        rvMessages.setLayoutManager(new LinearLayoutManager(this));
        messageAdapter = new ChatMessageAdapter();
        rvMessages.setAdapter(messageAdapter);
    }

    private void setupImagePicker() {
        imagePickerLauncher = registerForActivityResult(new ActivityResultContracts.GetContent(),
                this::handleImagePicked);
    }

    private void setupQuickPrompts() {
        setupPromptChip(R.id.chipPrompt1, getString(R.string.chatbot_quick_prompt_1), false);
        setupPromptChip(R.id.chipPrompt2, getString(R.string.chatbot_quick_prompt_2), false);
        setupPromptChip(R.id.chipPrompt3, getString(R.string.chatbot_quick_prompt_3), false);
        setupPromptChip(R.id.chipPrompt4, getString(R.string.chatbot_quick_prompt_4), true);
    }

    private void setupPromptChip(int chipId, @NonNull String prompt, boolean openImagePicker) {
        View chip = findViewById(chipId);
        chip.setOnClickListener(v -> {
            edtPrompt.setText(prompt);
            edtPrompt.setSelection(prompt.length());
            edtPrompt.requestFocus();
            if (openImagePicker) {
                imagePickerLauncher.launch("image/*");
            }
        });
    }

    private void setupActions() {
        View btnBack = findViewById(R.id.btnChatbotBack);
        View btnGrid = findViewById(R.id.btnChatbotGrid);
        btnUpload = findViewById(R.id.btnChatbotUpload);
        btnSend = findViewById(R.id.btnChatbotSend);

        btnBack.setOnClickListener(v -> getOnBackPressedDispatcher().onBackPressed());
        btnGrid.setOnClickListener(v -> openHistoryDrawer());
        btnUpload.setOnClickListener(v -> imagePickerLauncher.launch("image/*"));
        btnSend.setOnClickListener(v -> submitPrompt());
    }

    private void setupSessionPanel() {
        rvSessions.setLayoutManager(new LinearLayoutManager(this));
        sessionAdapter = new ChatSessionAdapter(new ChatSessionAdapter.SessionActionListener() {
            @Override
            public void onSessionClick(@NonNull ChatSession session) {
                viewModel.loadSession(session.getId());
                if (drawer != null) {
                    drawer.closeDrawer(GravityCompat.END);
                }
            }

            @Override
            public void onSessionLongClick(@NonNull ChatSession session) {
                showDeleteSessionDialog(session);
            }
        });
        rvSessions.setAdapter(sessionAdapter);

        View btnNewChat = findViewById(R.id.btnNewChat);
        btnNewChat.setOnClickListener(v -> {
            viewModel.startNewChat();
            if (drawer != null) {
                drawer.closeDrawer(GravityCompat.END);
            }
        });
    }

    private void setupViewModel() {
        viewModel = new ViewModelProvider(this).get(ChatViewModel.class);

        FirebaseUser firebaseUser = FirebaseAuth.getInstance().getCurrentUser();
        if (firebaseUser != null) {
            viewModel.setUserId(firebaseUser.getUid());
        } else {
            Toast.makeText(this, R.string.chatbot_need_login, Toast.LENGTH_SHORT).show();
        }

        viewModel.getMessages().observe(this, this::renderMessages);
        viewModel.isLoading().observe(this, this::renderLoading);
        viewModel.getUiMessage().observe(this, this::renderToastMessage);
        viewModel.getSessions().observe(this, sessions -> {
            if (sessionAdapter != null) {
                sessionAdapter.submitSessions(sessions);
            }
            if (txtNoSessions != null) {
                txtNoSessions.setVisibility(sessions.isEmpty() ? View.VISIBLE : View.GONE);
            }
        });
        viewModel.getActiveSessionId().observe(this, sessionId -> {
            if (sessionAdapter != null) {
                sessionAdapter.setActiveSessionId(sessionId == null ? "" : sessionId);
            }
        });
    }

    private void renderMessages(@NonNull List<ChatMessage> messages) {
        updateScreenState(!messages.isEmpty(), true);
        messageAdapter.submitMessages(messages);
        if (!messages.isEmpty()) {
            rvMessages.smoothScrollToPosition(messages.size() - 1);
        }
    }

    private void renderLoading(Boolean isLoading) {
        boolean loading = isLoading != null && isLoading;
        isLoadingState = loading;
        typingIndicatorGroup.setVisibility(loading && isChatMode ? View.VISIBLE : View.GONE);
        if (btnSend != null) {
            btnSend.setEnabled(!loading);
            btnSend.setAlpha(loading ? 0.6f : 1f);
        }
        if (btnUpload != null) {
            btnUpload.setEnabled(!loading);
            btnUpload.setAlpha(loading ? 0.7f : 1f);
        }

        if (loading) {
            startTypingAnimation();
        } else {
            stopTypingAnimation();
        }
    }

    private void renderToastMessage(String message) {
        if (!TextUtils.isEmpty(message)) {
            Toast.makeText(this, message, Toast.LENGTH_SHORT).show();
        }
    }

    private void openHistoryDrawer() {
        viewModel.loadSessions();
        if (drawer != null) {
            drawer.openDrawer(GravityCompat.END);
        }
    }

    private void showDeleteSessionDialog(@NonNull ChatSession session) {
        new MaterialAlertDialogBuilder(this)
                .setTitle(R.string.chatbot_delete_session_title)
                .setMessage(getString(R.string.chatbot_delete_session_message, session.getTitle()))
                .setNegativeButton(R.string.chatbot_delete_cancel, null)
                .setPositiveButton(R.string.chatbot_delete_confirm,
                        (DialogInterface dialog, int which) -> viewModel.deleteSession(session.getId()))
                .show();
    }

    private void startTypingAnimation() {
        if (dot1Anim == null) {
            dot1Anim = ObjectAnimator.ofFloat(dot1, View.TRANSLATION_Y, 0f, -15f, 0f);
            dot1Anim.setDuration(600);
            dot1Anim.setRepeatCount(ObjectAnimator.INFINITE);
            dot1Anim.setStartDelay(0);

            dot2Anim = ObjectAnimator.ofFloat(dot2, View.TRANSLATION_Y, 0f, -15f, 0f);
            dot2Anim.setDuration(600);
            dot2Anim.setRepeatCount(ObjectAnimator.INFINITE);
            dot2Anim.setStartDelay(150);

            dot3Anim = ObjectAnimator.ofFloat(dot3, View.TRANSLATION_Y, 0f, -15f, 0f);
            dot3Anim.setDuration(600);
            dot3Anim.setRepeatCount(ObjectAnimator.INFINITE);
            dot3Anim.setStartDelay(300);
        }
        if (!dot1Anim.isStarted()) dot1Anim.start();
        if (!dot2Anim.isStarted()) dot2Anim.start();
        if (!dot3Anim.isStarted()) dot3Anim.start();
    }

    private void stopTypingAnimation() {
        if (dot1Anim != null) {
            dot1Anim.cancel();
            dot1.setTranslationY(0f);
        }
        if (dot2Anim != null) {
            dot2Anim.cancel();
            dot2.setTranslationY(0f);
        }
        if (dot3Anim != null) {
            dot3Anim.cancel();
            dot3.setTranslationY(0f);
        }
    }

    private void submitPrompt() {
        if (isLoadingState) {
            Toast.makeText(this, "Mình đang trả lời nè, đợi mình xíu nha.", Toast.LENGTH_SHORT).show();
            return;
        }

        String prompt = edtPrompt.getText() != null ? edtPrompt.getText().toString().trim() : "";
        if (TextUtils.isEmpty(prompt) && TextUtils.isEmpty(pendingImageBase64)) {
            Toast.makeText(this, R.string.chatbot_empty_prompt, Toast.LENGTH_SHORT).show();
            return;
        }

        viewModel.sendMessage(prompt, pendingImageMimeType, pendingImageBase64);
        hideKeyboard();
        edtPrompt.setText("");
        clearPendingImage();
    }

    private void hideKeyboard() {
        InputMethodManager imm = (InputMethodManager) getSystemService(Context.INPUT_METHOD_SERVICE);
        View target = getCurrentFocus();
        if (target == null) {
            target = edtPrompt;
        }
        if (imm != null && target != null) {
            imm.hideSoftInputFromWindow(target.getWindowToken(), 0);
        }
        edtPrompt.clearFocus();
    }

    private void updateScreenState(boolean chatMode, boolean animate) {
        if (isChatMode == chatMode
                && intro.getVisibility() == (chatMode ? View.GONE : View.VISIBLE)
                && rvMessages.getVisibility() == (chatMode ? View.VISIBLE : View.GONE)) {
            return;
        }

        isChatMode = chatMode;
        if (animate && root instanceof ViewGroup) {
            AutoTransition transition = new AutoTransition();
            transition.setDuration(180L);
            TransitionManager.beginDelayedTransition((ViewGroup) root, transition);
        }

        intro.setVisibility(chatMode ? View.GONE : View.VISIBLE);
        rvMessages.setVisibility(chatMode ? View.VISIBLE : View.GONE);
        if (!chatMode) {
            typingIndicatorGroup.setVisibility(View.GONE);
            stopTypingAnimation();
        }
        root.setBackgroundResource(R.color.chatbot_background);
    }

    private void handleImagePicked(Uri uri) {
        if (uri == null) {
            return;
        }

        try {
            String mimeType = GeminiRepository.detectMimeType(getContentResolver(), uri);
            byte[] raw = readImageBytes(uri);
            if (raw.length > MAX_IMAGE_BYTES) {
                Toast.makeText(this, R.string.chatbot_image_too_large, Toast.LENGTH_SHORT).show();
                clearPendingImage();
                return;
            }

            pendingImageMimeType = mimeType;
            pendingImageBase64 = Base64.encodeToString(raw, Base64.NO_WRAP);
            txtAttachedImage.setVisibility(View.VISIBLE);
            Toast.makeText(this, R.string.chatbot_upload_selected, Toast.LENGTH_SHORT).show();
        } catch (IOException error) {
            Toast.makeText(this, R.string.chatbot_ai_error, Toast.LENGTH_SHORT).show();
            clearPendingImage();
        }
    }

    @NonNull
    private byte[] readImageBytes(@NonNull Uri uri) throws IOException {
        try (InputStream inputStream = getContentResolver().openInputStream(uri);
                ByteArrayOutputStream outputStream = new ByteArrayOutputStream()) {

            if (inputStream == null) {
                throw new IOException("Cannot open selected image");
            }

            byte[] buffer = new byte[8 * 1024];
            int read;
            int total = 0;
            while ((read = inputStream.read(buffer)) != -1) {
                total += read;
                if (total > MAX_IMAGE_BYTES) {
                    return new byte[MAX_IMAGE_BYTES + 1];
                }
                outputStream.write(buffer, 0, read);
            }
            return outputStream.toByteArray();
        }
    }

    private void clearPendingImage() {
        pendingImageMimeType = null;
        pendingImageBase64 = null;
        txtAttachedImage.setVisibility(View.GONE);
    }

    private void applyInsets() {
        final int headerStart = header.getPaddingStart();
        final int headerTop = header.getPaddingTop();
        final int headerEnd = header.getPaddingEnd();
        final int headerBottom = header.getPaddingBottom();

        final int introStart = intro.getPaddingStart();
        final int introTop = intro.getPaddingTop();
        final int introEnd = intro.getPaddingEnd();
        final int introBottom = intro.getPaddingBottom();

        final int listStart = rvMessages.getPaddingStart();
        final int listTop = rvMessages.getPaddingTop();
        final int listEnd = rvMessages.getPaddingEnd();
        final int listBottom = rvMessages.getPaddingBottom();

        final ViewGroup.MarginLayoutParams inputLayout = (ViewGroup.MarginLayoutParams) inputCard.getLayoutParams();
        final int inputStart = inputLayout.leftMargin;
        final int inputEnd = inputLayout.rightMargin;
        final int inputBottom = inputLayout.bottomMargin;

        ViewCompat.setOnApplyWindowInsetsListener(root, (v, insets) -> {
            Insets bars = insets.getInsets(WindowInsetsCompat.Type.systemBars());
            Insets ime = insets.getInsets(WindowInsetsCompat.Type.ime());
            int bottomInset = Math.max(bars.bottom, ime.bottom);

            header.setPadding(headerStart + bars.left, headerTop + bars.top, headerEnd + bars.right, headerBottom);
            intro.setPadding(introStart + bars.left, introTop, introEnd + bars.right, introBottom);
            rvMessages.setPadding(listStart + bars.left, listTop, listEnd + bars.right, listBottom);

            ViewGroup.MarginLayoutParams params = (ViewGroup.MarginLayoutParams) inputCard.getLayoutParams();
            params.leftMargin = inputStart + bars.left;
            params.rightMargin = inputEnd + bars.right;
            params.bottomMargin = inputBottom + bottomInset;
            inputCard.setLayoutParams(params);

            return insets;
        });

        ViewCompat.requestApplyInsets(root);
        updateScreenState(false, false);
    }

}
