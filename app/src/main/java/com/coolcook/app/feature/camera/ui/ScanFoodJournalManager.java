package com.coolcook.app.feature.camera.ui;

import android.content.Intent;
import android.graphics.Bitmap;
import android.text.TextUtils;
import android.view.LayoutInflater;
import android.view.View;
import android.widget.EditText;
import android.widget.ImageView;
import android.widget.ProgressBar;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.coolcook.app.R;
import com.coolcook.app.feature.auth.ui.AuthActivity;
import com.coolcook.app.feature.social.data.FriendInviteRepository;
import com.coolcook.app.feature.social.data.JournalFeedRepository;
import com.coolcook.app.feature.social.data.MediaUploadRepository;
import com.coolcook.app.feature.social.model.FriendInvite;
import com.coolcook.app.feature.social.model.JournalFeedItem;
import com.coolcook.app.feature.social.model.MediaUploadResult;
import com.coolcook.app.feature.social.ui.adapter.JournalFeedAdapter;
import com.google.android.material.bottomsheet.BottomSheetDialog;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.FirebaseUser;
import com.google.firebase.firestore.ListenerRegistration;

import java.util.ArrayList;
import java.util.List;

final class ScanFoodJournalManager {

    interface Host {
        void setProcessingUiEnabled(boolean enabled);

        void updateJournalStatus(@NonNull String status);

        void setJournalPreviewUiVisible(boolean visible);
    }

    private static final int JOURNAL_LIST_LIMIT = 30;

    private final ScanFoodActivity activity;
    private final Host host;
    private final RecyclerView rvJournalMoments;
    private final TextView txtJournalEmptyState;
    private final TextView txtJournalFriendCount;
    private final TextView txtJournalUploadProgress;
    private final TextView txtJournalPostError;
    private final EditText edtJournalCaption;
    private final ImageView imgJournalCapturedPreview;
    private final View journalCaptureOverlay;
    private final View btnJournalPostCancel;
    private final View btnJournalPostPublish;
    private final ProgressBar journalPostLoading;
    private final MediaUploadRepository mediaUploadRepository;
    private final JournalFeedRepository journalFeedRepository;
    private final FriendInviteRepository friendInviteRepository;

    private ListenerRegistration journalFeedListener;
    private ListenerRegistration journalProfileListener;
    private JournalFeedAdapter journalFeedAdapter;
    private byte[] pendingJournalImageBytes;
    private String pendingJournalSourceLabel = "camera";
    private boolean isJournalSaveInProgress;

    ScanFoodJournalManager(
            @NonNull ScanFoodActivity activity,
            @NonNull Host host,
            @Nullable RecyclerView rvJournalMoments,
            @Nullable TextView txtJournalEmptyState,
            @Nullable TextView txtJournalFriendCount,
            @Nullable TextView txtJournalUploadProgress,
            @Nullable TextView txtJournalPostError,
            @Nullable EditText edtJournalCaption,
            @Nullable ImageView imgJournalCapturedPreview,
            @Nullable View journalCaptureOverlay,
            @Nullable View btnJournalPostCancel,
            @Nullable View btnJournalPostPublish,
            @Nullable ProgressBar journalPostLoading,
            @NonNull MediaUploadRepository mediaUploadRepository,
            @NonNull JournalFeedRepository journalFeedRepository,
            @NonNull FriendInviteRepository friendInviteRepository) {
        this.activity = activity;
        this.host = host;
        this.rvJournalMoments = rvJournalMoments;
        this.txtJournalEmptyState = txtJournalEmptyState;
        this.txtJournalFriendCount = txtJournalFriendCount;
        this.txtJournalUploadProgress = txtJournalUploadProgress;
        this.txtJournalPostError = txtJournalPostError;
        this.edtJournalCaption = edtJournalCaption;
        this.imgJournalCapturedPreview = imgJournalCapturedPreview;
        this.journalCaptureOverlay = journalCaptureOverlay;
        this.btnJournalPostCancel = btnJournalPostCancel;
        this.btnJournalPostPublish = btnJournalPostPublish;
        this.journalPostLoading = journalPostLoading;
        this.mediaUploadRepository = mediaUploadRepository;
        this.journalFeedRepository = journalFeedRepository;
        this.friendInviteRepository = friendInviteRepository;
    }

