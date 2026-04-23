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

    @Nullable
    static Bitmap decodePreviewBitmap(@NonNull byte[] imageBytes) {
        try {
            BitmapFactory.Options bounds = new BitmapFactory.Options();
            bounds.inJustDecodeBounds = true;
            BitmapFactory.decodeByteArray(imageBytes, 0, imageBytes.length, bounds);

            int maxSide = Math.max(bounds.outWidth, bounds.outHeight);
            int inSampleSize = 1;
            while (maxSide / inSampleSize > 1600) {
                inSampleSize *= 2;
            }

            BitmapFactory.Options decode = new BitmapFactory.Options();
            decode.inPreferredConfig = Bitmap.Config.ARGB_8888;
            decode.inSampleSize = Math.max(1, inSampleSize);
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
}
