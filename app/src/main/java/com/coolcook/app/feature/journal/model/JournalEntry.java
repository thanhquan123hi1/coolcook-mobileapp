package com.coolcook.app.feature.journal.model;

import android.text.TextUtils;

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
import java.util.UUID;

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
    private final String foodId;
    private final String foodName;
    private final String foodImageUrl;
    private final String localImageName;
    private final String healthReason;
    private final String source;
    @Nullable
    private final Double caloriesEstimate;

    public JournalEntry(
            @NonNull String id,
            @NonNull String userId,
            @NonNull LocalDate date,
            @NonNull String imageUrl,
            @NonNull String thumbnailUrl,
            @Nullable Date capturedAt,
            @NonNull String caption,
            @NonNull String mealType) {
        this(
                id,
                userId,
                date,
                imageUrl,
                thumbnailUrl,
                capturedAt,
                caption,
                mealType,
                "",
                "",
                "",
                "",
                "",
                "",
                null);
    }

    public JournalEntry(
            @NonNull String id,
            @NonNull String userId,
            @NonNull LocalDate date,
            @NonNull String imageUrl,
            @NonNull String thumbnailUrl,
            @Nullable Date capturedAt,
            @NonNull String caption,
            @NonNull String mealType,
            @NonNull String foodId,
            @NonNull String foodName,
            @NonNull String foodImageUrl,
            @NonNull String localImageName,
            @NonNull String healthReason,
            @NonNull String source,
            @Nullable Double caloriesEstimate) {
        this.id = id;
        this.userId = userId;
        this.date = date;
        this.imageUrl = imageUrl;
        this.thumbnailUrl = thumbnailUrl;
        this.capturedAt = capturedAt;
        this.caption = caption;
        this.mealType = mealType;
        this.foodId = foodId;
        this.foodName = foodName;
        this.foodImageUrl = foodImageUrl;
        this.localImageName = localImageName;
        this.healthReason = healthReason;
        this.source = source;
        this.caloriesEstimate = caloriesEstimate;
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
    public String getFoodId() {
        return foodId;
    }

    @NonNull
    public String getFoodName() {
        return foodName;
    }

    @NonNull
    public String getFoodImageUrl() {
        return foodImageUrl;
    }

    @NonNull
    public String getLocalImageName() {
        return localImageName;
    }

    @NonNull
    public String getHealthReason() {
        return healthReason;
    }

    @NonNull
    public String getSource() {
        return source;
    }

    @Nullable
    public Double getCaloriesEstimate() {
        return caloriesEstimate;
    }

    public boolean isFoodEntry() {
        return !foodId.isEmpty() || !foodName.isEmpty();
    }

    public boolean hasPreviewImage() {
        return !getPreviewUrl().isEmpty();
    }

    @NonNull
    public String getPreviewUrl() {
        if (!thumbnailUrl.isEmpty()) {
            return thumbnailUrl;
        }
        if (!imageUrl.isEmpty()) {
            return imageUrl;
        }
        return foodImageUrl;
    }

    @NonNull
    public String getDisplayTitle() {
        if (isFoodEntry() && !foodName.isEmpty()) {
            return foodName;
        }
        if (!caption.isEmpty()) {
            return caption;
        }
        return "Nhat ky mon an";
    }

    @NonNull
    public String getDateKey() {
        return date.format(ISO_DATE);
    }

    @NonNull
    public static JournalEntry createFoodEntry(
            @NonNull String userId,
            @NonNull LocalDate date,
            @NonNull String foodId,
            @NonNull String foodName,
            @NonNull String caption,
            @NonNull String imageUrl,
            @NonNull String mealType,
            @NonNull String recommendationReason,
            @NonNull String source,
            @Nullable Object extra) {

        String id = UUID.randomUUID().toString();
        String finalCaption = TextUtils.isEmpty(caption) ? foodName : caption;
        String normalizedImageUrl = normalizeFoodImage(imageUrl);

        return new JournalEntry(
                id,
                userId,
                date,
                normalizedImageUrl,
                normalizedImageUrl,
                new Date(),
                finalCaption,
                mealType,
                foodId,
                foodName,
                normalizedImageUrl,
                imageUrl.trim(),
                recommendationReason,
                source,
                null);
    }

    @NonNull
    public JournalEntry copyWithMetadata(@NonNull LocalDate nextDate, @NonNull String nextCaption) {
        return new JournalEntry(
                id,
                userId,
                nextDate,
                imageUrl,
                thumbnailUrl,
                capturedAt,
                nextCaption.trim(),
                mealType,
                foodId,
                foodName,
                foodImageUrl,
                localImageName,
                healthReason,
                source,
                caloriesEstimate);
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
        payload.put("foodId", foodId);
        payload.put("foodName", foodName);
        payload.put("foodImageUrl", foodImageUrl);
        payload.put("localImageName", localImageName);
        payload.put("healthReason", healthReason);
        payload.put("source", source);
        if (caloriesEstimate != null) {
            payload.put("caloriesEstimate", caloriesEstimate);
        }

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
        String foodId = value(snapshot.getString("foodId"), "");
        String foodName = value(snapshot.getString("foodName"), "");
        String foodImageUrl = value(snapshot.getString("foodImageUrl"), "");
        String localImageName = value(snapshot.getString("localImageName"), "");
        String healthReason = value(snapshot.getString("healthReason"), "");
        String source = value(snapshot.getString("source"), "");
        Double caloriesEstimate = snapshot.getDouble("caloriesEstimate");

        return new JournalEntry(
                id,
                userId,
                localDate,
                imageUrl,
                thumbnailUrl,
                capturedAt,
                caption,
                mealType,
                foodId,
                foodName,
                foodImageUrl,
                localImageName,
                healthReason,
                source,
                caloriesEstimate);
    }

    @NonNull
    private static LocalDate parseDate(@NonNull String rawDate, @Nullable Date fallbackDate) {
        if (!rawDate.isEmpty()) {
            try {
                return LocalDate.parse(rawDate, ISO_DATE);
            } catch (DateTimeParseException ignored) {
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

    @NonNull
    private static String normalizeFoodImage(@Nullable String rawImage) {
        if (rawImage == null) {
            return "";
        }

        String value = rawImage.trim();
        if (value.isEmpty()) {
            return "";
        }

        if (value.startsWith("http://")
                || value.startsWith("https://")
                || value.startsWith("content://")
                || value.startsWith("file://")
                || value.startsWith("android.resource://")) {
            return value;
        }

        return "android.resource://com.coolcook.app/drawable/" + value;
    }
}
