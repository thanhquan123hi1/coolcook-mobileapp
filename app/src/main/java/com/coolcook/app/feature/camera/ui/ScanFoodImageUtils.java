package com.coolcook.app.feature.camera.ui;

import android.content.ContentResolver;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.ImageFormat;
import android.graphics.Rect;
import android.graphics.YuvImage;
import android.net.Uri;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.camera.core.ImageProxy;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.ByteBuffer;

final class ScanFoodImageUtils {

    private static final int MAX_RECOGNITION_DIMENSION = 1600;

    private ScanFoodImageUtils() {
    }

    @NonNull
    static byte[] readImageBytes(@NonNull ContentResolver resolver, @NonNull Uri uri, int maxBytes)
            throws IOException {
        try (InputStream inputStream = resolver.openInputStream(uri);
             ByteArrayOutputStream outputStream = new ByteArrayOutputStream()) {
            if (inputStream == null) {
                throw new IOException("Cannot open selected image");
            }

            byte[] buffer = new byte[8 * 1024];
            int read;
            int total = 0;
            while ((read = inputStream.read(buffer)) != -1) {
                total += read;
                if (total > maxBytes) {
                    return new byte[maxBytes + 1];
                }
                outputStream.write(buffer, 0, read);
            }
            return outputStream.toByteArray();
        }
    }

    @NonNull
    static byte[] readOptimizedRecognitionBytes(
            @NonNull ContentResolver resolver,
            @NonNull Uri uri,
            int maxBytes) throws IOException {
        BitmapFactory.Options bounds = new BitmapFactory.Options();
        bounds.inJustDecodeBounds = true;
        try (InputStream boundsStream = resolver.openInputStream(uri)) {
            if (boundsStream == null) {
                throw new IOException("Cannot open selected image");
            }
            BitmapFactory.decodeStream(boundsStream, null, bounds);
        }

        BitmapFactory.Options decode = new BitmapFactory.Options();
        decode.inSampleSize = calculateInSampleSize(bounds, MAX_RECOGNITION_DIMENSION);
        decode.inPreferredConfig = Bitmap.Config.ARGB_8888;
        try (InputStream decodeStream = resolver.openInputStream(uri)) {
            if (decodeStream == null) {
                throw new IOException("Cannot open selected image");
            }
            Bitmap bitmap = BitmapFactory.decodeStream(decodeStream, null, decode);
            if (bitmap == null) {
                throw new IOException("Cannot decode selected image");
            }
            return compressBitmap(bitmap, maxBytes);
        }
    }

    @NonNull
    static byte[] optimizeForRecognition(@NonNull byte[] imageBytes, int maxBytes) throws IOException {
        BitmapFactory.Options bounds = new BitmapFactory.Options();
        bounds.inJustDecodeBounds = true;
        BitmapFactory.decodeByteArray(imageBytes, 0, imageBytes.length, bounds);

        BitmapFactory.Options decode = new BitmapFactory.Options();
        decode.inSampleSize = calculateInSampleSize(bounds, MAX_RECOGNITION_DIMENSION);
        decode.inPreferredConfig = Bitmap.Config.ARGB_8888;
        Bitmap bitmap = BitmapFactory.decodeByteArray(imageBytes, 0, imageBytes.length, decode);
        if (bitmap == null) {
            throw new IOException("Cannot decode image bytes");
        }
        return compressBitmap(bitmap, maxBytes);
    }

    @Nullable
    static Bitmap decodePreviewBitmap(@NonNull byte[] imageBytes) {
        try {
            BitmapFactory.Options bounds = new BitmapFactory.Options();
            bounds.inJustDecodeBounds = true;
            BitmapFactory.decodeByteArray(imageBytes, 0, imageBytes.length, bounds);

            BitmapFactory.Options decode = new BitmapFactory.Options();
            decode.inPreferredConfig = Bitmap.Config.ARGB_8888;
            decode.inSampleSize = calculateInSampleSize(bounds, MAX_RECOGNITION_DIMENSION);
            return BitmapFactory.decodeByteArray(imageBytes, 0, imageBytes.length, decode);
        } catch (Exception ignored) {
            return null;
        }
    }

    @Nullable
    static byte[] imageProxyToJpeg(@NonNull ImageProxy imageProxy) {
        ImageProxy.PlaneProxy[] planes = imageProxy.getPlanes();
        if (imageProxy.getFormat() == ImageFormat.JPEG && planes.length > 0) {
            return readPlaneBytes(planes[0]);
        }

        if (imageProxy.getFormat() != ImageFormat.YUV_420_888 || planes.length < 3) {
            return planes.length > 0 ? readPlaneBytes(planes[0]) : null;
        }

        byte[] nv21 = yuv420888ToNv21(imageProxy);
        if (nv21 == null) {
            return null;
        }

        YuvImage yuvImage = new YuvImage(
                nv21,
                ImageFormat.NV21,
                imageProxy.getWidth(),
                imageProxy.getHeight(),
                null);

        ByteArrayOutputStream outputStream = new ByteArrayOutputStream();
        boolean compressed = yuvImage.compressToJpeg(
                new Rect(0, 0, imageProxy.getWidth(), imageProxy.getHeight()),
                88,
                outputStream);

        if (!compressed) {
            return null;
        }

        return outputStream.toByteArray();
    }

