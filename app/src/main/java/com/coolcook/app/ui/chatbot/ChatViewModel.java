package com.coolcook.app.ui.chatbot;

import android.text.TextUtils;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.lifecycle.LiveData;
import androidx.lifecycle.MutableLiveData;
import androidx.lifecycle.ViewModel;

import com.coolcook.app.ui.chatbot.data.ChatRepository;
import com.coolcook.app.ui.chatbot.data.GeminiRepository;
import com.coolcook.app.ui.chatbot.model.ChatMessage;
import com.coolcook.app.ui.chatbot.model.ChatSession;
import com.google.firebase.firestore.FirebaseFirestore;

import java.util.ArrayList;
import java.util.List;

public class ChatViewModel extends ViewModel {

    private final ChatRepository chatRepository;
    private final GeminiRepository geminiRepository;

    private final MutableLiveData<List<ChatMessage>> messages = new MutableLiveData<>(new ArrayList<>());
    private final MutableLiveData<List<ChatSession>> sessions = new MutableLiveData<>(new ArrayList<>());
    private final MutableLiveData<Boolean> loading = new MutableLiveData<>(false);
    private final MutableLiveData<String> uiMessage = new MutableLiveData<>("");
    private final MutableLiveData<String> activeSessionId = new MutableLiveData<>("");

    private String userId = "";

    public ChatViewModel() {
        this.chatRepository = new ChatRepository(FirebaseFirestore.getInstance());
        this.geminiRepository = new GeminiRepository();
    }

    public LiveData<List<ChatMessage>> getMessages() {
        return messages;
    }

    public LiveData<List<ChatSession>> getSessions() {
        return sessions;
    }

    public LiveData<Boolean> isLoading() {
        return loading;
    }

    public LiveData<String> getUiMessage() {
        return uiMessage;
    }

    public LiveData<String> getActiveSessionId() {
        return activeSessionId;
    }

    public void setUserId(@Nullable String userId) {
        this.userId = userId == null ? "" : userId;
        if (!TextUtils.isEmpty(this.userId)) {
            loadSessions();
        }
    }

    public void startNewChat() {
        activeSessionId.setValue("");
        messages.setValue(new ArrayList<>());
    }

    public void loadSessions() {
        if (TextUtils.isEmpty(userId)) {
            return;
        }

        chatRepository.loadSessions(userId, (result, error) -> {
            if (error != null) {
                uiMessage.postValue("Mình chưa tải được lịch sử chat, thử lại nhé.");
                return;
            }
            sessions.postValue(result);
        });
    }

    public void loadSession(@NonNull String sessionId) {
        if (TextUtils.isEmpty(userId) || TextUtils.isEmpty(sessionId)) {
            return;
        }

        loading.setValue(true);
        chatRepository.loadMessages(userId, sessionId, (result, error) -> {
            loading.postValue(false);
            if (error != null) {
                uiMessage.postValue("Mình chưa tải lại được cuộc chat này, thử lại nhé.");
                return;
            }

            activeSessionId.postValue(sessionId);
            messages.postValue(result);
        });
    }

    public void deleteSession(@NonNull String sessionId) {
        if (TextUtils.isEmpty(userId) || TextUtils.isEmpty(sessionId)) {
            return;
        }

        chatRepository.deleteSession(userId, sessionId, error -> {
            if (error != null) {
                uiMessage.postValue("Mình chưa xóa được đoạn chat này, thử lại nhé.");
                return;
            }

            String currentActive = activeSessionId.getValue();
            if (sessionId.equals(currentActive)) {
                activeSessionId.postValue("");
                messages.postValue(new ArrayList<>());
            }

            loadSessions();
        });
    }