    void setupJournalList() {
        if (rvJournalMoments == null) {
            return;
        }

        journalFeedAdapter = new JournalFeedAdapter();
        rvJournalMoments.setLayoutManager(new LinearLayoutManager(activity));
        rvJournalMoments.setAdapter(journalFeedAdapter);
        rvJournalMoments.setNestedScrollingEnabled(false);
        updateJournalEmptyState(0, "Chưa có hoạt động nào!");
    }

    void startRealtimeListeners() {
        stopRealtimeListeners();

        FirebaseUser user = FirebaseAuth.getInstance().getCurrentUser();
        if (user == null) {
            if (journalFeedAdapter != null) {
                journalFeedAdapter.submitItems(new ArrayList<>());
            }
            updateJournalEmptyState(0, "Đăng nhập để xem feed của bạn bè.");
            host.updateJournalStatus("Vui lòng đăng nhập để đăng moment.");
            if (txtJournalFriendCount != null) {
                txtJournalFriendCount.setText("Tất cả bạn bè");
            }
            return;
        }

        journalFeedListener = journalFeedRepository.listenToFeed(user.getUid(), JOURNAL_LIST_LIMIT,
                new JournalFeedRepository.FeedCallback() {
                    @Override
                    public void onItems(@NonNull List<JournalFeedItem> items) {
                        if (journalFeedAdapter != null) {
                            journalFeedAdapter.submitItems(items);
                        }
                        updateJournalEmptyState(items.size(), "Chưa có hoạt động nào!");
                    }

                    @Override
                    public void onError(@NonNull Exception error) {
                        updateJournalEmptyState(0, "Không tải được feed. Vui lòng thử lại.");
                        host.updateJournalStatus("Không tải được feed.");
                    }
                });

        journalProfileListener = journalFeedRepository.listenToUserProfile(user,
                new JournalFeedRepository.UserProfileCallback() {
                    @Override
                    public void onProfile(@NonNull JournalFeedRepository.UserProfile profile) {
                        if (txtJournalFriendCount == null) {
                            return;
                        }
                        if (profile.friendCount <= 0L) {
                            txtJournalFriendCount.setText("Tất cả bạn bè");
                        } else {
                            txtJournalFriendCount.setText(profile.friendCount + " Bạn bè");
                        }
                    }

                    @Override
                    public void onError(@NonNull Exception error) {
                        if (txtJournalFriendCount != null) {
                            txtJournalFriendCount.setText("Bạn bè");
                        }
                    }
                });
    }

    void stopRealtimeListeners() {
        if (journalFeedListener != null) {
            journalFeedListener.remove();
            journalFeedListener = null;
        }
        if (journalProfileListener != null) {
            journalProfileListener.remove();
            journalProfileListener = null;
        }
    }

    void processImageBytesForJournal(@NonNull byte[] imageBytes, @NonNull String sourceLabel, boolean busy) {
        if (busy) {
            return;
        }
        showJournalCapturePreview(imageBytes, sourceLabel);
    }

    boolean isCapturePreviewVisible() {
        return journalCaptureOverlay != null && journalCaptureOverlay.getVisibility() == View.VISIBLE;
    }

    void hideJournalCapturePreview(boolean clearPendingState) {
        if (journalCaptureOverlay != null) {
            journalCaptureOverlay.setVisibility(View.GONE);
        }
        if (imgJournalCapturedPreview != null) {
            imgJournalCapturedPreview.setImageDrawable(null);
        }
        if (edtJournalCaption != null) {
            edtJournalCaption.setText("");
        }
        setJournalPostLoading(false, "");
        showJournalPostError("");
        if (clearPendingState) {
            pendingJournalImageBytes = null;
            pendingJournalSourceLabel = "camera";
        }
        host.setJournalPreviewUiVisible(false);
        host.updateJournalStatus("Sẵn sàng chụp moment mới.");
    }

