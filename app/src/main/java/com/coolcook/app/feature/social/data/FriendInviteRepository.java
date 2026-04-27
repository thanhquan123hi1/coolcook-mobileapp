package com.coolcook.app.feature.social.data;

import android.net.Uri;
import android.text.TextUtils;
import android.util.Log;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.coolcook.app.feature.social.model.FriendInvite;
import com.google.firebase.auth.FirebaseUser;
import com.google.firebase.firestore.DocumentReference;
import com.google.firebase.firestore.DocumentSnapshot;
import com.google.firebase.firestore.FieldValue;
import com.google.firebase.firestore.FirebaseFirestore;
import com.google.firebase.firestore.Query;
import com.google.firebase.firestore.QuerySnapshot;
import com.google.firebase.firestore.SetOptions;
import com.google.firebase.firestore.WriteBatch;

import java.util.Calendar;
import java.util.Date;
import java.util.HashMap;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.ThreadLocalRandom;

public class FriendInviteRepository {
    private static final String TAG = "FriendInviteRepository";

    public interface CreateInviteCallback {
        void onSuccess(@NonNull FriendInvite invite);

        void onError(@NonNull String message);
    }

    public interface LoadInviteCallback {
        void onSuccess(@NonNull FriendInvite invite);

        void onError(@NonNull String message);
    }

    public interface AcceptInviteCallback {
        void onSuccess(@NonNull String message);

        void onError(@NonNull String message);
    }

    public interface EnsureFriendCodeCallback {
        void onSuccess(@NonNull String friendCode);

        void onError(@NonNull String message);
    }

    private static final String USERS_COLLECTION = "users";
    private static final String FRIENDS_COLLECTION = "friends";
    private static final String FEED_COLLECTION = "feed";
    private static final String JOURNAL_COLLECTION = "journal";
    private static final int RECENT_MOMENT_SYNC_LIMIT = 60;
    private static final int MAX_CREATE_CODE_ATTEMPTS = 12;
    private static final char[] PREFIX_ALPHABET = "CCKLO".toCharArray();

    private final FirebaseFirestore firestore;

    public FriendInviteRepository(@NonNull FirebaseFirestore firestore) {
        this.firestore = firestore;
    }

    public void ensureFriendCodeIfMissing(@Nullable FirebaseUser user) {
        if (user == null) {
            return;
        }
        ensureFriendCode(user, null);
    }

    public void ensureFriendCode(
            @NonNull FirebaseUser user,
            @Nullable EnsureFriendCodeCallback callback) {
        userRef(user.getUid())
                .get()
                .addOnSuccessListener(snapshot -> {
                    String existingCode = normalizeFriendCode(snapshot.getString("friendCode"));
                    if (!TextUtils.isEmpty(existingCode)) {
                        userRef(user.getUid())
                                .set(buildUserProfileUpdate(user, existingCode), SetOptions.merge())
                                .addOnSuccessListener(unused -> {
                                    if (callback != null) {
                                        callback.onSuccess(existingCode);
                                    }
                                })
                                .addOnFailureListener(error -> {
                                    if (callback != null) {
                                        callback.onError("Không thể cập nhật mã kết bạn.");
                                    }
                                });
                        return;
                    }
                    createUniqueFriendCode(user, callback, MAX_CREATE_CODE_ATTEMPTS);
                })
                .addOnFailureListener(error -> {
                    if (callback != null) {
                        callback.onError("Không thể tạo mã kết bạn.");
                    }
                });
    }

    public void createInvite(@NonNull FirebaseUser user, @NonNull CreateInviteCallback callback) {
        ensureFriendCode(user, new EnsureFriendCodeCallback() {
            @Override
            public void onSuccess(@NonNull String friendCode) {
                Date createdAt = new Date();
                callback.onSuccess(new FriendInvite(
                        friendCode,
                        user.getUid(),
                        userDisplayName(user),
                        userAvatarUrl(user),
                        FriendInvite.STATUS_ACTIVE,
                        createdAt,
                        expiresInDays(3650)));
            }

            @Override
            public void onError(@NonNull String message) {
                callback.onError(message);
            }
        });
    }

