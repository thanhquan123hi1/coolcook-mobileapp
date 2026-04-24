package com.coolcook.app.feature.camera.ui;

import android.graphics.Bitmap;
import android.text.TextUtils;
import android.view.View;
import android.widget.EditText;
import android.widget.ImageView;
import android.widget.ProgressBar;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.coolcook.app.feature.auth.ui.AuthActivity;
import com.coolcook.app.core.media.MediaUploadRepository;
import com.coolcook.app.core.media.MediaUploadResult;
import com.coolcook.app.feature.journal.data.JournalRepository;
import com.coolcook.app.feature.journal.model.JournalEntry;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.FirebaseUser;

import java.time.LocalDate;
import java.time.ZoneId;
import java.util.Date;

final class ScanFoodJournalManager {

    interface Host {
        void setProcessingUiEnabled(boolean enabled);

        void updateJournalStatus(@NonNull String status);
    }

    private final ScanFoodActivity activity;
    private final Host host;
    private final TextView txtJournalUploadProgress;
    private final TextView txtJournalPostError;
    private final EditText edtJournalCaption;
    private final ImageView imgJournalCapturedPreview;
    private final View journalCaptureOverlay;
    private final View btnJournalPostCancel;
    private final View btnJournalPostPublish;
    private final ProgressBar journalPostLoading;
    private final MediaUploadRepository mediaUploadRepository;
    private final JournalRepository journalRepository;

    private byte[] pendingJournalImageBytes;
    private boolean isJournalSaveInProgress;

    ScanFoodJournalManager(
            @NonNull ScanFoodActivity activity,
            @NonNull Host host,
            @Nullable TextView txtJournalUploadProgress,
            @Nullable TextView txtJournalPostError,
            @Nullable EditText edtJournalCaption,
            @Nullable ImageView imgJournalCapturedPreview,
            @Nullable View journalCaptureOverlay,
            @Nullable View btnJournalPostCancel,
            @Nullable View btnJournalPostPublish,
            @Nullable ProgressBar journalPostLoading,
            @NonNull MediaUploadRepository mediaUploadRepository,
            @NonNull JournalRepository journalRepository) {
        this.activity = activity;
        this.host = host;
        this.txtJournalUploadProgress = txtJournalUploadProgress;
        this.txtJournalPostError = txtJournalPostError;
        this.edtJournalCaption = edtJournalCaption;
        this.imgJournalCapturedPreview = imgJournalCapturedPreview;
        this.journalCaptureOverlay = journalCaptureOverlay;
        this.btnJournalPostCancel = btnJournalPostCancel;
        this.btnJournalPostPublish = btnJournalPostPublish;
        this.journalPostLoading = journalPostLoading;
        this.mediaUploadRepository = mediaUploadRepository;
        this.journalRepository = journalRepository;
    }

    void processImageBytesForJournal(@NonNull byte[] imageBytes, @NonNull String sourceLabel, boolean busy) {
        if (busy) {
            return;
        }
        showJournalCapturePreview(imageBytes);
    }

    boolean isCapturePreviewVisible() {
        return journalCaptureOverlay != null && journalCaptureOverlay.getVisibility() == View.VISIBLE;
    }

    void hideJournalCapturePreview(boolean clearPendingState) {
        if (journalCaptureOverlay == null) {
            return;
        }
        journalCaptureOverlay.setVisibility(View.GONE);
        showJournalPostError("");
        setJournalPostLoading(false, "");
        if (clearPendingState) {
            pendingJournalImageBytes = null;
        }
        host.updateJournalStatus("San sang luu anh vao nhat ky.");
    }

