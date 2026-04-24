package com.coolcook.app.core.media;

import androidx.annotation.NonNull;

public class MediaUploadResult {

    private final String imageUrl;
    private final String thumbUrl;
    private final String publicId;
    private final int width;
    private final int height;
    private final String cloudCreatedAt;

    public MediaUploadResult(
            @NonNull String imageUrl,
            @NonNull String thumbUrl,
            @NonNull String publicId,
            int width,
            int height,
            @NonNull String cloudCreatedAt) {
        this.imageUrl = imageUrl;
        this.thumbUrl = thumbUrl;
        this.publicId = publicId;
        this.width = width;
        this.height = height;
        this.cloudCreatedAt = cloudCreatedAt;
    }

    @NonNull
    public String getImageUrl() {
        return imageUrl;
    }

    @NonNull
    public String getThumbUrl() {
        return thumbUrl;
    }

    @NonNull
    public String getPublicId() {
        return publicId;
    }

    public int getWidth() {
        return width;
    }

    public int getHeight() {
        return height;
    }

    @NonNull
    public String getCloudCreatedAt() {
        return cloudCreatedAt;
    }
}