    public void loadInvite(@NonNull String inviteId, @NonNull LoadInviteCallback callback) {
        String normalizedCode = normalizeFriendCode(inviteId);
        if (TextUtils.isEmpty(normalizedCode)) {
            callback.onError("Mã kết bạn không hợp lệ.");
            return;
        }

        resolveUserByFriendCode(normalizedCode)
                .addOnSuccessListener(snapshot -> {
                    if (snapshot == null || !snapshot.exists()) {
                        callback.onError("Không tìm thấy người dùng với mã này.");
                        return;
                    }
                    callback.onSuccess(toInvite(snapshot, normalizedCode));
                })
                .addOnFailureListener(error -> callback.onError("Không tải được thông tin kết bạn."));
    }

    public void acceptInvite(
            @NonNull String inviteId,
            @NonNull FirebaseUser currentUser,
            @NonNull AcceptInviteCallback callback) {
        ensureFriendCode(currentUser, new EnsureFriendCodeCallback() {
            @Override
            public void onSuccess(@NonNull String ownFriendCode) {
                acceptInviteInternal(inviteId, currentUser, ownFriendCode, callback);
            }

            @Override
            public void onError(@NonNull String message) {
                callback.onError(message);
            }
        });
    }

    @NonNull
    public static String parseInviteId(@Nullable Uri uri) {
        if (uri == null) {
            return "";
        }

        String queryInviteId = uri.getQueryParameter("inviteId");
        if (!TextUtils.isEmpty(queryInviteId)) {
            return queryInviteId;
        }

        String lastPath = uri.getLastPathSegment();
        return lastPath == null ? "" : lastPath;
    }

    private void acceptInviteInternal(
            @NonNull String inviteId,
            @NonNull FirebaseUser currentUser,
            @NonNull String ownFriendCode,
            @NonNull AcceptInviteCallback callback) {
        String normalizedCode = normalizeFriendCode(inviteId);
        if (TextUtils.isEmpty(normalizedCode)) {
            callback.onError("Mã kết bạn không hợp lệ.");
            return;
        }
        if (normalizedCode.equals(ownFriendCode)) {
            callback.onError("Bạn không thể kết bạn với chính mình.");
            return;
        }

        resolveUserByFriendCode(normalizedCode)
                .addOnSuccessListener(friendUserSnapshot -> {
                    if (friendUserSnapshot == null || !friendUserSnapshot.exists()) {
                        callback.onError("Không tìm thấy người dùng với mã này.");
                        return;
                    }

                    String friendUid = normalizeUid(friendUserSnapshot.getId());
                    if (TextUtils.isEmpty(friendUid)) {
                        callback.onError("Không tìm thấy người dùng với mã này.");
                        return;
                    }
                    if (friendUid.equals(currentUser.getUid())) {
                        callback.onError("Bạn không thể kết bạn với chính mình.");
                        return;
                    }

                    DocumentReference currentUserRef = userRef(currentUser.getUid());
                    DocumentReference friendUserRef = userRef(friendUid);
                    DocumentReference currentFriendRef = currentUserRef.collection(FRIENDS_COLLECTION).document(friendUid);
                    DocumentReference reverseFriendRef = friendUserRef.collection(FRIENDS_COLLECTION).document(currentUser.getUid());

                    currentFriendRef.get()
                            .addOnSuccessListener(existingFriend -> {
                                if (existingFriend.exists()
                                        && "accepted".equals(existingFriend.getString("status"))) {
                                    callback.onError("Hai bạn đã là bạn bè.");
                                    return;
                                }

                                currentUserRef.get()
                                        .addOnSuccessListener(currentUserSnapshot -> {
                                            Date now = new Date();
                                            String currentName = firstNonEmpty(
                                                    currentUserSnapshot.getString("displayName"),
                                                    userDisplayName(currentUser));
                                            String currentAvatar = firstNonEmpty(
                                                    currentUserSnapshot.getString("avatarUrl"),
                                                    userAvatarUrl(currentUser));
                                            String friendName = firstNonEmpty(
                                                    friendUserSnapshot.getString("displayName"),
                                                    "Bạn mới");
                                            String friendAvatar = firstNonEmpty(
                                                    friendUserSnapshot.getString("avatarUrl"),
                                                    "");
                                            String friendCode = normalizeFriendCode(friendUserSnapshot.getString("friendCode"));

                                            WriteBatch batch = firestore.batch();
                                            batch.set(currentFriendRef, buildFriendPayload(
                                                    friendUid,
                                                    friendName,
                                                    friendAvatar,
                                                    friendCode,
                                                    now), SetOptions.merge());
                                            batch.set(reverseFriendRef, buildFriendPayload(
                                                    currentUser.getUid(),
                                                    currentName,
                                                    currentAvatar,
                                                    ownFriendCode,
                                                    now), SetOptions.merge());
                                            batch.set(currentUserRef, buildUserProfileUpdate(
                                                    currentName,
                                                    currentAvatar,
                                                    ownFriendCode,
                                                    true), SetOptions.merge());
                                            batch.set(friendUserRef, buildUserProfileUpdate(
                                                    friendName,
                                                    friendAvatar,
                                                    friendCode,
                                                    true), SetOptions.merge());
                                            batch.commit()
                                                    .addOnSuccessListener(unused -> {
                                                        callback.onSuccess("Đã kết bạn thành công.");
                                                        syncRecentMomentsBetweenFriends(friendUid, currentUser.getUid());
                                                    })
                                                    .addOnFailureListener(error -> callback.onError(readableError(error)));
                                        })
                                        .addOnFailureListener(error -> callback.onError("Không thể tải hồ sơ hiện tại."));
                            })
                            .addOnFailureListener(error -> callback.onError(readableError(error)));
                })
                .addOnFailureListener(error -> callback.onError(readableError(error)));
    }

