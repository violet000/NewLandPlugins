package com.zijin.plugin.usbcamera;

import android.app.Activity;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Matrix;
import android.graphics.Point;
import android.graphics.Rect;
import android.graphics.Region;
import android.hardware.Camera;
import android.util.Base64;
import android.util.Log;

import com.zijin.camera_lib.hepler.CameraSizeHelper;
import com.zijin.camera_lib.hepler.FaceCaptureHelper;
import com.zijin.camera_lib.hepler.ImageConvertUtil;
import com.zijin.camera_lib.hepler.PictureHelper;

import java.lang.ref.WeakReference;
import java.lang.reflect.Field;
import java.lang.reflect.InvocationHandler;
import java.lang.reflect.Method;
import java.lang.reflect.Proxy;

/**
 * The face library detects and uploads the same cropped bitmap. Keep detection
 * on the original recognition box, and swap the uploaded JPEG for the full
 * preview frame in the same orientation.
 */
final class FaceFullFrame {

    private static final String TAG = "UsbCamera";
    private static final String USB_ACTIVITY = "com.zijin.camera_lib.UsbFaceVerifyActivity";
    private static final int MAX_NV21_BYTES = 8_000_000;

    private static byte[] latestNv21;
    private static int latestWidth;
    private static int latestHeight;
    private static boolean latestLocal;
    private static WeakReference<Activity> latestActivity = new WeakReference<>(null);
    private static volatile String installFailure;

    private FaceFullFrame() {
    }

    static void clear() {
        synchronized (FaceFullFrame.class) {
            latestNv21 = null;
            latestWidth = 0;
            latestHeight = 0;
            latestActivity = new WeakReference<>(null);
        }
    }

    /**
     * @return true once the preview callback is wrapped
     */
    static boolean install(Activity activity) {
        if (activity == null) {
            return false;
        }
        try {
            if (USB_ACTIVITY.equals(activity.getClass().getName())) {
                return installUsb(activity);
            }
            installFailure = null;
            return installLocal(activity);
        } catch (Throwable t) {
            installFailure = String.valueOf(t);
            return false;
        }
    }

    static String installFailure() {
        return installFailure;
    }

    /**
     * @param requestJson face request body, either {@code faceBase64} or {@code image}
     * @return the same JSON with the picture replaced, or null when no full frame is available
     */
    static String replaceUpload(String requestJson) {
        if (requestJson == null) {
            return null;
        }
        String uploaded = jsonString(requestJson, "faceBase64");
        String key = "faceBase64";
        if (uploaded == null) {
            uploaded = jsonString(requestJson, "image");
            key = "image";
        }
        if (uploaded == null) {
            return null;
        }
        String full = fullFrameBase64(uploaded);
        if (full == null || full.isEmpty()) {
            Log.w(TAG, "full frame missing, uploading the detection crop");
            return null;
        }
        return replaceJsonString(requestJson, key, full);
    }

    private static boolean installUsb(Activity activity) throws ReflectiveOperationException {
        Object proxy = readField(activity, "uvcCamera");
        if (proxy == null) {
            return false;
        }
        Field callbackField = findField(proxy.getClass(), "mPreviewCallback");
        if (callbackField == null) {
            return false;
        }
        Object current = callbackField.get(proxy);
        if (current == null) {
            return false;
        }
        if (Proxy.isProxyClass(current.getClass())
                && Proxy.getInvocationHandler(current) instanceof UsbTap) {
            return true;
        }
        Class<?> callbackType = Class.forName("com.lgh.uvccamera.callback.PreviewCallback");
        Object wrapped = Proxy.newProxyInstance(
                callbackType.getClassLoader(),
                new Class<?>[]{callbackType},
                new UsbTap(activity, current));
        proxy.getClass().getMethod("setPreviewCallback", callbackType).invoke(proxy, wrapped);
        Log.i(TAG, "usb preview hooked for full-frame upload");
        return true;
    }

