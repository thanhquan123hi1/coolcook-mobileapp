package com.coolcook.app.feature.chatbot.model;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import java.util.Date;

public class ChatSession {

    private String id;
    private String title;
    private String lastPreview;
    private Date createdAt;
    private Date updatedAt;

    public ChatSession() {
        // Needed for Firestore serialization.
    }

    public ChatSession(@NonNull String id, @NonNull String title, @Nullable String lastPreview,
            @Nullable Date createdAt, @Nullable Date updatedAt) {
        this.id = id;
        this.title = title;
        this.lastPreview = lastPreview;
        this.createdAt = createdAt;
        this.updatedAt = updatedAt;
    }

    @NonNull
    public String getId() {
        return id == null ? "" : id;
    }

    public void setId(@Nullable String id) {
        this.id = id;
    }

    @NonNull
    public String getTitle() {
        return title == null || title.trim().isEmpty() ? "New Chat" : title;
    }

    public void setTitle(@Nullable String title) {
        this.title = title;
    }

    @NonNull
    public String getLastPreview() {
        return lastPreview == null ? "" : lastPreview;
    }

    public void setLastPreview(@Nullable String lastPreview) {
        this.lastPreview = lastPreview;
    }

    @Nullable
    public Date getCreatedAt() {
        return createdAt;
    }

    public void setCreatedAt(@Nullable Date createdAt) {
        this.createdAt = createdAt;
    }

    @Nullable
    public Date getUpdatedAt() {
        return updatedAt;
    }

    public void setUpdatedAt(@Nullable Date updatedAt) {
        this.updatedAt = updatedAt;
    }
}
