package com.coolcook.app.feature.journal.model;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.google.firebase.Timestamp;
import com.google.firebase.firestore.DocumentSnapshot;

import java.time.LocalDate;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.util.Date;
import java.util.HashMap;
import java.util.Map;

public class JournalEntry {

    private static final DateTimeFormatter ISO_DATE = DateTimeFormatter.ISO_LOCAL_DATE;

    private final String id;
    private final String userId;
    private final LocalDate date;
    private final String imageUrl;
    private final String thumbnailUrl;
    private final Date capturedAt;
    private final String caption;
    private final String mealType;

    public JournalEntry(
            @NonNull String id,
            @NonNull String userId,
            @NonNull LocalDate date,
            @NonNull String imageUrl,
            @NonNull String thumbnailUrl,
            @Nullable Date capturedAt,
            @NonNull String caption,
            @NonNull String mealType) {
        this.id = id;
        this.userId = userId;
        this.date = date;
        this.imageUrl = imageUrl;
        this.thumbnailUrl = thumbnailUrl;
        this.capturedAt = capturedAt;
        this.caption = caption;
        this.mealType = mealType;
    }

    @NonNull
    public String getId() {
        return id;
    }

    @NonNull
    public String getUserId() {
        return userId;
    }

    @NonNull
    public LocalDate getDate() {
        return date;
    }

    @NonNull
    public String getImageUrl() {
        return imageUrl;
    }

    @NonNull
    public String getThumbnailUrl() {
        return thumbnailUrl;
    }

    @Nullable
    public Date getCapturedAt() {
        return capturedAt;
    }

    @NonNull
    public String getCaption() {
        return caption;
    }

    @NonNull
    public String getMealType() {
        return mealType;
    }

    @NonNull
    public String getPreviewUrl() {
        return thumbnailUrl.isEmpty() ? imageUrl : thumbnailUrl;
    }

    @NonNull
    public String getDateKey() {
        return date.format(ISO_DATE);
    }

    @NonNull
    public Map<String, Object> toFirestorePayload() {
        Date safeCapturedAt = capturedAt == null ? new Date() : capturedAt;

        Map<String, Object> payload = new HashMap<>();
        payload.put("id", id);
        payload.put("userId", userId);
        payload.put("date", getDateKey());
        payload.put("imageUrl", imageUrl);
        payload.put("thumbnailUrl", thumbnailUrl);
        payload.put("capturedAt", safeCapturedAt);
        payload.put("caption", caption);
        payload.put("mealType", mealType);

        // Compatibility fields for old readers in Scan mode.
        payload.put("thumbUrl", thumbnailUrl);
        payload.put("createdAt", safeCapturedAt);
        payload.put("updatedAt", new Date());

        return payload;
    }

    @NonNull
    public static JournalEntry fromSnapshot(@NonNull DocumentSnapshot snapshot) {
        Date capturedAt = toDate(snapshot.get("capturedAt"));
        if (capturedAt == null) {
            capturedAt = toDate(snapshot.get("createdAt"));
        }

        String rawDate = value(snapshot.getString("date"), "");
        LocalDate localDate = parseDate(rawDate, capturedAt);

        String imageUrl = value(snapshot.getString("imageUrl"), "");
        String thumbnailUrl = value(snapshot.getString("thumbnailUrl"), value(snapshot.getString("thumbUrl"), ""));

        String id = value(snapshot.getString("id"), snapshot.getId());
        String userId = value(snapshot.getString("userId"), "");
        String caption = value(snapshot.getString("caption"), "");
        String mealType = value(snapshot.getString("mealType"), "other");

        return new JournalEntry(id, userId, localDate, imageUrl, thumbnailUrl, capturedAt, caption, mealType);
    }

    @NonNull
    private static LocalDate parseDate(@NonNull String rawDate, @Nullable Date fallbackDate) {
        if (!rawDate.isEmpty()) {
            try {
                return LocalDate.parse(rawDate, ISO_DATE);
            } catch (DateTimeParseException ignored) {
                // Use fallback captured date when the stored date format is malformed.
            }
        }
        if (fallbackDate != null) {
            return fallbackDate.toInstant().atZone(ZoneId.systemDefault()).toLocalDate();
        }
        return LocalDate.now();
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
    private static String value(@Nullable String raw, @NonNull String fallback) {
        if (raw == null) {
            return fallback;
        }
        String trimmed = raw.trim();
        return trimmed.isEmpty() ? fallback : trimmed;
    }
}
