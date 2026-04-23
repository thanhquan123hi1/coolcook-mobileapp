package com.coolcook.app.ui.scan.model;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.google.firebase.Timestamp;
import com.google.firebase.firestore.DocumentSnapshot;

import java.util.Date;

public class JournalFeedItem {

    private final String momentId;
    private final String ownerUid;
    private final String ownerName;
    private final String ownerAvatarUrl;
    private final String imageUrl;
    private final String thumbUrl;
    private final String cloudinaryPublicId;
    private final String caption;
    private final Date createdAt;
    private final String visibility;
    private final int width;
    private final int height;
    private final boolean ownMoment;

    public JournalFeedItem(
            @NonNull String momentId,
            @NonNull String ownerUid,
            @NonNull String ownerName,
            @NonNull String ownerAvatarUrl,
            @NonNull String imageUrl,
            @NonNull String thumbUrl,
            @NonNull String cloudinaryPublicId,
            @NonNull String caption,
            @Nullable Date createdAt,
            @NonNull String visibility,
            int width,
            int height,
            boolean ownMoment) {
        this.momentId = momentId;
        this.ownerUid = ownerUid;
        this.ownerName = ownerName;
        this.ownerAvatarUrl = ownerAvatarUrl;
        this.imageUrl = imageUrl;
        this.thumbUrl = thumbUrl;
        this.cloudinaryPublicId = cloudinaryPublicId;
        this.caption = caption;
        this.createdAt = createdAt;
        this.visibility = visibility;
        this.width = width;
        this.height = height;
        this.ownMoment = ownMoment;
    }

    @NonNull
    public String getMomentId() {
        return momentId;
    }

    @NonNull
    public String getOwnerUid() {
        return ownerUid;
    }

    @NonNull
    public String getOwnerName() {
        return ownerName;
    }

    @NonNull
    public String getOwnerAvatarUrl() {
        return ownerAvatarUrl;
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
    public String getCloudinaryPublicId() {
        return cloudinaryPublicId;
    }

    @NonNull
    public String getCaption() {
        return caption;
    }

    @Nullable
    public Date getCreatedAt() {
        return createdAt;
    }

    @NonNull
    public String getVisibility() {
        return visibility;
    }

    public int getWidth() {
        return width;
    }

    public int getHeight() {
        return height;
    }

    public boolean isOwnMoment() {
        return ownMoment;
    }

    @NonNull
    public String getBestImageUrl() {
        return thumbUrl.isEmpty() ? imageUrl : thumbUrl;
    }

    @NonNull
    public static JournalFeedItem fromSnapshot(@NonNull DocumentSnapshot snapshot, @NonNull String viewerUid) {
        String ownerUid = value(snapshot.getString("ownerUid"), "");
        Long widthRaw = snapshot.getLong("width");
        Long heightRaw = snapshot.getLong("height");

        return new JournalFeedItem(
                value(snapshot.getString("momentId"), snapshot.getId()),
                ownerUid,
                value(snapshot.getString("ownerName"), "Bạn"),
                value(snapshot.getString("ownerAvatarUrl"), ""),
                value(snapshot.getString("imageUrl"), ""),
                value(snapshot.getString("thumbUrl"), value(snapshot.getString("thumbnailUrl"), "")),
                value(snapshot.getString("cloudinaryPublicId"), ""),
                value(snapshot.getString("caption"), ""),
                toDate(snapshot.get("createdAt")),
                value(snapshot.getString("visibility"), "friends"),
                widthRaw == null ? 0 : widthRaw.intValue(),
                heightRaw == null ? 0 : heightRaw.intValue(),
                ownerUid.equals(viewerUid));
    }

    @NonNull
    private static String value(@Nullable String raw, @NonNull String fallback) {
        if (raw == null) {
            return fallback;
        }
        String trimmed = raw.trim();
        return trimmed.isEmpty() ? fallback : trimmed;
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
