package com.coolcook.app.feature.chatbot.model;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import java.util.Date;
import java.util.UUID;

public class ChatMessage {

    public static final String ROLE_USER = "user";
    public static final String ROLE_AI = "ai";

    private String id;
    private String role;
    private String content;
    private Date createdAt;

    public ChatMessage() {
        // Needed for Firestore serialization.
    }

    public ChatMessage(@NonNull String role, @NonNull String content) {
        this(UUID.randomUUID().toString(), role, content, new Date());
    }

    public ChatMessage(@NonNull String id, @NonNull String role, @NonNull String content, @Nullable Date createdAt) {
        this.id = id;
        this.role = role;
        this.content = content;
        this.createdAt = createdAt != null ? createdAt : new Date();
    }

    @NonNull
    public String getId() {
        return id == null ? "" : id;
    }

    public void setId(@Nullable String id) {
        this.id = id;
    }

    @NonNull
    public String getRole() {
        return role == null ? ROLE_AI : role;
    }

    public void setRole(@Nullable String role) {
        this.role = role;
    }

    @NonNull
    public String getContent() {
        return content == null ? "" : content;
    }

    public void setContent(@Nullable String content) {
        this.content = content;
    }

    @NonNull
    public Date getCreatedAt() {
        return createdAt == null ? new Date() : createdAt;
    }

    public void setCreatedAt(@Nullable Date createdAt) {
        this.createdAt = createdAt;
    }

    public boolean isUser() {
        return ROLE_USER.equals(getRole());
    }

    @NonNull
    public ChatMessage copyWithContent(@NonNull String nextContent) {
        return new ChatMessage(getId(), getRole(), nextContent, getCreatedAt());
    }
}
