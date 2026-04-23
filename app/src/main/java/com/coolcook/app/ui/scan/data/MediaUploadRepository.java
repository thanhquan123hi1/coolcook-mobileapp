package com.coolcook.app.ui.scan.data;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.os.Handler;
import android.os.Looper;
import android.text.TextUtils;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.cloudinary.android.MediaManager;
import com.cloudinary.android.UploadRequest;
import com.cloudinary.android.callback.ErrorInfo;
import com.cloudinary.android.callback.UploadCallback;
import com.coolcook.app.BuildConfig;
import com.coolcook.app.ui.scan.model.MediaUploadResult;

import java.io.ByteArrayOutputStream;
import java.util.HashMap;
import java.util.Map;

public class MediaUploadRepository {

    public interface UploadCallbackListener {
        void onPreparing();

        void onProgress(int progress);

        void onSuccess(@NonNull MediaUploadResult result);

        void onError(@NonNull String message);
    }

    private static final int JOURNAL_UPLOAD_MAX_DIMENSION_PX = 1440;
    private static final int JOURNAL_UPLOAD_QUALITY = 84;

    private final Context appContext;
    private final Handler mainHandler = new Handler(Looper.getMainLooper());

    public MediaUploadRepository(@NonNull Context context) {
        this.appContext = context.getApplicationContext();
    }

    public void uploadJournalImage(@NonNull byte[] sourceBytes, @NonNull UploadCallbackListener callback) {
        if (sourceBytes.length == 0) {
            callback.onError("Không có ảnh để đăng.");
            return;
        }

        callback.onPreparing();
        new Thread(() -> {
            byte[] preparedBytes = prepareJournalImageForUpload(sourceBytes);
            if (preparedBytes == null || preparedBytes.length == 0) {
                postError(callback, "Không tối ưu được ảnh để đăng.");
                return;
            }
            ImageSize localSize = resolveImageSize(preparedBytes);
            mainHandler.post(() -> uploadPreparedBytes(preparedBytes, localSize, callback));
        }).start();
    }

    private void uploadPreparedBytes(
            @NonNull byte[] preparedBytes,
            @NonNull ImageSize localSize,
            @NonNull UploadCallbackListener callback) {
        if (!ensureCloudinaryConfigured()) {
            callback.onError("Thiếu cấu hình Cloudinary để đăng ảnh.");
            return;
        }

        String uploadPreset = BuildConfig.CLOUDINARY_UPLOAD_PRESET.trim();
        UploadRequest uploadRequest = MediaManager.get().upload(preparedBytes)
                .option("resource_type", "image");

        if (!TextUtils.isEmpty(uploadPreset)) {
            uploadRequest.option("upload_preset", uploadPreset);
        } else {
            // MVP fallback only. A signed upload should be delegated to backend code in production.
            uploadRequest.option("folder", "coolcook/journal");
        }

        uploadRequest.callback(new UploadCallback() {
            @Override
            public void onStart(String requestId) {
                mainHandler.post(() -> callback.onProgress(0));
            }

            @Override
            public void onProgress(String requestId, long bytes, long totalBytes) {
                int progress = 0;
                if (totalBytes > 0L) {
                    progress = (int) ((bytes * 100L) / totalBytes);
                    progress = Math.max(0, Math.min(progress, 100));
                }
                int safeProgress = progress;
                mainHandler.post(() -> callback.onProgress(safeProgress));
            }

            @Override
            public void onSuccess(String requestId, Map resultData) {
                MediaUploadResult result = buildResult(resultData, localSize);
                mainHandler.post(() -> {
                    if (TextUtils.isEmpty(result.getImageUrl())) {
                        callback.onError("Cloudinary không trả về URL ảnh hợp lệ.");
                        return;
                    }
                    callback.onSuccess(result);
                });
            }

            @Override
            public void onError(String requestId, ErrorInfo error) {
                String message = error == null || TextUtils.isEmpty(error.getDescription())
                        ? "Upload ảnh thất bại."
                        : error.getDescription();
                postError(callback, message);
            }

            @Override
            public void onReschedule(String requestId, ErrorInfo error) {
                mainHandler.post(() -> callback.onProgress(0));
            }
        }).dispatch();
    }

    private boolean ensureCloudinaryConfigured() {
        String cloudName = BuildConfig.CLOUDINARY_CLOUD_NAME.trim();
        String apiKey = BuildConfig.CLOUDINARY_API_KEY.trim();
        String apiSecret = BuildConfig.CLOUDINARY_API_SECRET.trim();
        String uploadPreset = BuildConfig.CLOUDINARY_UPLOAD_PRESET.trim();

        boolean hasSignedCredentials = !TextUtils.isEmpty(apiKey) && !TextUtils.isEmpty(apiSecret);
        boolean hasUnsignedPreset = !TextUtils.isEmpty(uploadPreset);
        if (TextUtils.isEmpty(cloudName) || (!hasSignedCredentials && !hasUnsignedPreset)) {
            return false;
        }

        try {
            MediaManager.get();
            return true;
        } catch (IllegalStateException ignored) {
            HashMap<String, Object> config = new HashMap<>();
            config.put("cloud_name", cloudName);
            config.put("secure", true);
            if (!TextUtils.isEmpty(apiKey)) {
                config.put("api_key", apiKey);
            }
            if (!TextUtils.isEmpty(apiSecret)) {
                // Avoid shipping API secret in production builds; use a signed-upload backend instead.
                config.put("api_secret", apiSecret);
            }
            MediaManager.init(appContext, config);
            return true;
        }
    }

