package com.coolcook.app.ui.scan.data;

import android.net.Uri;
import android.text.TextUtils;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.coolcook.app.ui.scan.model.JournalFeedItem;
import com.coolcook.app.ui.scan.model.MediaUploadResult;
import com.google.firebase.auth.FirebaseUser;
import com.google.firebase.firestore.DocumentReference;
import com.google.firebase.firestore.DocumentSnapshot;
import com.google.firebase.firestore.FieldValue;
import com.google.firebase.firestore.FirebaseFirestore;
import com.google.firebase.firestore.ListenerRegistration;
import com.google.firebase.firestore.Query;
import com.google.firebase.firestore.QueryDocumentSnapshot;
import com.google.firebase.firestore.SetOptions;
import com.google.firebase.firestore.WriteBatch;

import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class JournalFeedRepository {

    public interface FeedCallback {
        void onItems(@NonNull List<JournalFeedItem> items);

        void onError(@NonNull Exception error);
    }

    public interface PublishCallback {
        void onSuccess(@NonNull JournalFeedItem item);

        void onError(@NonNull Exception error);
    }

    public interface UserProfileCallback {
        void onProfile(@NonNull UserProfile profile);

        void onError(@NonNull Exception error);
    }

    private static final String USERS_COLLECTION = "users";
    private static final String FRIENDS_COLLECTION = "friends";
    private static final String FEED_COLLECTION = "feed";
    private static final String MOMENTS_COLLECTION = "moments";
    private static final String JOURNAL_COLLECTION = "journal";
    private static final int FAN_OUT_BATCH_FRIEND_LIMIT = 450;
    private static final DateTimeFormatter ISO_DATE = DateTimeFormatter.ISO_LOCAL_DATE;

    private final FirebaseFirestore firestore;

    public JournalFeedRepository(@NonNull FirebaseFirestore firestore) {
        this.firestore = firestore;
    }

    @NonNull
    public ListenerRegistration listenToFeed(
            @NonNull String userId,
            int limit,
            @NonNull FeedCallback callback) {
        return firestore.collection(USERS_COLLECTION)
                .document(userId)
                .collection(FEED_COLLECTION)
                .orderBy("createdAt", Query.Direction.DESCENDING)
                .limit(limit)
                .addSnapshotListener((snapshot, error) -> {
                    if (error != null) {
                        callback.onError(error);
                        return;
                    }
                    List<JournalFeedItem> items = new ArrayList<>();
                    if (snapshot != null) {
                        for (DocumentSnapshot doc : snapshot.getDocuments()) {
                            JournalFeedItem item = JournalFeedItem.fromSnapshot(doc, userId);
                            if (!item.getImageUrl().isEmpty()) {
                                items.add(item);
                            }
                        }
                    }
                    callback.onItems(items);
                });
    }

    @NonNull
    public ListenerRegistration listenToUserProfile(
            @NonNull FirebaseUser user,
            @NonNull UserProfileCallback callback) {
        return firestore.collection(USERS_COLLECTION)
                .document(user.getUid())
                .addSnapshotListener((snapshot, error) -> {
                    if (error != null) {
                        callback.onError(error);
                        return;
                    }
                    callback.onProfile(UserProfile.fromSnapshot(user, snapshot));
                });
    }

    public void publishMoment(
            @NonNull FirebaseUser user,
            @NonNull MediaUploadResult uploadResult,
            @NonNull String caption,
            @NonNull String source,
            @NonNull String cameraFacing,
            @NonNull PublishCallback callback) {
        if (TextUtils.isEmpty(uploadResult.getImageUrl())) {
            callback.onError(new IllegalArgumentException("imageUrl is required"));
            return;
        }

        loadUserProfile(user, new UserProfileCallback() {
            @Override
            public void onProfile(@NonNull UserProfile profile) {
                loadAcceptedFriends(user.getUid(), new FriendsCallback() {
                    @Override
                    public void onFriends(@NonNull List<String> friendIds) {
                        commitMoment(
                                user.getUid(),
                                profile,
                                friendIds,
                                uploadResult,
                                caption,
                                source,
                                cameraFacing,
                                callback);
                    }

                    @Override
                    public void onError(@NonNull Exception error) {
                        callback.onError(error);
                    }
                });
            }

            @Override
            public void onError(@NonNull Exception error) {
                callback.onError(error);
            }
        });
    }

    private void loadUserProfile(@NonNull FirebaseUser user, @NonNull UserProfileCallback callback) {
        firestore.collection(USERS_COLLECTION)
                .document(user.getUid())
                .get()
                .addOnSuccessListener(snapshot -> callback.onProfile(UserProfile.fromSnapshot(user, snapshot)))
                .addOnFailureListener(callback::onError);
    }

    private void loadAcceptedFriends(@NonNull String userId, @NonNull FriendsCallback callback) {
        firestore.collection(USERS_COLLECTION)
                .document(userId)
                .collection(FRIENDS_COLLECTION)
                .whereEqualTo("status", "accepted")
                .get()
                .addOnSuccessListener(snapshot -> {
                    List<String> friendIds = new ArrayList<>();
                    for (QueryDocumentSnapshot doc : snapshot) {
                        String friendUid = doc.getId();
                        String uidField = doc.getString("uid");
                        if (!TextUtils.isEmpty(uidField)) {
                            friendUid = uidField;
                        }
                        if (!TextUtils.isEmpty(friendUid)) {
                            friendIds.add(friendUid);
                        }
                    }
                    callback.onFriends(friendIds);
                })
                .addOnFailureListener(callback::onError);
    }

    private void commitMoment(
            @NonNull String userId,
            @NonNull UserProfile profile,
            @NonNull List<String> friendIds,
            @NonNull MediaUploadResult uploadResult,
            @NonNull String caption,
            @NonNull String source,
            @NonNull String cameraFacing,
            @NonNull PublishCallback callback) {
        DocumentReference momentRef = firestore.collection(MOMENTS_COLLECTION).document();
        String momentId = momentRef.getId();
        Date createdAt = new Date();
        String safeCaption = normalizeCaption(caption);
        String dateKey = createdAt.toInstant()
                .atZone(ZoneId.systemDefault())
                .toLocalDate()
                .format(ISO_DATE);

        Map<String, Object> momentPayload = buildMomentPayload(
                momentId,
                userId,
                profile,
                uploadResult,
                safeCaption,
                createdAt);

        Map<String, Object> feedPayload = buildFeedPayload(momentPayload);
        Map<String, Object> legacyPayload = buildLegacyJournalPayload(
                momentPayload,
                dateKey,
                source,
                cameraFacing);

        WriteBatch batch = firestore.batch();
        batch.set(firestore.collection(USERS_COLLECTION).document(userId), profile.toFirestoreUpdate(), SetOptions.merge());
        batch.set(momentRef, momentPayload);
        batch.set(userFeedRef(userId, momentId), feedPayload);
        batch.set(legacyJournalRef(userId, momentId), legacyPayload);

        int fanOutCount = 0;
        for (String friendId : friendIds) {
            if (fanOutCount >= FAN_OUT_BATCH_FRIEND_LIMIT) {
                break;
            }
            if (!TextUtils.isEmpty(friendId) && !friendId.equals(userId)) {
                batch.set(userFeedRef(friendId, momentId), feedPayload);
                fanOutCount++;
            }
        }

        JournalFeedItem item = new JournalFeedItem(
                momentId,
                userId,
                profile.displayName,
                profile.avatarUrl,
                uploadResult.getImageUrl(),
                uploadResult.getThumbUrl(),
                uploadResult.getPublicId(),
                safeCaption,
                createdAt,
                "friends",
                uploadResult.getWidth(),
                uploadResult.getHeight(),
                true);

        batch.commit()
                .addOnSuccessListener(unused -> callback.onSuccess(item))
                .addOnFailureListener(callback::onError);
    }

    @NonNull
    private DocumentReference userFeedRef(@NonNull String userId, @NonNull String momentId) {
        return firestore.collection(USERS_COLLECTION)
                .document(userId)
                .collection(FEED_COLLECTION)
                .document(momentId);
    }

    @NonNull
    private DocumentReference legacyJournalRef(@NonNull String userId, @NonNull String momentId) {
        return firestore.collection(USERS_COLLECTION)
                .document(userId)
                .collection(JOURNAL_COLLECTION)
                .document(momentId);
    }

    @NonNull
    private Map<String, Object> buildMomentPayload(
            @NonNull String momentId,
            @NonNull String userId,
            @NonNull UserProfile profile,
            @NonNull MediaUploadResult uploadResult,
            @NonNull String caption,
            @NonNull Date createdAt) {
        Map<String, Object> payload = new HashMap<>();
        payload.put("momentId", momentId);
        payload.put("ownerUid", userId);
        payload.put("ownerName", profile.displayName);
        payload.put("ownerAvatarUrl", profile.avatarUrl);
        payload.put("imageUrl", uploadResult.getImageUrl());
        payload.put("thumbUrl", uploadResult.getThumbUrl());
        payload.put("thumbnailUrl", uploadResult.getThumbUrl());
        payload.put("cloudinaryPublicId", uploadResult.getPublicId());
        payload.put("caption", caption);
        payload.put("createdAt", createdAt);
        payload.put("cloudinaryCreatedAt", uploadResult.getCloudCreatedAt());
        payload.put("serverCreatedAt", FieldValue.serverTimestamp());
        payload.put("visibility", "friends");
        payload.put("width", uploadResult.getWidth());
        payload.put("height", uploadResult.getHeight());
        return payload;
    }

    @NonNull
    private Map<String, Object> buildFeedPayload(@NonNull Map<String, Object> momentPayload) {
        Map<String, Object> payload = new HashMap<>();
        payload.put("momentId", momentPayload.get("momentId"));
        payload.put("ownerUid", momentPayload.get("ownerUid"));
        payload.put("ownerName", momentPayload.get("ownerName"));
        payload.put("ownerAvatarUrl", momentPayload.get("ownerAvatarUrl"));
        payload.put("imageUrl", momentPayload.get("imageUrl"));
        payload.put("thumbUrl", momentPayload.get("thumbUrl"));
        payload.put("thumbnailUrl", momentPayload.get("thumbnailUrl"));
        payload.put("cloudinaryPublicId", momentPayload.get("cloudinaryPublicId"));
        payload.put("caption", momentPayload.get("caption"));
        payload.put("createdAt", momentPayload.get("createdAt"));
        payload.put("serverCreatedAt", FieldValue.serverTimestamp());
        payload.put("visibility", momentPayload.get("visibility"));
        payload.put("width", momentPayload.get("width"));
        payload.put("height", momentPayload.get("height"));
        return payload;
    }

    @NonNull
    private Map<String, Object> buildLegacyJournalPayload(
            @NonNull Map<String, Object> momentPayload,
            @NonNull String dateKey,
            @NonNull String source,
            @NonNull String cameraFacing) {
        Map<String, Object> payload = new HashMap<>(momentPayload);
        payload.put("id", momentPayload.get("momentId"));
        payload.put("userId", momentPayload.get("ownerUid"));
        payload.put("date", dateKey);
        payload.put("capturedAt", momentPayload.get("createdAt"));
        payload.put("mealType", "other");
        payload.put("source", source);
        payload.put("cameraFacing", cameraFacing);
        payload.put("updatedAt", new Date());
        return payload;
    }

    @NonNull
    private String normalizeCaption(@NonNull String caption) {
        String trimmed = caption.trim();
        if (trimmed.length() <= 180) {
            return trimmed;
        }
        return trimmed.substring(0, 180).trim();
    }

    private interface FriendsCallback {
        void onFriends(@NonNull List<String> friendIds);

        void onError(@NonNull Exception error);
    }

    public static class UserProfile {
        public final String uid;
        public final String displayName;
        public final String avatarUrl;
        public final long friendCount;

        public UserProfile(
                @NonNull String uid,
                @NonNull String displayName,
                @NonNull String avatarUrl,
                long friendCount) {
            this.uid = uid;
            this.displayName = displayName;
            this.avatarUrl = avatarUrl;
            this.friendCount = friendCount;
        }

        @NonNull
        public Map<String, Object> toFirestoreUpdate() {
            Map<String, Object> payload = new HashMap<>();
            payload.put("displayName", displayName);
            payload.put("avatarUrl", avatarUrl);
            payload.put("updatedAt", new Date());
            return payload;
        }

        @NonNull
        public static UserProfile fromSnapshot(@NonNull FirebaseUser user, @Nullable DocumentSnapshot snapshot) {
            String uid = user.getUid();
            String displayName = "";
            String avatarUrl = "";
            long friendCount = 0L;

            if (snapshot != null && snapshot.exists()) {
                displayName = stringValue(snapshot.getString("displayName"));
                avatarUrl = stringValue(snapshot.getString("avatarUrl"));
                Long rawFriendCount = snapshot.getLong("friendCount");
                friendCount = rawFriendCount == null ? 0L : rawFriendCount;
            }

            if (TextUtils.isEmpty(displayName)) {
                displayName = stringValue(user.getDisplayName());
            }
            if (TextUtils.isEmpty(displayName)) {
                displayName = stringValue(user.getEmail());
            }
            if (TextUtils.isEmpty(displayName)) {
                displayName = "Bạn";
            }

            if (TextUtils.isEmpty(avatarUrl)) {
                Uri photoUrl = user.getPhotoUrl();
                avatarUrl = photoUrl == null ? "" : photoUrl.toString();
            }

            return new UserProfile(uid, displayName, avatarUrl, friendCount);
        }

        @NonNull
        private static String stringValue(@Nullable String raw) {
            return raw == null ? "" : raw.trim();
        }
    }
}
