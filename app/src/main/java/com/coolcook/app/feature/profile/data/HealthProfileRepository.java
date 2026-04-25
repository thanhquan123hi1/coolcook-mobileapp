package com.coolcook.app.feature.profile.data;

import android.text.TextUtils;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.coolcook.app.feature.profile.model.HealthProfile;
import com.google.firebase.firestore.DocumentReference;
import com.google.firebase.firestore.FirebaseFirestore;

import java.util.Date;

public class HealthProfileRepository {

    public interface LoadCallback {
        void onComplete(@Nullable HealthProfile profile, @Nullable String friendlyError);
    }

    public interface SaveCallback {
        void onComplete(boolean success, @Nullable String friendlyError);
    }

    private static final String USERS_COLLECTION = "users";

    @NonNull
    private final FirebaseFirestore firestore;

    public HealthProfileRepository(@NonNull FirebaseFirestore firestore) {
        this.firestore = firestore;
    }

    public void loadHealthProfile(@NonNull String userId, @NonNull LoadCallback callback) {
        if (TextUtils.isEmpty(userId)) {
            callback.onComplete(null, "Bạn cần đăng nhập để xem hồ sơ sức khỏe.");
            return;
        }

        profileDocument(userId)
                .get()
                .addOnSuccessListener(snapshot -> {
                    if (!snapshot.exists()) {
                        callback.onComplete(null, null);
                        return;
                    }
                    callback.onComplete(HealthProfile.fromSnapshot(snapshot), null);
                })
                .addOnFailureListener(error ->
                        callback.onComplete(null, "Không thể tải hồ sơ sức khỏe lúc này."));
    }

    public void saveHealthProfile(
            @NonNull String userId,
            @NonNull HealthProfile profile,
            @NonNull SaveCallback callback) {
        if (TextUtils.isEmpty(userId)) {
            callback.onComplete(false, "Bạn cần đăng nhập để lưu hồ sơ sức khỏe.");
            return;
        }

        String validationError = validate(profile);
        if (!TextUtils.isEmpty(validationError)) {
            callback.onComplete(false, validationError);
            return;
        }

        HealthProfile payload = new HealthProfile(
                profile.getWeightKg(),
                profile.getSystolicBp(),
                profile.getDiastolicBp(),
                profile.getHeartRateBpm(),
                profile.getGoal(),
                profile.getHealthTags(),
                new Date());

        profileDocument(userId)
                .set(payload.toMap())
                .addOnSuccessListener(unused -> callback.onComplete(true, null))
                .addOnFailureListener(error ->
                        callback.onComplete(false, "Không thể lưu hồ sơ sức khỏe. Vui lòng thử lại."));
    }

    @Nullable
    public static String validate(@NonNull HealthProfile profile) {
        if (profile.getWeightKg() < 25d || profile.getWeightKg() > 250d) {
            return "Cân nặng nên nằm trong khoảng 25 đến 250 kg.";
        }
        if (profile.getSystolicBp() < 70 || profile.getSystolicBp() > 220) {
            return "Huyết áp tâm thu nên nằm trong khoảng 70 đến 220.";
        }
        if (profile.getDiastolicBp() < 40 || profile.getDiastolicBp() > 140) {
            return "Huyết áp tâm trương nên nằm trong khoảng 40 đến 140.";
        }
        if (profile.getHeartRateBpm() < 35 || profile.getHeartRateBpm() > 220) {
            return "Nhịp tim nên nằm trong khoảng 35 đến 220 bpm.";
        }
        return null;
    }

    @NonNull
    private DocumentReference profileDocument(@NonNull String userId) {
        return firestore.collection(USERS_COLLECTION)
                .document(userId)
                .collection("health")
                .document("profile");
    }
}
