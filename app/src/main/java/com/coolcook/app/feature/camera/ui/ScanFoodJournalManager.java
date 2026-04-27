package com.coolcook.app.feature.camera.ui;

import android.content.Intent;
import android.content.ClipData;
import android.content.ClipboardManager;
import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.drawable.Drawable;
import android.os.Handler;
import android.os.Looper;
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
import com.coolcook.app.feature.social.ui.FriendInviteActivity;
import com.coolcook.app.feature.social.ui.adapter.JournalFeedAdapter;
import com.google.android.material.bottomsheet.BottomSheetDialog;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.FirebaseUser;
import com.google.firebase.firestore.ListenerRegistration;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

final class ScanFoodJournalManager {

    interface ImageSaveCallback {
        void onSuccess();

        void onError(@NonNull String message);
    }

    interface Host {
        void setProcessingUiEnabled(boolean enabled);

        void updateJournalStatus(@NonNull String status);

        void setJournalPreviewUiVisible(boolean visible);

        void saveJournalPreviewImageToGallery(
                @NonNull byte[] imageBytes,
                @NonNull String fileName,
                @NonNull ImageSaveCallback callback);
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
    private final TextView txtJournalPostSentBadge;
    private final TextView iconJournalPostSend;
    private final TextView iconJournalPostSuccess;
    private final MediaUploadRepository mediaUploadRepository;
    private final JournalFeedRepository journalFeedRepository;
    private final FriendInviteRepository friendInviteRepository;

    private ListenerRegistration journalFeedListener;
    private ListenerRegistration journalProfileListener;
    private JournalFeedAdapter journalFeedAdapter;
    private byte[] pendingJournalImageBytes;
    private String pendingJournalSourceLabel = "camera";
    private boolean isJournalSaveInProgress;
    private boolean isJournalDownloadInProgress;
    @Nullable
    private String activeDownloadToken;
    @Nullable
    private String lastDownloadedToken;

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
        this.txtJournalPostSentBadge = resolveTextView(journalCaptureOverlay, R.id.txtPreviewSentBadgeOld, R.id.txtPreviewSentBadgeOld);
        this.iconJournalPostSend = resolveTextView(btnJournalPostPublish, R.id.iconPreviewSend, R.id.iconPreviewSendOld);
        this.iconJournalPostSuccess = resolveTextView(btnJournalPostPublish, R.id.iconPreviewSuccess, R.id.iconPreviewSuccessOld);
        this.mediaUploadRepository = mediaUploadRepository;
        this.journalFeedRepository = journalFeedRepository;
        this.friendInviteRepository = friendInviteRepository;
    }

    @Nullable
    private static TextView resolveTextView(@Nullable View root, int preferredId, int fallbackId) {
        if (root == null) {
            return null;
        }
        TextView preferred = root.findViewById(preferredId);
        return preferred != null ? preferred : root.findViewById(fallbackId);
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
        resetJournalPostActionState();
        setJournalPostLoading(false, "");
        showJournalPostError("");
        setJournalPostSentBadgeVisible(false);
        if (clearPendingState) {
            pendingJournalImageBytes = null;
            pendingJournalSourceLabel = "camera";
            resetJournalDownloadState();
        }
        host.setJournalPreviewUiVisible(false);
        host.updateJournalStatus("Sẵn sàng chụp moment mới.");
    }