    void publishPendingJournalMoment(boolean isUsingFrontCamera) {
        if (pendingJournalImageBytes == null || pendingJournalImageBytes.length == 0) {
            showJournalPostError("Không có ảnh để đăng.");
            return;
        }

        FirebaseUser user = FirebaseAuth.getInstance().getCurrentUser();
        if (user == null) {
            Toast.makeText(activity, "Vui lòng đăng nhập để đăng nhật ký", Toast.LENGTH_SHORT).show();
            activity.startActivity(AuthActivity.createIntent(activity, AuthActivity.MODE_LOGIN));
            return;
        }

        String caption = edtJournalCaption == null ? "" : String.valueOf(edtJournalCaption.getText()).trim();
        String cameraFacing = "camera".equals(pendingJournalSourceLabel)
                ? (isUsingFrontCamera ? "front" : "back")
                : "none";

        isJournalSaveInProgress = true;
        host.setProcessingUiEnabled(false);
        setJournalPostLoading(true, "Đang tối ưu ảnh...");
        showJournalPostError("");
        host.updateJournalStatus("Đang đăng moment...");

        mediaUploadRepository.uploadJournalImage(pendingJournalImageBytes,
                new MediaUploadRepository.UploadCallbackListener() {
                    @Override
                    public void onPreparing() {
                        activity.runOnUiThread(() -> setJournalPostLoading(true, "Đang tối ưu ảnh..."));
                    }

                    @Override
                    public void onProgress(int progress) {
                        activity.runOnUiThread(() -> setJournalPostLoading(
                                true,
                                progress <= 0
                                        ? "Đang tải ảnh lên dịch vụ ảnh..."
                                        : "Đang tải ảnh lên dịch vụ ảnh... " + progress + "%"));
                    }

                    @Override
                    public void onSuccess(@NonNull MediaUploadResult result) {
                        activity.runOnUiThread(() -> setJournalPostLoading(true, "Đang cập nhật Firestore..."));
                        journalFeedRepository.publishMoment(
                                user,
                                result,
                                caption,
                                pendingJournalSourceLabel,
                                cameraFacing,
                                new JournalFeedRepository.PublishCallback() {
                                    @Override
                                    public void onSuccess(@NonNull JournalFeedItem item) {
                                        activity.runOnUiThread(() -> {
                                            isJournalSaveInProgress = false;
                                            host.setProcessingUiEnabled(true);
                                            if (journalFeedAdapter != null) {
                                                journalFeedAdapter.prependIfMissing(item);
                                                updateJournalEmptyState(
                                                        journalFeedAdapter.getItemCount(),
                                                        "Chưa có hoạt động nào!");
                                            }
                                            hideJournalCapturePreview(true);
                                            host.updateJournalStatus("Đã đăng moment mới.");
                                            Toast.makeText(
                                                    activity,
                                                    "Đã đăng moment thành công",
                                                    Toast.LENGTH_SHORT).show();
                                        });
                                    }

                                    @Override
                                    public void onError(@NonNull Exception error) {
                                        activity.runOnUiThread(() -> finishJournalSaveWithError(
                                                "Lưu Firestore thất bại. Vui lòng thử lại."));
                                    }
                                });
                    }

                    @Override
                    public void onError(@NonNull String message) {
                        activity.runOnUiThread(() -> finishJournalSaveWithError(message));
                    }
                });
    }

    void openFriendInviteSheet() {
        BottomSheetDialog dialog = new BottomSheetDialog(activity);
        View sheet = LayoutInflater.from(activity).inflate(R.layout.bottom_sheet_friend_invite, null, false);
        dialog.setContentView(sheet);

        TextView txtTitle = sheet.findViewById(R.id.txtFriendSheetTitle);
        TextView txtSubtitle = sheet.findViewById(R.id.txtFriendSheetSubtitle);
        View btnCreateInviteLink = sheet.findViewById(R.id.btnCreateInviteLink);
        View btnClose = sheet.findViewById(R.id.btnFriendSheetClose);

        FirebaseUser user = FirebaseAuth.getInstance().getCurrentUser();
        if (txtTitle != null) {
            txtTitle.setText(txtJournalFriendCount == null ? "Bạn bè" : txtJournalFriendCount.getText());
        }
        if (txtSubtitle != null && user == null) {
            txtSubtitle.setText("Đăng nhập để tạo link mời bạn vào journal feed.");
        }

        if (btnCreateInviteLink != null) {
            btnCreateInviteLink.setOnClickListener(v -> {
                FirebaseUser currentUser = FirebaseAuth.getInstance().getCurrentUser();
                if (currentUser == null) {
                    dialog.dismiss();
                    activity.startActivity(AuthActivity.createIntent(activity, AuthActivity.MODE_LOGIN));
                    return;
                }

                v.setEnabled(false);
                v.setAlpha(0.7f);
                friendInviteRepository.createInvite(currentUser, new FriendInviteRepository.CreateInviteCallback() {
                    @Override
                    public void onSuccess(@NonNull FriendInvite invite) {
                        activity.runOnUiThread(() -> {
                            dialog.dismiss();
                            shareInvite(invite);
                        });
                    }

                    @Override
                    public void onError(@NonNull String message) {
                        activity.runOnUiThread(() -> {
                            v.setEnabled(true);
                            v.setAlpha(1f);
                            Toast.makeText(activity, message, Toast.LENGTH_SHORT).show();
                        });
                    }
                });
            });
        }

        if (btnClose != null) {
            btnClose.setOnClickListener(v -> dialog.dismiss());
        }

        dialog.show();
    }

