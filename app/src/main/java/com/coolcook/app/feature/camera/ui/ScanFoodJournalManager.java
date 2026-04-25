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
        updateJournalEmptyState(0, "ChÆ°a cĂ³ hoáº¡t Ä‘á»™ng nĂ o!");
    }

    void startRealtimeListeners() {
        stopRealtimeListeners();

        FirebaseUser user = FirebaseAuth.getInstance().getCurrentUser();
        if (user == null) {
            if (journalFeedAdapter != null) {
                journalFeedAdapter.submitItems(new ArrayList<>());
            }
            updateJournalEmptyState(0, "ÄÄƒng nháº­p Ä‘á»ƒ xem feed cá»§a báº¡n bĂ¨.");
            host.updateJournalStatus("Vui lĂ²ng Ä‘Äƒng nháº­p Ä‘á»ƒ Ä‘Äƒng moment.");
            if (txtJournalFriendCount != null) {
                txtJournalFriendCount.setText("Táº¥t cáº£ báº¡n bĂ¨");
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
                        updateJournalEmptyState(items.size(), "ChÆ°a cĂ³ hoáº¡t Ä‘á»™ng nĂ o!");
                    }

                    @Override
                    public void onError(@NonNull Exception error) {
                        updateJournalEmptyState(0, "KhĂ´ng táº£i Ä‘Æ°á»£c feed. Vui lĂ²ng thá»­ láº¡i.");
                        host.updateJournalStatus("KhĂ´ng táº£i Ä‘Æ°á»£c feed.");
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
                            txtJournalFriendCount.setText("Táº¥t cáº£ báº¡n bĂ¨");
                        } else {
                            txtJournalFriendCount.setText(profile.friendCount + " Báº¡n bĂ¨");
                        }
                    }

                    @Override
                    public void onError(@NonNull Exception error) {
                        if (txtJournalFriendCount != null) {
                            txtJournalFriendCount.setText("Báº¡n bĂ¨");
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
        host.updateJournalStatus("Sáºµn sĂ ng chá»¥p moment má»›i.");
    }

    void publishPendingJournalMoment(boolean isUsingFrontCamera) {
        if (pendingJournalImageBytes == null || pendingJournalImageBytes.length == 0) {
            showJournalPostError("KhĂ´ng cĂ³ áº£nh Ä‘á»ƒ Ä‘Äƒng.");
            return;
        }

        FirebaseUser user = FirebaseAuth.getInstance().getCurrentUser();
        if (user == null) {
            Toast.makeText(activity, "Vui lĂ²ng Ä‘Äƒng nháº­p Ä‘á»ƒ Ä‘Äƒng nháº­t kĂ½", Toast.LENGTH_SHORT).show();
            activity.startActivity(AuthActivity.createIntent(activity, AuthActivity.MODE_LOGIN));
            return;
        }

        String caption = edtJournalCaption == null ? "" : String.valueOf(edtJournalCaption.getText()).trim();
        String cameraFacing = "camera".equals(pendingJournalSourceLabel)
                ? (isUsingFrontCamera ? "front" : "back")
                : "none";

        isJournalSaveInProgress = true;
        host.setProcessingUiEnabled(false);
        setJournalPostLoading(true, "Äang tá»‘i Æ°u áº£nh...");
        showJournalPostError("");
        host.updateJournalStatus("Äang Ä‘Äƒng moment...");

        mediaUploadRepository.uploadJournalImage(pendingJournalImageBytes,
                new MediaUploadRepository.UploadCallbackListener() {
                    @Override
                    public void onPreparing() {
                        activity.runOnUiThread(() -> setJournalPostLoading(true, "Äang tá»‘i Æ°u áº£nh..."));
                    }

                    @Override
                    public void onProgress(int progress) {
                        activity.runOnUiThread(() -> setJournalPostLoading(
                                true,
                                progress <= 0
                                        ? "Äang táº£i áº£nh lĂªn Cloudinary..."
                                        : "Äang táº£i áº£nh lĂªn Cloudinary... " + progress + "%"));
                    }

                    @Override
                    public void onSuccess(@NonNull MediaUploadResult result) {
                        activity.runOnUiThread(() -> setJournalPostLoading(true, "Äang cáº­p nháº­t Firestore..."));
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
                                                        "ChÆ°a cĂ³ hoáº¡t Ä‘á»™ng nĂ o!");
                                            }
                                            hideJournalCapturePreview(true);
                                            host.updateJournalStatus("ÄĂ£ Ä‘Äƒng moment má»›i.");
                                            Toast.makeText(
                                                    activity,
                                                    "ÄĂ£ Ä‘Äƒng moment thĂ nh cĂ´ng",
                                                    Toast.LENGTH_SHORT).show();
                                        });
                                    }

                                    @Override
                                    public void onError(@NonNull Exception error) {
                                        activity.runOnUiThread(() -> finishJournalSaveWithError(
                                                "LÆ°u Firestore tháº¥t báº¡i. Vui lĂ²ng thá»­ láº¡i."));
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
            txtTitle.setText(txtJournalFriendCount == null ? "Báº¡n bĂ¨" : txtJournalFriendCount.getText());
        }
        if (txtSubtitle != null && user == null) {
            txtSubtitle.setText("ÄÄƒng nháº­p Ä‘á»ƒ táº¡o link má»i báº¡n vĂ o journal feed.");
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
            host.updateJournalStatus("KhĂ´ng má»Ÿ Ä‘Æ°á»£c áº£nh vá»«a chá»¥p.");
            Toast.makeText(activity, "KhĂ´ng hiá»ƒn thá»‹ Ä‘Æ°á»£c áº£nh", Toast.LENGTH_SHORT).show();
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
        host.updateJournalStatus("ThĂªm caption rá»“i báº¥m ÄÄƒng.");
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
        String shareText = "Káº¿t báº¡n vá»›i mĂ¬nh trĂªn CoolCook nhĂ©.\n"
                + invite.buildDeepLink()
                + "\nNáº¿u app link web Ä‘Ă£ Ä‘Æ°á»£c cáº¥u hĂ¬nh trĂªn domain, báº¡n cÅ©ng cĂ³ thá»ƒ thá»­:\n"
                + invite.buildWebLink();
        Intent intent = new Intent(Intent.ACTION_SEND);
        intent.setType("text/plain");
        intent.putExtra(Intent.EXTRA_TEXT, shareText);
        activity.startActivity(Intent.createChooser(intent, "Chia sáº» lá»i má»i"));
    }
}
