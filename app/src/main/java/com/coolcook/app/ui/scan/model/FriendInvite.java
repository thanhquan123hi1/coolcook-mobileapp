package com.coolcook.app.ui.scan.model;

import android.net.Uri;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.google.firebase.Timestamp;
import com.google.firebase.firestore.DocumentSnapshot;

import java.util.Date;

public class FriendInvite {

    public static final String STATUS_ACTIVE = "active";
    public static final String STATUS_USED = "used";
    public static final String STATUS_EXPIRED = "expired";

    private final String inviteId;
    private final String createdByUid;
    private final String createdByName;
    private final String createdByAvatarUrl;
    private final String status;
    private final Date createdAt;
    private final Date expiresAt;

    public FriendInvite(
            @NonNull String inviteId,
            @NonNull String createdByUid,
            @NonNull String createdByName,
            @NonNull String createdByAvatarUrl,
            @NonNull String status,
            @Nullable Date createdAt,
            @Nullable Date expiresAt) {
        this.inviteId = inviteId;
        this.createdByUid = createdByUid;
        this.createdByName = createdByName;
        this.createdByAvatarUrl = createdByAvatarUrl;
        this.status = status;
        this.createdAt = createdAt;
        this.expiresAt = expiresAt;
    }

    @NonNull
    public String getInviteId() {
        return inviteId;
    }

    @NonNull
    public String getCreatedByUid() {
        return createdByUid;
    }

    @NonNull
    public String getCreatedByName() {
        return createdByName;
    }

    @NonNull
    public String getCreatedByAvatarUrl() {
        return createdByAvatarUrl;
    }

    @NonNull
    public String getStatus() {
        return status;
    }

    @Nullable
    public Date getCreatedAt() {
        return createdAt;
    }

    @Nullable
    public Date getExpiresAt() {
        return expiresAt;
    }

    public boolean isActive() {
        return STATUS_ACTIVE.equals(status) && !isExpired();
    }

    public boolean isExpired() {
        return expiresAt != null && expiresAt.before(new Date());
    }

    @NonNull
    public String buildDeepLink() {
        return new Uri.Builder()
                .scheme("coolcook")
                .authority("friend-invite")
                .appendQueryParameter("inviteId", inviteId)
                .build()
                .toString();
    }

    @NonNull
    public String buildWebLink() {
        return "https://coolcook.app/invite/" + inviteId;
    }

    @NonNull
    public static FriendInvite fromSnapshot(@NonNull DocumentSnapshot snapshot) {
        return new FriendInvite(
                value(snapshot.getString("inviteId"), snapshot.getId()),
                value(snapshot.getString("createdByUid"), ""),
                value(snapshot.getString("createdByName"), "Bạn mới"),
                value(snapshot.getString("createdByAvatarUrl"), ""),
                value(snapshot.getString("status"), STATUS_EXPIRED),
                toDate(snapshot.get("createdAt")),
                toDate(snapshot.get("expiresAt")));
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
