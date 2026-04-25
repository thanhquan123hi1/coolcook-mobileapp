package com.coolcook.app.feature.profile.model;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.google.firebase.Timestamp;
import com.google.firebase.firestore.DocumentSnapshot;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class HealthProfile {

    private final double weightKg;
    private final int systolicBp;
    private final int diastolicBp;
    private final int heartRateBpm;
    @NonNull
    private final String goal;
    @NonNull
    private final List<String> healthTags;
    @Nullable
    private final Date updatedAt;

    public HealthProfile(
            double weightKg,
            int systolicBp,
            int diastolicBp,
            int heartRateBpm,
            @Nullable String goal,
            @NonNull List<String> healthTags,
            @Nullable Date updatedAt) {
        this.weightKg = weightKg;
        this.systolicBp = systolicBp;
        this.diastolicBp = diastolicBp;
        this.heartRateBpm = heartRateBpm;
        this.goal = goal == null ? "" : goal.trim();
        this.healthTags = new ArrayList<>(healthTags);
        this.updatedAt = updatedAt;
    }

    public double getWeightKg() {
        return weightKg;
    }

    public int getSystolicBp() {
        return systolicBp;
    }

    public int getDiastolicBp() {
        return diastolicBp;
    }

    public int getHeartRateBpm() {
        return heartRateBpm;
    }

    @NonNull
    public String getGoal() {
        return goal;
    }

    @NonNull
    public List<String> getHealthTags() {
        return Collections.unmodifiableList(healthTags);
    }

    @Nullable
    public Date getUpdatedAt() {
        return updatedAt;
    }

    @NonNull
    public Map<String, Object> toMap() {
        Map<String, Object> payload = new HashMap<>();
        payload.put("weightKg", weightKg);
        payload.put("systolicBp", systolicBp);
        payload.put("diastolicBp", diastolicBp);
        payload.put("heartRateBpm", heartRateBpm);
        payload.put("goal", goal);
        payload.put("healthTags", new ArrayList<>(healthTags));
        payload.put("updatedAt", updatedAt == null ? new Date() : updatedAt);
        return payload;
    }

    @NonNull
    public static HealthProfile fromSnapshot(@NonNull DocumentSnapshot snapshot) {
        Object rawTags = snapshot.get("healthTags");
        List<String> tags = new ArrayList<>();
        if (rawTags instanceof List<?>) {
            for (Object item : (List<?>) rawTags) {
                if (item == null) {
                    continue;
                }
                String value = String.valueOf(item).trim();
                if (!value.isEmpty() && !tags.contains(value)) {
                    tags.add(value);
                }
            }
        }

        return new HealthProfile(
                snapshot.getDouble("weightKg") == null ? 0d : snapshot.getDouble("weightKg"),
                valueOf(snapshot.getLong("systolicBp")),
                valueOf(snapshot.getLong("diastolicBp")),
                valueOf(snapshot.getLong("heartRateBpm")),
                snapshot.getString("goal"),
                tags,
                toDate(snapshot.get("updatedAt")));
    }

    private static int valueOf(@Nullable Long value) {
        return value == null ? 0 : value.intValue();
    }

    @Nullable
    private static Date toDate(@Nullable Object raw) {
        if (raw instanceof Date) {
            return (Date) raw;
        }
        if (raw instanceof Timestamp) {
            return ((Timestamp) raw).toDate();
        }
        return null;
    }
}