    @NonNull
    private MediaUploadResult buildResult(@Nullable Map resultData, @NonNull ImageSize fallbackSize) {
        if (resultData == null) {
            return new MediaUploadResult("", "", "", fallbackSize.width, fallbackSize.height, "");
        }

        String secureUrl = stringValue(resultData.get("secure_url"));
        String publicId = stringValue(resultData.get("public_id"));
        String createdAt = stringValue(resultData.get("created_at"));
        int width = intValue(resultData.get("width"), fallbackSize.width);
        int height = intValue(resultData.get("height"), fallbackSize.height);
        String thumbUrl = buildCloudinaryThumbUrl(secureUrl, 720, 960);

        return new MediaUploadResult(secureUrl, thumbUrl, publicId, width, height, createdAt);
    }

    @Nullable
    private byte[] prepareJournalImageForUpload(@NonNull byte[] sourceBytes) {
        try {
            BitmapFactory.Options bounds = new BitmapFactory.Options();
            bounds.inJustDecodeBounds = true;
            BitmapFactory.decodeByteArray(sourceBytes, 0, sourceBytes.length, bounds);

            int sourceWidth = Math.max(1, bounds.outWidth);
            int sourceHeight = Math.max(1, bounds.outHeight);
            int maxSide = Math.max(sourceWidth, sourceHeight);

            int inSampleSize = 1;
            while (maxSide / inSampleSize > JOURNAL_UPLOAD_MAX_DIMENSION_PX * 2) {
                inSampleSize *= 2;
            }

            BitmapFactory.Options decodeOptions = new BitmapFactory.Options();
            decodeOptions.inPreferredConfig = Bitmap.Config.ARGB_8888;
            decodeOptions.inSampleSize = Math.max(1, inSampleSize);

            Bitmap decoded = BitmapFactory.decodeByteArray(sourceBytes, 0, sourceBytes.length, decodeOptions);
            if (decoded == null) {
                return sourceBytes;
            }

            Bitmap scaled = decoded;
            int currentWidth = decoded.getWidth();
            int currentHeight = decoded.getHeight();
            int currentMax = Math.max(currentWidth, currentHeight);

            if (currentMax > JOURNAL_UPLOAD_MAX_DIMENSION_PX) {
                float ratio = JOURNAL_UPLOAD_MAX_DIMENSION_PX / (float) currentMax;
                int targetWidth = Math.max(1, Math.round(currentWidth * ratio));
                int targetHeight = Math.max(1, Math.round(currentHeight * ratio));
                scaled = Bitmap.createScaledBitmap(decoded, targetWidth, targetHeight, true);
                if (scaled != decoded) {
                    decoded.recycle();
                }
            }

            ByteArrayOutputStream outputStream = new ByteArrayOutputStream();
            boolean compressed = scaled.compress(Bitmap.CompressFormat.JPEG, JOURNAL_UPLOAD_QUALITY, outputStream);
            scaled.recycle();
            if (!compressed) {
                return null;
            }
            return outputStream.toByteArray();
        } catch (Exception ignored) {
            return null;
        }
    }

    @NonNull
    private ImageSize resolveImageSize(@NonNull byte[] bytes) {
        BitmapFactory.Options options = new BitmapFactory.Options();
        options.inJustDecodeBounds = true;
        BitmapFactory.decodeByteArray(bytes, 0, bytes.length, options);
        return new ImageSize(Math.max(0, options.outWidth), Math.max(0, options.outHeight));
    }

    @NonNull
    private String buildCloudinaryThumbUrl(@NonNull String rawUrl, int targetWidth, int targetHeight) {
        if (TextUtils.isEmpty(rawUrl)) {
            return "";
        }
        String marker = "/image/upload/";
        int markerIndex = rawUrl.indexOf(marker);
        if (markerIndex < 0) {
            return rawUrl;
        }
        String prefix = rawUrl.substring(0, markerIndex + marker.length());
        String suffix = rawUrl.substring(markerIndex + marker.length());
        String transform = "c_fill,g_auto,w_" + targetWidth + ",h_" + targetHeight + ",q_auto,f_auto/";
        return prefix + transform + suffix;
    }

    private void postError(@NonNull UploadCallbackListener callback, @NonNull String message) {
        mainHandler.post(() -> callback.onError(message));
    }

    @NonNull
    private String stringValue(@Nullable Object raw) {
        return raw == null ? "" : String.valueOf(raw);
    }

    private int intValue(@Nullable Object raw, int fallback) {
        if (raw instanceof Number) {
            return ((Number) raw).intValue();
        }
        if (raw == null) {
            return fallback;
        }
        try {
            return Integer.parseInt(String.valueOf(raw));
        } catch (NumberFormatException ignored) {
            return fallback;
        }
    }

    private static class ImageSize {
        final int width;
        final int height;

        ImageSize(int width, int height) {
            this.width = width;
            this.height = height;
        }
    }
}