    private static boolean installLocal(Activity activity) throws ReflectiveOperationException {
        Object camera = readField(activity, "camera");
        if (!(camera instanceof Camera)) {
            return false;
        }
        Camera device = (Camera) camera;
        Field callbackField = findField(device.getClass(), "mPreviewCallback");
        if (callbackField == null) {
            return false;
        }
        Object current = callbackField.get(device);
        if (!(current instanceof Camera.PreviewCallback)) {
            return false;
        }
        if (current instanceof LocalTap) {
            return true;
        }
        callbackField.set(device, new LocalTap(activity, (Camera.PreviewCallback) current));
        Log.i(TAG, "camera preview hooked for full-frame upload");
        return true;
    }

    private static void remember(Activity activity, byte[] data, int width, int height, boolean local) {
        if (activity == null || data == null || width < 2 || height < 2) {
            return;
        }
        int bytes = width * height * 3 / 2;
        if (bytes <= 0 || bytes > MAX_NV21_BYTES || data.length < bytes) {
            return;
        }
        synchronized (FaceFullFrame.class) {
            if (latestNv21 == null || latestNv21.length != bytes) {
                latestNv21 = new byte[bytes];
            }
            System.arraycopy(data, 0, latestNv21, 0, bytes);
            latestWidth = width;
            latestHeight = height;
            latestLocal = local;
            latestActivity = new WeakReference<>(activity);
        }
    }

    private static String fullFrameBase64(String uploadedBase64) {
        byte[] nv21;
        int width;
        int height;
        boolean local;
        Activity activity;
        synchronized (FaceFullFrame.class) {
            if (latestNv21 == null) {
                return null;
            }
            nv21 = latestNv21.clone();
            width = latestWidth;
            height = latestHeight;
            local = latestLocal;
            activity = latestActivity.get();
        }
        Bitmap full = ImageConvertUtil.nv21ToBitmap(nv21, width, height);
        if (full == null) {
            return null;
        }
        try {
            if (local && activity != null) {
                full = orientLocalPreview(activity, full);
            }
            Bitmap uploaded = decodeUploaded(uploadedBase64);
            int orientation = 0;
            if (uploaded != null) {
                try {
                    orientation = matchOrientation(full, detectionBounds(activity, local, full), uploaded);
                } finally {
                    if (!uploaded.isRecycled()) {
                        uploaded.recycle();
                    }
                }
            }
            Bitmap upright = applyOrientation(full, orientation);
            if (upright != full) {
                if (!full.isRecycled()) {
                    full.recycle();
                }
                full = upright;
            }
            Log.i(TAG, "upload full frame " + full.getWidth() + "x" + full.getHeight());
            String encoded = PictureHelper.processPicture(full, PictureHelper.JPEG);
            if (!full.isRecycled()) {
                full.recycle();
            }
            return encoded == null || encoded.isEmpty() ? null : encoded;
        } catch (Throwable t) {
            Log.w(TAG, "encode full frame failed", t);
            if (!full.isRecycled()) {
                full.recycle();
            }
            return null;
        }
    }

    private static Bitmap orientLocalPreview(Activity activity, Bitmap bitmap) {
        try {
            Object info = readField(activity, "cameraInfo");
            if (!(info instanceof Camera.CameraInfo)) {
                return bitmap;
            }
            int display = CameraSizeHelper.getCameraDisplayOrientation(activity, (Camera.CameraInfo) info);
            return FaceCaptureHelper.matchLandscapePreview(bitmap, (Camera.CameraInfo) info, display);
        } catch (Throwable t) {
            Log.w(TAG, "orient local preview failed", t);
            return bitmap;
        }
    }

    private static Rect detectionBounds(Activity activity, boolean local, Bitmap full) {
        Rect entire = new Rect(0, 0, full.getWidth(), full.getHeight());
        if (activity == null) {
            return entire;
        }
        try {
            if (local) {
                Object mask = readField(activity, "faceRegionMask");
                if (mask == null) {
                    return entire;
                }
                Object region = mask.getClass().getMethod("getFaceMaskRegion").invoke(mask);
                if (!(region instanceof Region)) {
                    return entire;
                }
                return new Rect(((Region) region).getBounds());
            }
            Object region = readField(activity, "faceRegion");
            if (region instanceof Region) {
                return new Rect(((Region) region).getBounds());
            }
        } catch (Throwable t) {
            Log.w(TAG, "read detection region failed", t);
        }
        return entire;
    }

