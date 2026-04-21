package com.coolcook.app.ui.chatbot;

import android.content.Context;
import android.content.Intent;
import android.os.Bundle;
import android.text.TextUtils;
import android.view.View;
import android.view.ViewGroup;
import android.widget.EditText;
import android.widget.TextView;
import android.widget.Toast;

import androidx.activity.EdgeToEdge;
import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.graphics.Insets;
import androidx.core.view.ViewCompat;
import androidx.core.view.WindowInsetsCompat;

import com.coolcook.app.R;

public class ChatBotActivity extends AppCompatActivity {

    private View root;
    private View scroll;
    private View inputCard;
    private EditText edtPrompt;
    private TextView txtPreviewBubble;
    private ActivityResultLauncher<String> imagePickerLauncher;

    public static Intent createIntent(Context context) {
        return new Intent(context, ChatBotActivity.class);
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        EdgeToEdge.enable(this);
        setContentView(R.layout.activity_chatbot);

        bindViews();
        setupImagePicker();
        setupActions();
        applyInsets();
    }

    private void bindViews() {
        root = findViewById(R.id.chatbotRoot);
        scroll = findViewById(R.id.chatbotScroll);
        inputCard = findViewById(R.id.chatbotInputCard);
        edtPrompt = findViewById(R.id.edtChatbotPrompt);
        txtPreviewBubble = findViewById(R.id.txtChatbotBubble);
    }

    private void setupImagePicker() {
        imagePickerLauncher = registerForActivityResult(new ActivityResultContracts.GetContent(), uri -> {
            if (uri != null) {
                Toast.makeText(this, R.string.chatbot_upload_selected, Toast.LENGTH_SHORT).show();
            }
        });
    }

    private void setupActions() {
        View btnBack = findViewById(R.id.btnChatbotBack);
        View btnGrid = findViewById(R.id.btnChatbotGrid);
        View btnUpload = findViewById(R.id.btnChatbotUpload);
        View btnSend = findViewById(R.id.btnChatbotSend);

        btnBack.setOnClickListener(v -> getOnBackPressedDispatcher().onBackPressed());
        btnGrid.setOnClickListener(
                v -> Toast.makeText(this, R.string.chatbot_grid_coming_soon, Toast.LENGTH_SHORT).show());
        btnUpload.setOnClickListener(v -> imagePickerLauncher.launch("image/*"));
        btnSend.setOnClickListener(v -> submitPrompt());
    }

    private void submitPrompt() {
        String prompt = edtPrompt.getText() != null ? edtPrompt.getText().toString().trim() : "";
        if (TextUtils.isEmpty(prompt)) {
            Toast.makeText(this, R.string.chatbot_empty_prompt, Toast.LENGTH_SHORT).show();
            return;
        }

        txtPreviewBubble.setText(prompt);
        txtPreviewBubble.setVisibility(View.VISIBLE);
        edtPrompt.setText("");
        Toast.makeText(this, R.string.chatbot_sent_toast, Toast.LENGTH_SHORT).show();
    }

    private void applyInsets() {
        final int scrollLeft = scroll.getPaddingLeft();
        final int scrollTop = scroll.getPaddingTop();
        final int scrollRight = scroll.getPaddingRight();
        final int scrollBottom = scroll.getPaddingBottom();

        final ViewGroup.MarginLayoutParams inputLayoutParams = (ViewGroup.MarginLayoutParams) inputCard
                .getLayoutParams();
        final int inputStartMargin = inputLayoutParams.leftMargin;
        final int inputEndMargin = inputLayoutParams.rightMargin;
        final int inputBottomMargin = inputLayoutParams.bottomMargin;

        ViewCompat.setOnApplyWindowInsetsListener(root, (v, insets) -> {
            Insets systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars());
            Insets imeInsets = insets.getInsets(WindowInsetsCompat.Type.ime());
            int bottomInset = Math.max(systemBars.bottom, imeInsets.bottom);

            scroll.setPadding(
                    scrollLeft + systemBars.left,
                    scrollTop + systemBars.top,
                    scrollRight + systemBars.right,
                    scrollBottom);

            ViewGroup.MarginLayoutParams updatedInputParams = (ViewGroup.MarginLayoutParams) inputCard
                    .getLayoutParams();
            updatedInputParams.leftMargin = inputStartMargin + systemBars.left;
            updatedInputParams.rightMargin = inputEndMargin + systemBars.right;
            updatedInputParams.bottomMargin = inputBottomMargin + bottomInset;
            inputCard.setLayoutParams(updatedInputParams);

            return insets;
        });

        ViewCompat.requestApplyInsets(root);
    }
}