    public void sendMessage(
            @Nullable String userPrompt,
            @Nullable String imageMimeType,
            @Nullable String imageBase64) {
        Boolean isLoadingNow = loading.getValue();
        if (isLoadingNow != null && isLoadingNow) {
            uiMessage.setValue("Mình đang trả lời nè, đợi mình xíu nha.");
            return;
        }

        if (TextUtils.isEmpty(userId)) {
            uiMessage.setValue("Bạn cần đăng nhập để dùng ChatBot nhé.");
            return;
        }

        boolean hasPrompt = !TextUtils.isEmpty(userPrompt) && !TextUtils.isEmpty(userPrompt.trim());
        boolean hasImage = !TextUtils.isEmpty(imageBase64);
        if (!hasPrompt && !hasImage) {
            uiMessage.setValue("Nhập tin nhắn hoặc chọn ảnh trước khi gửi nha.");
            return;
        }

        String normalizedPrompt = hasPrompt
                ? userPrompt.trim()
                : "Nhận diện món ăn trong ảnh này, gọi đúng tên món và hướng dẫn cách nấu chi tiết giúp mình.";

        List<ChatMessage> currentMessages = copyMessages();
        List<ChatMessage> history = new ArrayList<>(currentMessages);

        ChatMessage userMessage = new ChatMessage(ChatMessage.ROLE_USER, normalizedPrompt);
        ChatMessage aiMessage = new ChatMessage(ChatMessage.ROLE_AI, "");

        currentMessages.add(userMessage);
        currentMessages.add(aiMessage);
        messages.setValue(currentMessages);
        loading.setValue(true);

        ensureSession(normalizedPrompt, (sessionId, createError) -> {
            if (createError != null || TextUtils.isEmpty(sessionId)) {
                loading.postValue(false);
                updateAiMessage(aiMessage.getId(), "Ối hehe, mình hơi lag một chút, thử lại nhé!");
                return;
            }

            activeSessionId.postValue(sessionId);
            chatRepository.saveMessage(userId, sessionId, userMessage, error -> {
                if (error != null) {
                    uiMessage.postValue("Mình chưa lưu được tin nhắn người dùng, nhưng vẫn tiếp tục trả lời nè.");
                }
            });

            geminiRepository.streamChatResponse(
                    history,
                    normalizedPrompt,
                    imageMimeType,
                    imageBase64,
                    new GeminiRepository.StreamCallback() {
                        @Override
                        public void onStart() {
                            loading.postValue(true);
                        }

                        @Override
                        public void onChunk(@NonNull String accumulatedText) {
                            updateAiMessage(aiMessage.getId(), accumulatedText);
                        }

                        @Override
                        public void onCompleted(@NonNull String finalText) {
                            updateAiMessage(aiMessage.getId(), finalText);
                            loading.postValue(false);

                            ChatMessage finalAiMessage = aiMessage.copyWithContent(finalText);
                            chatRepository.saveMessage(userId, sessionId, finalAiMessage, saveError -> {
                                if (saveError != null) {
                                    uiMessage.postValue(
                                            "Mình chưa lưu được phản hồi AI, nhưng bạn vẫn có thể chat tiếp nha.");
                                }
                                loadSessions();
                            });
                        }

                        @Override
                        public void onError(@NonNull String friendlyError) {
                            updateAiMessage(aiMessage.getId(), friendlyError);
                            loading.postValue(false);

                            ChatMessage fallback = aiMessage.copyWithContent(friendlyError);
                            chatRepository.saveMessage(userId, sessionId, fallback, saveError -> loadSessions());
                        }
                    });
        });
    }

    private void ensureSession(
            @NonNull String firstPrompt,
            @NonNull ChatRepository.SessionCreateCallback callback) {
        String currentSession = activeSessionId.getValue();
        if (!TextUtils.isEmpty(currentSession)) {
            callback.onComplete(currentSession, null);
            return;
        }

        chatRepository.createSession(userId, firstPrompt, (sessionId, error) -> {
            if (error == null && sessionId != null) {
                activeSessionId.postValue(sessionId);
            }
            callback.onComplete(sessionId, error);
        });
    }

    @NonNull
    private List<ChatMessage> copyMessages() {
        List<ChatMessage> current = messages.getValue();
        return current == null ? new ArrayList<>() : new ArrayList<>(current);
    }

    private void updateAiMessage(@NonNull String messageId, @NonNull String nextText) {
        List<ChatMessage> current = copyMessages();
        for (int i = current.size() - 1; i >= 0; i--) {
            ChatMessage item = current.get(i);
            if (messageId.equals(item.getId())) {
                current.set(i, item.copyWithContent(nextText));
                messages.postValue(current);
                return;
            }
        }
    }
}