    private static int matchOrientation(Bitmap full, Rect bounds, Bitmap uploaded) {
        Bitmap crop = crop(full, bounds);
        if (crop == null) {
            return 0;
        }
        Bitmap flipped = null;
        Bitmap[] variants = new Bitmap[4];
        try {
            flipped = flipHorizontal(crop);
            variants[0] = crop;
            variants[1] = rotate180(crop);
            variants[2] = flipped;
            variants[3] = rotate180(flipped);
            int best = 0;
            long bestScore = Long.MAX_VALUE;
            for (int i = 0; i < variants.length; i++) {
                long score = sampleDifference(variants[i], uploaded);
                if (score < bestScore) {
                    bestScore = score;
                    best = i;
                }
            }
            return best;
        } finally {
            recycleDistinct(variants, full);
        }
    }

    private static Bitmap crop(Bitmap source, Rect bounds) {
        if (source == null || bounds == null) {
            return null;
        }
        int left = Math.max(0, Math.min(bounds.left, source.getWidth() - 2));
        int top = Math.max(0, Math.min(bounds.top, source.getHeight() - 2));
        int width = Math.min(bounds.width(), source.getWidth() - left) & ~1;
        int height = Math.min(bounds.height(), source.getHeight() - top) & ~1;
        if (width < 2 || height < 2) {
            return null;
        }
        return Bitmap.createBitmap(source, left, top, width, height);
    }

    private static Bitmap applyOrientation(Bitmap source, int orientation) {
        if (orientation == 1) {
            return rotate180(source);
        }
        if (orientation == 2) {
            return flipHorizontal(source);
        }
        if (orientation == 3) {
            Bitmap flipped = flipHorizontal(source);
            Bitmap rotated = rotate180(flipped);
            if (rotated != flipped && !flipped.isRecycled()) {
                flipped.recycle();
            }
            return rotated;
        }
        return source;
    }

    private static Bitmap rotate180(Bitmap source) {
        Matrix matrix = new Matrix();
        matrix.postRotate(180f);
        return Bitmap.createBitmap(source, 0, 0, source.getWidth(), source.getHeight(), matrix, true);
    }

    private static Bitmap flipHorizontal(Bitmap source) {
        Matrix matrix = new Matrix();
        matrix.postScale(-1f, 1f);
        return Bitmap.createBitmap(source, 0, 0, source.getWidth(), source.getHeight(), matrix, true);
    }

    private static long sampleDifference(Bitmap candidate, Bitmap uploaded) {
        if (candidate == null || uploaded == null) {
            return Long.MAX_VALUE;
        }
        long sum = 0L;
        for (int y = 0; y < 8; y++) {
            int cy = y * (candidate.getHeight() - 1) / 7;
            int uy = y * (uploaded.getHeight() - 1) / 7;
            for (int x = 0; x < 8; x++) {
                int cx = x * (candidate.getWidth() - 1) / 7;
                int ux = x * (uploaded.getWidth() - 1) / 7;
                sum += colorDelta(candidate.getPixel(cx, cy), uploaded.getPixel(ux, uy));
            }
        }
        return sum;
    }

    private static int colorDelta(int left, int right) {
        int dr = ((left >> 16) & 0xff) - ((right >> 16) & 0xff);
        int dg = ((left >> 8) & 0xff) - ((right >> 8) & 0xff);
        int db = (left & 0xff) - (right & 0xff);
        return dr * dr + dg * dg + db * db;
    }

    private static Bitmap decodeUploaded(String base64) {
        try {
            if (base64.length() > 2_000_000) {
                return null;
            }
            byte[] jpeg = Base64.decode(base64, Base64.DEFAULT);
            return BitmapFactory.decodeByteArray(jpeg, 0, jpeg.length);
        } catch (Throwable t) {
            Log.w(TAG, "decode detection crop failed", t);
            return null;
        }
    }

