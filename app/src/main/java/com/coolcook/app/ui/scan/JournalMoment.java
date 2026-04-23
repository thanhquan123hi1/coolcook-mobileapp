package com.coolcook.app.ui.scan;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.google.firebase.Timestamp;
import com.google.firebase.firestore.DocumentSnapshot;

import java.util.Date;

public class JournalMoment {

    private final String id;
    private final String imageUrl;
    private final String thumbUrl;
    private final String source;
    private final String cameraFacing;
    private final int width;
    private final int height;
    private final Date createdAt;

    public JournalMoment(
            @NonNull String id,
            @NonNull String imageUrl,
            @NonNull String thumbUrl,
            @NonNull String source,
            @NonNull String cameraFacing,
            int width,
            int height,
            @Nullable Date createdAt) {
        this.id = id;
        this.imageUrl = imageUrl;
        this.thumbUrl = thumbUrl;
        this.source = source;
        this.cameraFacing = cameraFacing;
        this.width = width;
        this.height = height;
        this.createdAt = createdAt;
    }

    @NonNull
    public String getId() {
        return id;
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
    public String getSource() {
        return source;
    }

    @NonNull
    public String getCameraFacing() {
        return cameraFacing;
    }

    public int getWidth() {
        return width;
    }

    public int getHeight() {
        return height;
    }

    @Nullable
    public Date getCreatedAt() {
        return createdAt;
    }

    @NonNull
    public static JournalMoment fromSnapshot(@NonNull DocumentSnapshot snapshot) {
        String id = snapshot.getId();
        String imageUrl = safeString(snapshot.getString("imageUrl"));
        String thumbUrl = safeString(snapshot.getString("thumbnailUrl"));
        if (thumbUrl.isEmpty()) {
            thumbUrl = safeString(snapshot.getString("thumbUrl"));
        }
        String source = safeString(snapshot.getString("source"));
        String cameraFacing = safeString(snapshot.getString("cameraFacing"));
        Long widthRaw = snapshot.getLong("width");
        Long heightRaw = snapshot.getLong("height");
        int width = widthRaw == null ? 0 : widthRaw.intValue();
        int height = heightRaw == null ? 0 : heightRaw.intValue();
        Date createdAt = toDate(snapshot.get("capturedAt"));
        if (createdAt == null) {
            createdAt = toDate(snapshot.get("createdAt"));
        }

        return new JournalMoment(
                id,
                imageUrl,
                thumbUrl,
                source,
                cameraFacing,
                width,
                height,
                createdAt);
    }

    @NonNull
    private static String safeString(@Nullable String value) {
        return value == null ? "" : value;
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
}
