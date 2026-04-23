package com.coolcook.app.ui.scan.data;

import android.net.Uri;
import android.text.TextUtils;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.coolcook.app.ui.scan.model.FriendInvite;
import com.google.firebase.auth.FirebaseUser;
import com.google.firebase.firestore.DocumentReference;
import com.google.firebase.firestore.DocumentSnapshot;
import com.google.firebase.firestore.FieldValue;
import com.google.firebase.firestore.FirebaseFirestore;
import com.google.firebase.firestore.SetOptions;
import com.google.firebase.firestore.WriteBatch;

import java.util.Calendar;
import java.util.Date;
import java.util.HashMap;
import java.util.Map;

public class FriendInviteRepository {

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

    private static final String USERS_COLLECTION = "users";
    private static final String FRIENDS_COLLECTION = "friends";
    private static final String INVITES_COLLECTION = "friendInvites";

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
        invitePayload.put("createdByUid", user.getUid());
        invitePayload.put("createdByName", name);
        invitePayload.put("createdByAvatarUrl", avatarUrl);
        invitePayload.put("status", FriendInvite.STATUS_ACTIVE);
        invitePayload.put("createdAt", createdAt);
        invitePayload.put("expiresAt", expiresAt);

        Map<String, Object> userPayload = new HashMap<>();
        userPayload.put("displayName", name);
        userPayload.put("avatarUrl", avatarUrl);
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
                        FriendInvite.STATUS_ACTIVE,
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
                    if (!invite.isActive()) {
                        throw new InviteFlowException(invite.isExpired()
                                ? "Link mời đã hết hạn."
                                : "Link mời đã được sử dụng.");
                    }
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
                    inviteUpdate.put("status", FriendInvite.STATUS_USED);
                    inviteUpdate.put("usedByUid", currentUser.getUid());
                    inviteUpdate.put("usedAt", now);
                    transaction.set(inviteRef, inviteUpdate, SetOptions.merge());
                    return null;
                })
                .addOnSuccessListener(unused -> callback.onSuccess("Đã kết bạn thành công."))
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
    private Map<String, Object> friendPayload(
            @NonNull String uid,
            @NonNull String displayName,
            @NonNull String avatarUrl,
            @NonNull Date now) {
        Map<String, Object> payload = new HashMap<>();
        payload.put("uid", uid);
        payload.put("displayName", displayName);
        payload.put("avatarUrl", avatarUrl);
        payload.put("status", "accepted");
        payload.put("createdAt", now);
        payload.put("updatedAt", now);
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