    void downloadPendingJournalImage(@Nullable View downloadButton) {
        if (pendingJournalImageBytes == null || pendingJournalImageBytes.length == 0) {
            Toast.makeText(activity, "KhÃ´ng cÃ³ áº£nh Ä‘á»ƒ táº£i", Toast.LENGTH_SHORT).show();
            return;
        }

        String imageToken = buildImageToken(pendingJournalImageBytes);
        if (isJournalDownloadInProgress && TextUtils.equals(activeDownloadToken, imageToken)) {
            Toast.makeText(activity, "áº¢nh Ä‘ang Ä‘Æ°á»£c lÆ°u", Toast.LENGTH_SHORT).show();
            return;
        }
        if (TextUtils.equals(lastDownloadedToken, imageToken)) {
            Toast.makeText(activity, "áº¢nh nÃ y Ä‘Ã£ táº£i vá» rá»“i", Toast.LENGTH_SHORT).show();
            return;
        }

        isJournalDownloadInProgress = true;
        activeDownloadToken = imageToken;
        updateDownloadButtonState(downloadButton, false);
        animateDownloadButtonTap(downloadButton);

        host.saveJournalPreviewImageToGallery(
                pendingJournalImageBytes.clone(),
                "coolcook-journal-" + System.currentTimeMillis() + ".jpg",
                new ImageSaveCallback() {
                    @Override
                    public void onSuccess() {
                        isJournalDownloadInProgress = false;
                        activeDownloadToken = null;
                        lastDownloadedToken = imageToken;
                        updateDownloadButtonState(downloadButton, true);
                        Toast.makeText(activity, "ÄÃ£ táº£i vá»", Toast.LENGTH_SHORT).show();
                    }

                    @Override
                    public void onError(@NonNull String message) {
                        isJournalDownloadInProgress = false;
                        activeDownloadToken = null;
                        updateDownloadButtonState(downloadButton, true);
                        Toast.makeText(activity, message, Toast.LENGTH_SHORT).show();
                    }
                });
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
                                            showJournalPostSuccessThenReset();
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
        TextView txtInviteLink = sheet.findViewById(R.id.txtInviteLink);
        EditText edtInviteInput = sheet.findViewById(R.id.edtFriendInviteInput);
        View btnCreateInviteLink = sheet.findViewById(R.id.btnCreateInviteLink);
        View btnOpenInviteInput = sheet.findViewById(R.id.btnOpenInviteInput);
        View btnCopyInviteLink = sheet.findViewById(R.id.btnCopyInviteLink);
        View btnShareInviteLink = sheet.findViewById(R.id.btnShareInviteLink);
        View btnClose = sheet.findViewById(R.id.btnFriendSheetClose);
        final FriendInvite[] createdInvite = new FriendInvite[1];

        FirebaseUser user = FirebaseAuth.getInstance().getCurrentUser();
        if (txtTitle != null) {
            txtTitle.setText(txtJournalFriendCount == null ? "Bạn bè" : txtJournalFriendCount.getText());
        }
        if (txtSubtitle != null && user == null) {
            txtSubtitle.setText("Đăng nhập để tạo link mời bạn vào journal feed.");
        }

        if (btnOpenInviteInput != null) {
            btnOpenInviteInput.setOnClickListener(v -> {
                String inviteId = FriendInviteRepository.parseInviteInput(
                        edtInviteInput == null ? "" : edtInviteInput.getText().toString());
                if (TextUtils.isEmpty(inviteId)) {
                    Toast.makeText(activity, "Dan link hoac ma moi cua ban vao o tren.", Toast.LENGTH_SHORT).show();
                    return;
                }

                Intent intent = new Intent(activity, FriendInviteActivity.class);
                intent.putExtra("inviteId", inviteId);
                activity.startActivity(intent);
                dialog.dismiss();
            });
        }

        if (btnCreateInviteLink != null) {
            btnCreateInviteLink.setOnClickListener(v -> {
                FirebaseUser currentUser = FirebaseAuth.getInstance().getCurrentUser();
                if (currentUser == null) {
                    dialog.dismiss();
                    activity.startActivity(AuthActivity.createIntent(activity, AuthActivity.MODE_LOGIN));
                    return;
                }

                dialog.dismiss();
                activity.startActivity(new Intent(activity, FriendInviteActivity.class));
            });
        }

        if (btnCopyInviteLink != null) {
            btnCopyInviteLink.setOnClickListener(v -> {
                if (createdInvite[0] == null) {
                    return;
                }
                copyInvite(createdInvite[0]);
            });
        }

        if (btnShareInviteLink != null) {
            btnShareInviteLink.setOnClickListener(v -> {
                if (createdInvite[0] == null) {
                    return;
                }
                shareInvite(createdInvite[0]);
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
        resetJournalDownloadState();
        imgJournalCapturedPreview.setImageBitmap(bitmap);
        if (edtJournalCaption != null) {
            edtJournalCaption.setText("");
        }
        resetJournalPostActionState();
        setJournalPostLoading(false, "");
        showJournalPostError("");
        setJournalPostSentBadgeVisible(false);
        journalCaptureOverlay.setVisibility(View.VISIBLE);
        host.setJournalPreviewUiVisible(true);
        host.updateJournalStatus("Thêm caption rồi bấm Đăng.");
    }

    private void updateJournalEmptyState(int count, @NonNull String emptyText) {
        if (txtJournalEmptyState == null) {
            return;
        }
        boolean showEmpty = count <= 0;
        if (showEmpty && "Chưa có hoạt động nào!".contentEquals(emptyText)) {
            txtJournalEmptyState.setVisibility(View.GONE);
            txtJournalEmptyState.setText("");
            return;
        }
        txtJournalEmptyState.setVisibility(showEmpty ? View.VISIBLE : View.GONE);
        if (showEmpty) {
            txtJournalEmptyState.setText(emptyText);
        }
    }

    private void setJournalPostLoading(boolean loading, @NonNull String statusText) {
        if (journalPostLoading != null) {
            journalPostLoading.setVisibility(loading ? View.VISIBLE : View.GONE);
        }
        if (iconJournalPostSend != null) {
            iconJournalPostSend.setVisibility(loading ? View.GONE : View.VISIBLE);
            iconJournalPostSend.setAlpha(1f);
            iconJournalPostSend.setScaleX(1f);
            iconJournalPostSend.setScaleY(1f);
        }
        if (iconJournalPostSuccess != null) {
            if (loading) {
                iconJournalPostSuccess.animate().cancel();
                iconJournalPostSuccess.setVisibility(View.GONE);
            }
            iconJournalPostSuccess.setAlpha(0f);
            iconJournalPostSuccess.setScaleX(1f);
            iconJournalPostSuccess.setScaleY(1f);
        }
        if (txtJournalUploadProgress != null) {
            txtJournalUploadProgress.setText("");
            txtJournalUploadProgress.setVisibility(View.GONE);
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
        showJournalPostError("Thử lại");
        host.updateJournalStatus("");
        Toast.makeText(activity, "Thử lại", Toast.LENGTH_SHORT).show();
    }

    private void showJournalPostSuccessThenReset() {
        if (journalPostLoading != null) {
            journalPostLoading.setVisibility(View.GONE);
        }
        if (iconJournalPostSend != null) {
            iconJournalPostSend.setVisibility(View.GONE);
        }
        if (iconJournalPostSuccess != null) {
            iconJournalPostSuccess.setVisibility(View.VISIBLE);
            iconJournalPostSuccess.setScaleX(0.82f);
            iconJournalPostSuccess.setScaleY(0.82f);
            iconJournalPostSuccess.setAlpha(0f);
            iconJournalPostSuccess.animate()
                    .alpha(1f)
                    .scaleX(1f)
                    .scaleY(1f)
                    .setDuration(180L)
                    .start();
        }
        setJournalPostSentBadgeVisible(true);
        new Handler(Looper.getMainLooper()).postDelayed(() -> {
            hideJournalCapturePreview(true);
            host.updateJournalStatus("");
        }, 700L);
    }

    private void resetJournalPostActionState() {
        if (iconJournalPostSuccess != null) {
            iconJournalPostSuccess.animate().cancel();
            iconJournalPostSuccess.setVisibility(View.GONE);
            iconJournalPostSuccess.setAlpha(0f);
            iconJournalPostSuccess.setScaleX(1f);
            iconJournalPostSuccess.setScaleY(1f);
        }
        if (iconJournalPostSend != null) {
            iconJournalPostSend.animate().cancel();
            iconJournalPostSend.setVisibility(View.VISIBLE);
            iconJournalPostSend.setAlpha(1f);
            iconJournalPostSend.setScaleX(1f);
            iconJournalPostSend.setScaleY(1f);
        }
        setJournalPostSentBadgeVisible(false);
    }

    private void setJournalPostSentBadgeVisible(boolean visible) {
        if (txtJournalPostSentBadge == null) {
            return;
        }
        txtJournalPostSentBadge.animate().cancel();
        if (!visible) {
            txtJournalPostSentBadge.setVisibility(View.GONE);
            txtJournalPostSentBadge.setAlpha(0f);
            txtJournalPostSentBadge.setTranslationY(0f);
            return;
        }
        txtJournalPostSentBadge.setVisibility(View.VISIBLE);
        txtJournalPostSentBadge.setAlpha(0f);
        txtJournalPostSentBadge.setTranslationY(10f);
        txtJournalPostSentBadge.animate()
                .alpha(1f)
                .translationY(0f)
                .setDuration(180L)
                .start();
    }

    private void resetJournalDownloadState() {
        isJournalDownloadInProgress = false;
        activeDownloadToken = null;
        lastDownloadedToken = null;
    }

    @NonNull
    private static String buildImageToken(@NonNull byte[] imageBytes) {
        return imageBytes.length + ":" + Arrays.hashCode(imageBytes);
    }

    private void updateDownloadButtonState(@Nullable View downloadButton, boolean enabled) {
        if (downloadButton == null) {
            return;
        }
        downloadButton.setEnabled(enabled);
        downloadButton.setAlpha(enabled ? 1f : 0.6f);
    }

    private void animateDownloadButtonTap(@Nullable View downloadButton) {
        if (downloadButton == null) {
            return;
        }
        Drawable background = downloadButton.getBackground();
        if (background != null) {
            background.mutate().setAlpha(220);
        }
        downloadButton.animate().cancel();
        downloadButton.animate()
                .scaleX(0.9f)
                .scaleY(0.9f)
                .setDuration(90L)
                .withEndAction(() -> downloadButton.animate()
                        .scaleX(1f)
                        .scaleY(1f)
                        .setDuration(140L)
                        .start())
                .start();
    }

    private void shareInvite(@NonNull FriendInvite invite) {
        String shareText = "Ket ban voi minh tren CoolCook nhe.\n"
                + "Ma moi: " + invite.getInviteId()
                + "\nVao app > Ban be > dan link/ma moi de ket ban."
                + "\nLink app: " + invite.buildDeepLink()
                + "\nLink web: " + invite.buildWebLink();
        Intent intent = new Intent(Intent.ACTION_SEND);
        intent.setType("text/plain");
        intent.putExtra(Intent.EXTRA_TEXT, shareText);
        activity.startActivity(Intent.createChooser(intent, "Chia se loi moi"));
    }

    private void copyInvite(@NonNull FriendInvite invite) {
        ClipboardManager clipboard = (ClipboardManager) activity.getSystemService(Context.CLIPBOARD_SERVICE);
        if (clipboard == null) {
            Toast.makeText(activity, "Khong copy duoc link. Vui long thu chia se.", Toast.LENGTH_SHORT).show();
            return;
        }
        String inviteText = "Ma moi: " + invite.getInviteId()
                + "\nLink app: " + invite.buildDeepLink()
                + "\nLink web: " + invite.buildWebLink();
        clipboard.setPrimaryClip(ClipData.newPlainText("CoolCook invite", inviteText));
        Toast.makeText(activity, "Da copy ma moi va link.", Toast.LENGTH_SHORT).show();
    }
}