    @NonNull
    private DocumentReference userRef(@NonNull String uid) {
        return firestore.collection(USERS_COLLECTION).document(uid);
    }

    private com.google.android.gms.tasks.Task<DocumentSnapshot> resolveUserByFriendCode(@NonNull String friendCode) {
        return firestore.collection(USERS_COLLECTION)
                .whereEqualTo("friendCode", friendCode)
                .limit(1)
                .get()
                .continueWith(task -> {
                    if (!task.isSuccessful()) {
                        throw task.getException() == null
                                ? new IllegalStateException("friendCode lookup failed")
                                : task.getException();
                    }
                    QuerySnapshot snapshot = task.getResult();
                    if (snapshot == null || snapshot.isEmpty()) {
                        return null;
                    }
                    return snapshot.getDocuments().get(0);
                });
    }

    private void createUniqueFriendCode(
            @NonNull FirebaseUser user,
            @Nullable EnsureFriendCodeCallback callback,
            int attemptsRemaining) {
        if (attemptsRemaining <= 0) {
            if (callback != null) {
                callback.onError("Không thể tạo mã kết bạn.");
            }
            return;
        }

        String candidateCode = randomFriendCode();
        firestore.collection(USERS_COLLECTION)
                .whereEqualTo("friendCode", candidateCode)
                .limit(1)
                .get()
                .addOnSuccessListener(snapshot -> {
                    if (snapshot != null && !snapshot.isEmpty()) {
                        createUniqueFriendCode(user, callback, attemptsRemaining - 1);
                        return;
                    }

                    userRef(user.getUid())
                            .set(buildUserProfileUpdate(user, candidateCode), SetOptions.merge())
                            .addOnSuccessListener(unused -> {
                                if (callback != null) {
                                    callback.onSuccess(candidateCode);
                                }
                            })
                            .addOnFailureListener(error -> createUniqueFriendCode(user, callback, attemptsRemaining - 1));
                })
                .addOnFailureListener(error -> {
                    if (callback != null) {
                        callback.onError("Không thể tạo mã kết bạn.");
                    }
                });
    }

    @NonNull
    private Map<String, Object> buildUserProfileUpdate(
            @NonNull FirebaseUser user,
            @NonNull String friendCode) {
        return buildUserProfileUpdate(
                userDisplayName(user),
                userAvatarUrl(user),
                friendCode,
                false);
    }