    private static String jsonString(String json, String key) {
        String needle = "\"" + key + "\"";
        int keyAt = json.indexOf(needle);
        if (keyAt < 0) {
            return null;
        }
        int colon = json.indexOf(':', keyAt + needle.length());
        if (colon < 0) {
            return null;
        }
        int start = json.indexOf('"', colon + 1);
        if (start < 0) {
            return null;
        }
        int end = json.indexOf('"', start + 1);
        if (end <= start) {
            return null;
        }
        return json.substring(start + 1, end);
    }

    private static String replaceJsonString(String json, String key, String value) {
        String needle = "\"" + key + "\"";
        int keyAt = json.indexOf(needle);
        if (keyAt < 0) {
            return null;
        }
        int colon = json.indexOf(':', keyAt + needle.length());
        int start = colon < 0 ? -1 : json.indexOf('"', colon + 1);
        int end = start < 0 ? -1 : json.indexOf('"', start + 1);
        if (start < 0 || end <= start) {
            return null;
        }
        return json.substring(0, start + 1) + value + json.substring(end);
    }

    private static void recycleDistinct(Bitmap[] bitmaps, Bitmap keep) {
        for (Bitmap bitmap : bitmaps) {
            if (bitmap != null && bitmap != keep && !bitmap.isRecycled()) {
                bitmap.recycle();
            }
        }
    }

    private static Object readField(Activity activity, String name) throws ReflectiveOperationException {
        Field field = findField(activity.getClass(), name);
        if (field == null) {
            throw new NoSuchFieldException(name);
        }
        return field.get(activity);
    }

    private static Field findField(Class<?> type, String name) {
        while (type != null) {
            try {
                Field field = type.getDeclaredField(name);
                field.setAccessible(true);
                return field;
            } catch (NoSuchFieldException e) {
                type = type.getSuperclass();
            }
        }
        return null;
    }

    private static int[] usbSize(Activity activity) {
        try {
            Object preview = readField(activity, "previewSize");
            if (!(preview instanceof Point)) {
                return null;
            }
            Point size = (Point) preview;
            if (size.x < 2 || size.y < 2) {
                return null;
            }
            return new int[]{size.x, size.y};
        } catch (ReflectiveOperationException e) {
            return null;
        }
    }

    private static int[] localSize(Activity activity) {
        try {
            Object preview = readField(activity, "previewSize");
            if (preview == null) {
                return null;
            }
            int width = (Integer) preview.getClass().getMethod("getLonger").invoke(preview);
            int height = (Integer) preview.getClass().getMethod("getShorter").invoke(preview);
            if (width < 2 || height < 2) {
                return null;
            }
            return new int[]{width, height};
        } catch (ReflectiveOperationException | ClassCastException e) {
            return null;
        }
    }

    private static final class UsbTap implements InvocationHandler {
        private final Activity activity;
        private final Object delegate;

        private UsbTap(Activity activity, Object delegate) {
            this.activity = activity;
            this.delegate = delegate;
        }

        @Override
        public Object invoke(Object proxy, Method method, Object[] args) throws Throwable {
            if ("onPreviewFrame".equals(method.getName())
                    && args != null
                    && args.length == 1
                    && args[0] instanceof byte[]) {
                int[] size = usbSize(activity);
                if (size != null) {
                    remember(activity, (byte[]) args[0], size[0], size[1], false);
                }
            }
            try {
                return method.invoke(delegate, args);
            } catch (java.lang.reflect.InvocationTargetException e) {
                throw e.getCause() == null ? e : e.getCause();
            }
        }
    }

    private static final class LocalTap implements Camera.PreviewCallback {
        private final Activity activity;
        private final Camera.PreviewCallback delegate;

        private LocalTap(Activity activity, Camera.PreviewCallback delegate) {
            this.activity = activity;
            this.delegate = delegate;
        }

        @Override
        public void onPreviewFrame(byte[] data, Camera camera) {
            int[] size = localSize(activity);
            if (size != null) {
                remember(activity, data, size[0], size[1], true);
            }
            delegate.onPreviewFrame(data, camera);
        }
    }
}