    void publishPendingJournalMoment(boolean isUsingFrontCamera) {
        if (pendingJournalImageBytes == null || pendingJournalImageBytes.length == 0) {
            showJournalPostError("Khong co anh de luu.");
            return;
        }

        FirebaseUser user = FirebaseAuth.getInstance().getCurrentUser();
        if (user == null) {
            Toast.makeText(activity, "Vui long dang nhap de luu nhat ky", Toast.LENGTH_SHORT).show();
            activity.startActivity(AuthActivity.createIntent(activity, AuthActivity.MODE_LOGIN));
            return;
        }

        final String finalCaption = normalizeCaption(
                edtJournalCaption == null ? "" : String.valueOf(edtJournalCaption.getText()));

        isJournalSaveInProgress = true;
        host.setProcessingUiEnabled(false);
        setJournalPostLoading(true, "Dang toi uu anh...");
        showJournalPostError("");
        host.updateJournalStatus("Dang luu nhat ky...");

        mediaUploadRepository.uploadJournalImage(pendingJournalImageBytes,
                new MediaUploadRepository.UploadCallbackListener() {
                    @Override
                    public void onPreparing() {
                        activity.runOnUiThread(() -> setJournalPostLoading(true, "Dang toi uu anh..."));
                    }

                    @Override
                    public void onProgress(int progress) {
                        activity.runOnUiThread(() -> setJournalPostLoading(
                                true,
                                progress <= 0
                                        ? "Dang tai anh len Cloudinary..."
                                        : "Dang tai anh len Cloudinary... " + progress + "%"));
                    }

                    @Override
                    public void onSuccess(@NonNull MediaUploadResult result) {
                        activity.runOnUiThread(() -> setJournalPostLoading(true, "Dang luu Firestore..."));

                        Date capturedAt = new Date();
                        LocalDate date = capturedAt.toInstant().atZone(ZoneId.systemDefault()).toLocalDate();
                        JournalEntry entry = new JournalEntry(
                                "",
                                user.getUid(),
                                date,
                                result.getImageUrl(),
                                result.getThumbUrl(),
                                capturedAt,
                                finalCaption,
                                "other");

                        journalRepository.saveEntry(user.getUid(), entry, error -> activity.runOnUiThread(() -> {
                            if (error != null) {
                                finishJournalSaveWithError("Luu Firestore that bai. Vui long thu lai.");
                                return;
                            }
                            isJournalSaveInProgress = false;
                            host.setProcessingUiEnabled(true);
                            hideJournalCapturePreview(true);
                            host.updateJournalStatus("Da luu anh vao nhat ky.");
                            Toast.makeText(activity, "Da luu nhat ky", Toast.LENGTH_SHORT).show();
                        }));
                    }

                    @Override
                    public void onError(@NonNull String message) {
                        activity.runOnUiThread(() -> finishJournalSaveWithError(message));
                    }
                });
    }

    boolean isJournalSaveInProgress() {
        return isJournalSaveInProgress;
    }

    void showJournalCapturePreview(@NonNull byte[] imageBytes) {
        if (imgJournalCapturedPreview == null || journalCaptureOverlay == null) {
            return;
        }

        Bitmap bitmap = ScanFoodImageUtils.decodePreviewBitmap(imageBytes);
        if (bitmap == null) {
            host.updateJournalStatus("Khong mo duoc anh vua chup.");
            Toast.makeText(activity, "Khong hien thi duoc anh", Toast.LENGTH_SHORT).show();
            return;
        }

        pendingJournalImageBytes = imageBytes;
        imgJournalCapturedPreview.setImageBitmap(bitmap);
        if (edtJournalCaption != null) {
            edtJournalCaption.setText("");
        }
        setJournalPostLoading(false, "");
        showJournalPostError("");
        journalCaptureOverlay.setVisibility(View.VISIBLE);
        host.updateJournalStatus("Them caption roi bam Luu.");
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

    @NonNull
    private String normalizeCaption(@Nullable String caption) {
        if (caption == null) {
            return "";
        }
        String trimmed = caption.trim();
        if (trimmed.length() <= 180) {
            return trimmed;
        }
        return trimmed.substring(0, 180).trim();
    }
}