    @NonNull
    private Map<String, Object> buildUserProfileUpdate(
            @NonNull String displayName,
            @NonNull String avatarUrl,
            @NonNull String friendCode,
            boolean includeFriendCountIncrement) {
        Map<String, Object> payload = new HashMap<>();
        payload.put("displayName", displayName);
        payload.put("avatarUrl", avatarUrl);
        payload.put("friendCode", friendCode);
        payload.put("updatedAt", new Date());
        payload.put("createdAt", FieldValue.serverTimestamp());
        if (includeFriendCountIncrement) {
            payload.put("friendCount", FieldValue.increment(1));
        }
        return payload;
    }

    @NonNull
    private Map<String, Object> buildFriendPayload(
            @NonNull String uid,
            @NonNull String displayName,
            @NonNull String avatarUrl,
            @NonNull String friendCode,
            @NonNull Date now) {
        Map<String, Object> payload = new HashMap<>();
        payload.put("uid", uid);
        payload.put("displayName", displayName);
        payload.put("avatarUrl", avatarUrl);
        payload.put("photoUrl", avatarUrl);
        payload.put("friendCode", friendCode);
        payload.put("status", "accepted");
        payload.put("createdAt", now);
        payload.put("updatedAt", now);
        return payload;
    }

    private void syncRecentMomentsBetweenFriends(
            @NonNull String inviterUid,
            @NonNull String inviteeUid) {
        copyRecentMomentsToFriendFeed(inviterUid, inviteeUid);
        copyRecentMomentsToFriendFeed(inviteeUid, inviterUid);
    }

    private void copyRecentMomentsToFriendFeed(
            @NonNull String ownerUid,
            @NonNull String friendUid) {
        firestore.collection(USERS_COLLECTION)
                .document(ownerUid)
                .collection(JOURNAL_COLLECTION)
                .orderBy("createdAt", Query.Direction.DESCENDING)
                .limit(RECENT_MOMENT_SYNC_LIMIT)
                .get()
                .addOnSuccessListener(snapshot -> {
                    WriteBatch batch = firestore.batch();
                    int writeCount = 0;
                    for (DocumentSnapshot doc : snapshot.getDocuments()) {
                        String momentId = stringValue(doc.getString("momentId"));
                        if (TextUtils.isEmpty(momentId)) {
                            momentId = doc.getId();
                        }
                        Map<String, Object> feedPayload = buildFeedPayloadFromJournal(doc, momentId);
                        if (feedPayload.isEmpty()) {
                            continue;
                        }
                        batch.set(firestore.collection(USERS_COLLECTION)
                                        .document(friendUid)
                                        .collection(FEED_COLLECTION)
                                        .document(momentId),
                                feedPayload,
                                SetOptions.merge());
                        writeCount++;
                    }
                    if (writeCount <= 0) {
                        return;
                    }
                    batch.commit().addOnFailureListener(error ->
                            Log.w(TAG, "Unable to sync friend feed for " + ownerUid + " -> " + friendUid, error));
                })
                .addOnFailureListener(error ->
                        Log.w(TAG, "Unable to load recent journal moments for " + ownerUid, error));
    }

    @NonNull
    private Map<String, Object> buildFeedPayloadFromJournal(
            @NonNull DocumentSnapshot snapshot,
            @NonNull String momentId) {
        String imageUrl = stringValue(snapshot.getString("imageUrl"));
        String thumbUrl = stringValue(snapshot.getString("thumbUrl"));
        String thumbnailUrl = stringValue(snapshot.getString("thumbnailUrl"));
        if (TextUtils.isEmpty(imageUrl) && TextUtils.isEmpty(thumbUrl) && TextUtils.isEmpty(thumbnailUrl)) {
            return new HashMap<>();
        }

        Map<String, Object> payload = new HashMap<>();
        payload.put("momentId", momentId);
        payload.put("ownerUid", stringValue(snapshot.getString("ownerUid")));
        payload.put("ownerName", stringValue(snapshot.getString("ownerName")));
        payload.put("ownerAvatarUrl", stringValue(snapshot.getString("ownerAvatarUrl")));
        payload.put("imageUrl", imageUrl);
        payload.put("thumbUrl", TextUtils.isEmpty(thumbUrl) ? thumbnailUrl : thumbUrl);
        payload.put("thumbnailUrl", TextUtils.isEmpty(thumbnailUrl) ? thumbUrl : thumbnailUrl);
        payload.put("cloudinaryPublicId", stringValue(snapshot.getString("cloudinaryPublicId")));
        payload.put("caption", stringValue(snapshot.getString("caption")));
        payload.put("createdAt", snapshot.get("createdAt"));
        payload.put("serverCreatedAt", FieldValue.serverTimestamp());
        payload.put("visibility", TextUtils.isEmpty(snapshot.getString("visibility"))
                ? "friends"
                : snapshot.getString("visibility"));

        Long width = snapshot.getLong("width");
        Long height = snapshot.getLong("height");
        if (width != null) {
            payload.put("width", width);
        }
        if (height != null) {
            payload.put("height", height);
        }
        return payload;
    }

