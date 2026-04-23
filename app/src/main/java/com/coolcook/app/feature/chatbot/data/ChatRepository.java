package com.coolcook.app.feature.chatbot.data;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.coolcook.app.feature.chatbot.model.ChatMessage;
import com.coolcook.app.feature.chatbot.model.ChatSession;
import com.google.firebase.Timestamp;
import com.google.firebase.firestore.CollectionReference;
import com.google.firebase.firestore.DocumentReference;
import com.google.firebase.firestore.FirebaseFirestore;
import com.google.firebase.firestore.Query;
import com.google.firebase.firestore.WriteBatch;

import java.util.ArrayList;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

public class ChatRepository {

    public interface SessionCreateCallback {
        void onComplete(@Nullable String sessionId, @Nullable Exception error);
    }

    public interface CompletionCallback {
        void onComplete(@Nullable Exception error);
    }

    public interface SessionsCallback {
        void onComplete(@NonNull List<ChatSession> sessions, @Nullable Exception error);
    }

    public interface MessagesCallback {
        void onComplete(@NonNull List<ChatMessage> messages, @Nullable Exception error);
    }

    private static final String USERS_COLLECTION = "users";
    private static final String CHATS_COLLECTION = "chats";
    private static final String MESSAGES_COLLECTION = "messages";

    private final FirebaseFirestore firestore;

    public ChatRepository(@NonNull FirebaseFirestore firestore) {
        this.firestore = firestore;
    }

    public void createSession(
            @NonNull String userId,
            @NonNull String firstPrompt,
            @NonNull SessionCreateCallback callback) {
        DocumentReference sessionRef = chatsCollection(userId).document();
        String title = buildCuteSessionTitle(firstPrompt);

        Map<String, Object> payload = new HashMap<>();
        payload.put("title", title);
        payload.put("firstPrompt", firstPrompt);
        payload.put("lastPreview", firstPrompt);
        payload.put("createdAt", new Date());
        payload.put("updatedAt", new Date());

        sessionRef.set(payload)
                .addOnSuccessListener(unused -> callback.onComplete(sessionRef.getId(), null))
                .addOnFailureListener(error -> callback.onComplete(null, error));
    }

    public void saveMessage(
            @NonNull String userId,
            @NonNull String sessionId,
            @NonNull ChatMessage message,
            @NonNull CompletionCallback callback) {

        String messageId = message.getId().isEmpty() ? String.valueOf(System.currentTimeMillis()) : message.getId();
        message.setId(messageId);

        Map<String, Object> messageData = new HashMap<>();
        messageData.put("id", messageId);
        messageData.put("role", message.getRole());
        messageData.put("content", message.getContent());
        messageData.put("createdAt", message.getCreatedAt());

        messagesCollection(userId, sessionId)
                .document(messageId)
                .set(messageData)
                .addOnSuccessListener(unused -> updateSessionPreview(userId, sessionId, message.getContent(), callback))
                .addOnFailureListener(callback::onComplete);
    }

    public void loadMessages(
            @NonNull String userId,
            @NonNull String sessionId,
            @NonNull MessagesCallback callback) {
        messagesCollection(userId, sessionId)
                .orderBy("createdAt", Query.Direction.ASCENDING)
                .get()
                .addOnSuccessListener(snapshot -> {
                    List<ChatMessage> messages = new ArrayList<>();
                    snapshot.getDocuments().forEach(document -> {
                        String id = stringValue(document.getString("id"), document.getId());
                        String role = stringValue(document.getString("role"), ChatMessage.ROLE_AI);
                        String content = stringValue(document.getString("content"), "");
                        Date createdAt = toDate(document.get("createdAt"));
                        messages.add(new ChatMessage(id, role, content, createdAt));
                    });
                    callback.onComplete(messages, null);
                })
                .addOnFailureListener(error -> callback.onComplete(new ArrayList<>(), error));
    }

    public void loadSessions(@NonNull String userId, @NonNull SessionsCallback callback) {
        chatsCollection(userId)
                .orderBy("updatedAt", Query.Direction.DESCENDING)
                .get()
                .addOnSuccessListener(snapshot -> {
                    List<ChatSession> sessions = new ArrayList<>();
                    snapshot.getDocuments().forEach(document -> {
                        String sessionId = document.getId();
                        String title = stringValue(document.getString("title"), "New Chat");
                        String preview = stringValue(document.getString("lastPreview"), "");
                        Date createdAt = toDate(document.get("createdAt"));
                        Date updatedAt = toDate(document.get("updatedAt"));
                        sessions.add(new ChatSession(sessionId, title, preview, createdAt, updatedAt));
                    });
                    callback.onComplete(sessions, null);
                })
                .addOnFailureListener(error -> callback.onComplete(new ArrayList<>(), error));
    }

    public void deleteSession(
            @NonNull String userId,
            @NonNull String sessionId,
            @NonNull CompletionCallback callback) {
        DocumentReference sessionRef = chatsCollection(userId).document(sessionId);

        messagesCollection(userId, sessionId)
                .get()
                .addOnSuccessListener(snapshot -> {
                    WriteBatch batch = firestore.batch();
                    snapshot.getDocuments().forEach(doc -> batch.delete(doc.getReference()));
                    batch.delete(sessionRef);
                    batch.commit()
                            .addOnSuccessListener(unused -> callback.onComplete(null))
                            .addOnFailureListener(callback::onComplete);
                })
                .addOnFailureListener(callback::onComplete);
    }

    @NonNull
    public static String buildCuteSessionTitle(@NonNull String firstPrompt) {
        String normalized = firstPrompt.trim().replaceAll("\\s+", " ");
        if (normalized.isEmpty()) {
            return "New Chat hehe";
        }

        String shortened = normalized.length() > 26
                ? normalized.substring(0, 26).trim() + "..."
                : normalized;

        return String.format(Locale.getDefault(), "Hehe %s", shortened);
    }

    private void updateSessionPreview(
            @NonNull String userId,
            @NonNull String sessionId,
            @NonNull String message,
            @NonNull CompletionCallback callback) {
        Map<String, Object> sessionData = new HashMap<>();
        sessionData.put("lastPreview", message);
        sessionData.put("updatedAt", new Date());

        chatsCollection(userId)
                .document(sessionId)
                .update(sessionData)
                .addOnSuccessListener(unused -> callback.onComplete(null))
                .addOnFailureListener(callback::onComplete);
    }

    private CollectionReference chatsCollection(@NonNull String userId) {
        return firestore.collection(USERS_COLLECTION)
                .document(userId)
                .collection(CHATS_COLLECTION);
    }

    private CollectionReference messagesCollection(
            @NonNull String userId,
            @NonNull String sessionId) {
        return chatsCollection(userId)
                .document(sessionId)
                .collection(MESSAGES_COLLECTION);
    }

    @Nullable
    private static Date toDate(@Nullable Object rawDate) {
        if (rawDate instanceof Date) {
            return (Date) rawDate;
        }
        if (rawDate instanceof Timestamp) {
            return ((Timestamp) rawDate).toDate();
        }
        return null;
    }

    @NonNull
    private static String stringValue(@Nullable String value, @NonNull String fallback) {
        return value == null || value.trim().isEmpty() ? fallback : value;
    }
}