    @Nullable
    private static byte[] readPlaneBytes(@NonNull ImageProxy.PlaneProxy plane) {
        ByteBuffer buffer = plane.getBuffer();
        ByteBuffer copy = buffer.duplicate();
        copy.rewind();
        if (!copy.hasRemaining()) {
            return null;
        }

        byte[] bytes = new byte[copy.remaining()];
        copy.get(bytes);
        return bytes;
    }

    @Nullable
    private static byte[] yuv420888ToNv21(@NonNull ImageProxy image) {
        ImageProxy.PlaneProxy[] planes = image.getPlanes();
        ByteBuffer yBuffer = planes[0].getBuffer();
        ByteBuffer uBuffer = planes[1].getBuffer();
        ByteBuffer vBuffer = planes[2].getBuffer();

        int ySize = yBuffer.remaining();
        int uSize = uBuffer.remaining();
        int vSize = vBuffer.remaining();

        byte[] nv21 = new byte[ySize + uSize + vSize];
        yBuffer.get(nv21, 0, ySize);

        int chromaRowStride = planes[2].getRowStride();
        int chromaPixelStride = planes[2].getPixelStride();
        int width = image.getWidth();
        int height = image.getHeight();
        int offset = ySize;

        byte[] vBytes = new byte[vBuffer.remaining()];
        vBuffer.get(vBytes);
        byte[] uBytes = new byte[uBuffer.remaining()];
        uBuffer.get(uBytes);

        int chromaHeight = height / 2;
        int chromaWidth = width / 2;

        for (int row = 0; row < chromaHeight; row++) {
            int rowStart = row * chromaRowStride;
            for (int col = 0; col < chromaWidth; col++) {
                int index = rowStart + col * chromaPixelStride;
                if (index >= vBytes.length || index >= uBytes.length || offset + 1 >= nv21.length) {
                    break;
                }
                nv21[offset++] = vBytes[index];
                nv21[offset++] = uBytes[index];
            }
        }

        return nv21;
    }

    @NonNull
    private static byte[] compressBitmap(@NonNull Bitmap bitmap, int maxBytes) throws IOException {
        Bitmap scaledBitmap = scaleBitmapIfNeeded(bitmap, MAX_RECOGNITION_DIMENSION);
        try {
            for (int quality = 88; quality >= 50; quality -= 8) {
                ByteArrayOutputStream outputStream = new ByteArrayOutputStream();
                scaledBitmap.compress(Bitmap.CompressFormat.JPEG, quality, outputStream);
                byte[] compressed = outputStream.toByteArray();
                if (compressed.length <= maxBytes) {
                    return compressed;
                }
            }

            Bitmap fallback = scaleBitmapIfNeeded(scaledBitmap, 1200);
            ByteArrayOutputStream outputStream = new ByteArrayOutputStream();
            fallback.compress(Bitmap.CompressFormat.JPEG, 70, outputStream);
            byte[] compressed = outputStream.toByteArray();
            if (compressed.length <= maxBytes) {
                return compressed;
            }
            throw new IOException("Image too large after compression");
        } finally {
            if (scaledBitmap != bitmap) {
                scaledBitmap.recycle();
            }
            bitmap.recycle();
        }
    }

    @NonNull
    private static Bitmap scaleBitmapIfNeeded(@NonNull Bitmap bitmap, int maxDimension) {
        int width = bitmap.getWidth();
        int height = bitmap.getHeight();
        int longestSide = Math.max(width, height);
        if (longestSide <= maxDimension) {
            return bitmap;
        }

        float ratio = maxDimension / (float) longestSide;
        int scaledWidth = Math.max(1, Math.round(width * ratio));
        int scaledHeight = Math.max(1, Math.round(height * ratio));
        return Bitmap.createScaledBitmap(bitmap, scaledWidth, scaledHeight, true);
    }

    private static int calculateInSampleSize(@NonNull BitmapFactory.Options bounds, int maxDimension) {
        int width = Math.max(1, bounds.outWidth);
        int height = Math.max(1, bounds.outHeight);
        int inSampleSize = 1;
        while (Math.max(width / inSampleSize, height / inSampleSize) > maxDimension) {
            inSampleSize *= 2;
        }
        return Math.max(1, inSampleSize);
    }
}