    @NonNull
    private FriendInvite toInvite(@NonNull DocumentSnapshot snapshot, @NonNull String friendCode) {
        return new FriendInvite(
                friendCode,
                firstNonEmpty(snapshot.getString("uid"), snapshot.getId()),
                firstNonEmpty(snapshot.getString("displayName"), "Bạn mới"),
                firstNonEmpty(snapshot.getString("avatarUrl"), snapshot.getString("photoUrl"), ""),
                FriendInvite.STATUS_ACTIVE,
                rawDate(snapshot.get("createdAt")),
                expiresInDays(3650));
    }

    @Nullable
    private Date rawDate(@Nullable Object value) {
        if (value instanceof Date) {
            return (Date) value;
        }
        return null;
    }

    @NonNull
    private Date expiresInDays(int days) {
        Calendar calendar = Calendar.getInstance();
        calendar.add(Calendar.DAY_OF_YEAR, days);
        return calendar.getTime();
    }

    @NonNull
    private static String userDisplayName(@NonNull FirebaseUser user) {
        String name = user.getDisplayName();
        if (!TextUtils.isEmpty(name)) {
            return name.trim();
        }
        String email = user.getEmail();
        if (!TextUtils.isEmpty(email)) {
            return email.trim();
        }
        return "Bạn mới";
    }

    @NonNull
    private static String userAvatarUrl(@NonNull FirebaseUser user) {
        Uri photoUrl = user.getPhotoUrl();
        return photoUrl == null ? "" : photoUrl.toString();
    }

    @NonNull
    private static String stringValue(@Nullable String raw) {
        return raw == null ? "" : raw.trim();
    }

    @NonNull
    private static String normalizeUid(@Nullable String raw) {
        return raw == null ? "" : raw.trim();
    }

    @NonNull
    private static String normalizeFriendCode(@Nullable String raw) {
        if (raw == null) {
            return "";
        }
        return raw.trim().replace(" ", "").toUpperCase(Locale.ROOT);
    }

    @NonNull
    private static String firstNonEmpty(@Nullable String... values) {
        for (String value : values) {
            if (!TextUtils.isEmpty(value)) {
                return value.trim();
            }
        }
        return "";
    }

    @NonNull
    private static String randomFriendCode() {
        ThreadLocalRandom random = ThreadLocalRandom.current();
        char prefixA = PREFIX_ALPHABET[random.nextInt(PREFIX_ALPHABET.length)];
        char prefixB = PREFIX_ALPHABET[random.nextInt(PREFIX_ALPHABET.length)];
        int digits = random.nextInt(100000, 1000000);
        return new StringBuilder(8)
                .append(prefixA)
                .append(prefixB)
                .append(digits)
                .toString();
    }

    @NonNull
    private String readableError(@Nullable Exception error) {
        Throwable cursor = error;
        while (cursor != null) {
            if (cursor instanceof InviteFlowException) {
                return cursor.getMessage() == null
                        ? "Không thể xác nhận kết bạn."
                        : cursor.getMessage();
            }
            cursor = cursor.getCause();
        }
        return "Không thể xác nhận kết bạn. Vui lòng thử lại.";
    }

    private static class InviteFlowException extends RuntimeException {
        InviteFlowException(@NonNull String message) {
            super(message);
        }
    }
}
