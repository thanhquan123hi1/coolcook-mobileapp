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
import com.google.firebase.firestore.SetOptions;
import com.google.firebase.firestore.WriteBatch;

import java.util.Calendar;
import java.util.Date;
import java.util.HashMap;
import java.util.Map;

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

    public interface RejectInviteCallback {
        void onSuccess(@NonNull String message);

        void onError(@NonNull String message);
    }

    private static final String USERS_COLLECTION = "users";
    private static final String FRIENDS_COLLECTION = "friends";
    private static final String INVITES_COLLECTION = "friendInvites";
    private static final String FEED_COLLECTION = "feed";
    private static final String JOURNAL_COLLECTION = "journal";
    private static final int RECENT_MOMENT_SYNC_LIMIT = 60;

    private final FirebaseFirestore firestore;

    public FriendInviteRepository(@NonNull FirebaseFirestore firestore) {
        this.firestore = firestore;
    }

    public void createInvite(@NonNull FirebaseUser user, @NonNull CreateInviteCallback callback) {
        DocumentReference inviteRef = firestore.collection(INVITES_COLLECTION).document();
        Date createdAt = new Date();
        Date expiresAt = expiresInDays(7);
        String name = userDisplayName(user);
        String avatarUrl = userAvatarUrl(user);

        Map<String, Object> invitePayload = new HashMap<>();
        invitePayload.put("inviteId", inviteRef.getId());
        invitePayload.put("fromUserId", user.getUid());
        invitePayload.put("fromUserName", name);
        invitePayload.put("fromAvatarUrl", avatarUrl);
        invitePayload.put("createdByUid", user.getUid());
        invitePayload.put("createdByName", name);
        invitePayload.put("createdByAvatarUrl", avatarUrl);
        invitePayload.put("status", FriendInvite.STATUS_PENDING);
        invitePayload.put("createdAt", createdAt);
        invitePayload.put("expiresAt", expiresAt);

        Map<String, Object> userPayload = new HashMap<>();
        userPayload.put("uid", user.getUid());
        userPayload.put("displayName", name);
        userPayload.put("avatarUrl", avatarUrl);
        userPayload.put("email", stringValue(user.getEmail()));
        userPayload.put("updatedAt", new Date());
        userPayload.put("createdAt", FieldValue.serverTimestamp());

        WriteBatch batch = firestore.batch();
        batch.set(inviteRef, invitePayload);
        batch.set(firestore.collection(USERS_COLLECTION).document(user.getUid()), userPayload, SetOptions.merge());
        batch.commit()
                .addOnSuccessListener(unused -> callback.onSuccess(new FriendInvite(
                        inviteRef.getId(),
                        user.getUid(),
                        name,
                        avatarUrl,
                        FriendInvite.STATUS_PENDING,
                        createdAt,
                        expiresAt)))
                .addOnFailureListener(error -> callback.onError("Không tạo được link mời bạn."));
    }

    public void loadInvite(@NonNull String inviteId, @NonNull LoadInviteCallback callback) {
        if (TextUtils.isEmpty(inviteId)) {
            callback.onError("Link mời không hợp lệ.");
            return;
        }

        firestore.collection(INVITES_COLLECTION)
                .document(inviteId)
                .get()
                .addOnSuccessListener(snapshot -> {
                    if (!snapshot.exists()) {
                        callback.onError("Link mời không tồn tại.");
                        return;
                    }
                    FriendInvite invite = FriendInvite.fromSnapshot(snapshot);
                    if (!invite.isActive()) {
                        callback.onError(invite.isExpired()
                                ? "Link mời đã hết hạn."
                                : "Link mời đã được sử dụng.");
                        return;
                    }
                    callback.onSuccess(invite);
                })
                .addOnFailureListener(error -> callback.onError("Không tải được link mời."));
    }

    public void acceptInvite(
            @NonNull String inviteId,
            @NonNull FirebaseUser currentUser,
            @NonNull AcceptInviteCallback callback) {
        if (TextUtils.isEmpty(inviteId)) {
            callback.onError("Link mời không hợp lệ.");
            return;
        }

        DocumentReference inviteRef = firestore.collection(INVITES_COLLECTION).document(inviteId);
        firestore.runTransaction(transaction -> {
                    DocumentSnapshot inviteSnapshot = transaction.get(inviteRef);
                    if (!inviteSnapshot.exists()) {
                        throw new InviteFlowException("Link mời không tồn tại.");
                    }

                    FriendInvite invite = FriendInvite.fromSnapshot(inviteSnapshot);
                    if (invite.getCreatedByUid().equals(currentUser.getUid())) {
                        throw new InviteFlowException("Bạn không thể tự kết bạn với chính mình.");
                    }

                    DocumentReference inviterUserRef = firestore.collection(USERS_COLLECTION)
                            .document(invite.getCreatedByUid());
                    DocumentReference inviteeUserRef = firestore.collection(USERS_COLLECTION)
                            .document(currentUser.getUid());
                    DocumentReference inviterFriendRef = inviterUserRef
                            .collection(FRIENDS_COLLECTION)
                            .document(currentUser.getUid());
                    DocumentReference inviteeFriendRef = inviteeUserRef
                            .collection(FRIENDS_COLLECTION)
                            .document(invite.getCreatedByUid());

                    DocumentSnapshot existingFriend = transaction.get(inviteeFriendRef);
                    if (existingFriend.exists()
                            && "accepted".equals(existingFriend.getString("status"))) {
                        throw new InviteFlowException("Hai bạn đã là bạn bè.");
                    }

                    if (!invite.isActive()) {
                        throw new InviteFlowException(statusMessage(invite));
                    }

                    String currentName = userDisplayName(currentUser);
                    String currentAvatar = userAvatarUrl(currentUser);
                    Date now = new Date();

                    transaction.set(inviterFriendRef, friendPayload(
                            currentUser.getUid(),
                            currentName,
                            currentAvatar,
                            now), SetOptions.merge());
                    transaction.set(inviteeFriendRef, friendPayload(
                            invite.getCreatedByUid(),
                            invite.getCreatedByName(),
                            invite.getCreatedByAvatarUrl(),
                            now), SetOptions.merge());

                    Map<String, Object> inviterCount = new HashMap<>();
                    inviterCount.put("friendCount", FieldValue.increment(1));
                    inviterCount.put("updatedAt", now);
                    transaction.set(inviterUserRef, inviterCount, SetOptions.merge());

                    Map<String, Object> inviteeProfile = new HashMap<>();
                    inviteeProfile.put("displayName", currentName);
                    inviteeProfile.put("avatarUrl", currentAvatar);
                    inviteeProfile.put("friendCount", FieldValue.increment(1));
                    inviteeProfile.put("updatedAt", now);
                    transaction.set(inviteeUserRef, inviteeProfile, SetOptions.merge());

                    Map<String, Object> inviteUpdate = new HashMap<>();
                    inviteUpdate.put("status", FriendInvite.STATUS_ACCEPTED);
                    inviteUpdate.put("acceptedByUid", currentUser.getUid());
                    inviteUpdate.put("acceptedAt", now);
                    inviteUpdate.put("usedByUid", currentUser.getUid());
                    inviteUpdate.put("usedAt", now);
                    transaction.set(inviteRef, inviteUpdate, SetOptions.merge());
                    return invite.getCreatedByUid();
                })
                .addOnSuccessListener(inviterUid -> {
                    callback.onSuccess("Đã kết bạn thành công.");
                    if (!TextUtils.isEmpty(inviterUid)) {
                        syncRecentMomentsBetweenFriends(inviterUid, currentUser.getUid());
                    }
                })
                .addOnFailureListener(error -> callback.onError(readableError(error)));
    }

    public void rejectInvite(
            @NonNull String inviteId,
            @NonNull FirebaseUser currentUser,
            @NonNull RejectInviteCallback callback) {
        if (TextUtils.isEmpty(inviteId)) {
            callback.onError("Link mời không hợp lệ.");
            return;
        }

        DocumentReference inviteRef = firestore.collection(INVITES_COLLECTION).document(inviteId);
        firestore.runTransaction(transaction -> {
                    DocumentSnapshot inviteSnapshot = transaction.get(inviteRef);
                    if (!inviteSnapshot.exists()) {
                        throw new InviteFlowException("Link mời không tồn tại.");
                    }

                    FriendInvite invite = FriendInvite.fromSnapshot(inviteSnapshot);
                    if (invite.getCreatedByUid().equals(currentUser.getUid())) {
                        throw new InviteFlowException("Bạn không thể tự kết bạn với chính mình.");
                    }
                    if (!invite.isActive()) {
                        throw new InviteFlowException(statusMessage(invite));
                    }

                    Map<String, Object> inviteUpdate = new HashMap<>();
                    inviteUpdate.put("status", FriendInvite.STATUS_REJECTED);
                    inviteUpdate.put("rejectedByUid", currentUser.getUid());
                    inviteUpdate.put("rejectedAt", new Date());
                    transaction.set(inviteRef, inviteUpdate, SetOptions.merge());
                    return null;
                })
                .addOnSuccessListener(unused -> callback.onSuccess("Đã từ chối lời mời."))
                .addOnFailureListener(error -> callback.onError(readableError(error)));
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

    @NonNull
    public static String parseInviteInput(@Nullable String rawInput) {
        if (rawInput == null) {
            return "";
        }

        String normalized = rawInput.trim();
        if (normalized.isEmpty()) {
            return "";
        }

        String inviteId = parseInviteId(Uri.parse(normalized));
        if (!TextUtils.isEmpty(inviteId)) {
            return inviteId.trim();
        }

        int slashIndex = normalized.lastIndexOf('/');
        if (slashIndex >= 0 && slashIndex < normalized.length() - 1) {
            normalized = normalized.substring(slashIndex + 1);
        }

        int queryIndex = normalized.indexOf('?');
        if (queryIndex >= 0) {
            normalized = normalized.substring(0, queryIndex);
        }

        return normalized.trim();
    }

    @NonNull
    private Map<String, Object> friendPayload(
            @NonNull String uid,
            @NonNull String displayName,
            @NonNull String avatarUrl,
            @NonNull Date now) {
        Map<String, Object> payload = new HashMap<>();
        payload.put("friendId", uid);
        payload.put("uid", uid);
        payload.put("displayName", displayName);
        payload.put("avatarUrl", avatarUrl);
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
    private String statusMessage(@NonNull FriendInvite invite) {
        if (invite.isExpired() || FriendInvite.STATUS_EXPIRED.equals(invite.getStatus())) {
            return "Link mời đã hết hạn.";
        }
        if (FriendInvite.STATUS_REJECTED.equals(invite.getStatus())) {
            return "Link mời đã bị từ chối.";
        }
        if (FriendInvite.STATUS_ACCEPTED.equals(invite.getStatus())) {
            return "Link mời đã được sử dụng.";
        }
        return "Link mời không còn hiệu lực.";
    }

    @NonNull
    private String readableError(@Nullable Exception error) {
        Throwable cursor = error;
        while (cursor != null) {
            if (cursor instanceof InviteFlowException) {
                return cursor.getMessage() == null ? "Không xác nhận được lời mời." : cursor.getMessage();
            }
            cursor = cursor.getCause();
        }
        return "Không xác nhận được lời mời. Vui lòng thử lại.";
    }

    private static class InviteFlowException extends RuntimeException {
        InviteFlowException(@NonNull String message) {
            super(message);
        }
    }
}