    boolean isJournalSaveInProgress() {
        return isJournalSaveInProgress;
    }

    private void showJournalCapturePreview(@NonNull byte[] imageBytes, @NonNull String sourceLabel) {
        if (imgJournalCapturedPreview == null || journalCaptureOverlay == null) {
            return;
        }

        Bitmap bitmap = ScanFoodImageUtils.decodePreviewBitmap(imageBytes);
        if (bitmap == null) {
            host.updateJournalStatus("Không mở được ảnh vừa chụp.");
            Toast.makeText(activity, "Không hiển thị được ảnh", Toast.LENGTH_SHORT).show();
            return;
        }

        pendingJournalImageBytes = imageBytes;
        pendingJournalSourceLabel = sourceLabel;
        imgJournalCapturedPreview.setImageBitmap(bitmap);
        if (edtJournalCaption != null) {
            edtJournalCaption.setText("");
        }
        setJournalPostLoading(false, "");
        showJournalPostError("");
        journalCaptureOverlay.setVisibility(View.VISIBLE);
        host.setJournalPreviewUiVisible(true);
        host.updateJournalStatus("Thêm caption rồi bấm Đăng.");
    }

    private void updateJournalEmptyState(int count, @NonNull String emptyText) {
        if (txtJournalEmptyState == null) {
            return;
        }
        boolean showEmpty = count <= 0;
        txtJournalEmptyState.setVisibility(showEmpty ? View.VISIBLE : View.GONE);
        if (showEmpty) {
            txtJournalEmptyState.setText(emptyText);
        }
    }

    private void setJournalPostLoading(boolean loading, @NonNull String statusText) {
        if (journalPostLoading != null) {
            journalPostLoading.setVisibility(loading ? View.VISIBLE : View.GONE);
        }
        if (txtJournalUploadProgress != null) {
            txtJournalUploadProgress.setText(statusText);
            txtJournalUploadProgress.setVisibility(TextUtils.isEmpty(statusText) ? View.GONE : View.VISIBLE);
        }
        if (btnJournalPostCancel != null) {
            btnJournalPostCancel.setEnabled(!loading);
            btnJournalPostCancel.setAlpha(loading ? 0.65f : 1f);
        }
        if (btnJournalPostPublish != null) {
            btnJournalPostPublish.setEnabled(!loading);
            btnJournalPostPublish.setAlpha(loading ? 0.65f : 1f);
        }
    }

    private void showJournalPostError(@NonNull String message) {
        if (txtJournalPostError == null) {
            return;
        }
        if (TextUtils.isEmpty(message)) {
            txtJournalPostError.setVisibility(View.GONE);
            txtJournalPostError.setText("");
            return;
        }
        txtJournalPostError.setVisibility(View.VISIBLE);
        txtJournalPostError.setText(message);
    }

    private void finishJournalSaveWithError(@NonNull String errorMessage) {
        isJournalSaveInProgress = false;
        host.setProcessingUiEnabled(true);
        setJournalPostLoading(false, "");
        showJournalPostError(errorMessage);
        host.updateJournalStatus(errorMessage);
        Toast.makeText(activity, errorMessage, Toast.LENGTH_SHORT).show();
    }

    private void shareInvite(@NonNull FriendInvite invite) {
        String shareText = "Kết bạn với mình trên CoolCook nhé.\n"
                + invite.buildDeepLink()
            + "\nNếu app link web đã được cấu hình trên domain, bạn cũng có thể thử:\n"
                + invite.buildWebLink();
        Intent intent = new Intent(Intent.ACTION_SEND);
        intent.setType("text/plain");
        intent.putExtra(Intent.EXTRA_TEXT, shareText);
        activity.startActivity(Intent.createChooser(intent, "Chia sẻ lời mời"));
    }
}
