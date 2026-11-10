package com.zijin.plugin.usbcamera;

import android.app.Activity;
import android.app.Application;
import android.graphics.Rect;
import android.graphics.Region;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.util.Log;

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * Leaves face detection on the recognition box, extended above and below so the
 * uploaded picture still shows the chin after the library turns it upright.
 * USB cameras that support zoom are pulled to the widest setting.
 */
final class FaceFramingHelper {

    private static final String TAG = "UsbCamera";
    private static final String USB_ACTIVITY = "com.zijin.camera_lib.UsbFaceVerifyActivity";
    private static final String CAMERA_ACTIVITY = "com.zijin.camera_lib.CameraActivity";
    private static final String CAMERA_USER_INFO_ACTIVITY = "com.zijin.camera_lib.CameraUserInfoActivity";
    private static final int ZOOM_WIDEST = 0;
    private static final int POLL_MS = 400;
    private static final int MAX_ATTEMPTS = 20;
    private static final int REPEAT_ZOOM = 3;

    private static final AtomicBoolean installed = new AtomicBoolean();
    private static final Handler MAIN = new Handler(Looper.getMainLooper());
    private static final ConcurrentHashMap<Integer, Integer> generations = new ConcurrentHashMap<>();
    private static final int GRAB_ATTEMPTS = 40;
    /** Extra vertical room, as a fraction of the recognition box height. */
    private static final float CHIN_PAD_Y = 0.40f;
    private static final float CHIN_PAD_X = 0.08f;

    private static final ConcurrentHashMap<Integer, Rect> chinTargets = new ConcurrentHashMap<>();

    private FaceFramingHelper() {
    }

    static void install(Activity host) {
        if (host == null || host.getApplication() == null) {
            return;
        }
        if (installed.compareAndSet(false, true)) {
            host.getApplication().registerActivityLifecycleCallbacks(WATCHER);
        }
    }

    private static boolean isFaceActivity(Activity activity) {
        if (activity == null) {
            return false;
        }
        String name = activity.getClass().getName();
        return USB_ACTIVITY.equals(name)
                || CAMERA_ACTIVITY.equals(name)
                || CAMERA_USER_INFO_ACTIVITY.equals(name);
    }

    private static void watch(Activity activity) {
        int id = System.identityHashCode(activity);
        Integer previous = generations.get(id);
        int generation = (previous == null ? 0 : previous) + 1;
        generations.put(id, generation);
        FaceSuccessGuard.arm(activity);
        pollGrabber(activity, id, generation, 0);
        pollChin(activity, id, generation, 0);
        if (USB_ACTIVITY.equals(activity.getClass().getName())) {
            pollZoom(activity, id, generation, 0, 0);
        }
    }

    private static void stop(Activity activity) {
        if (activity == null) {
            return;
        }
        int id = System.identityHashCode(activity);
        generations.remove(id);
        chinTargets.remove(id);
        FaceFullFrame.clear();
        FaceSuccessGuard.disarm(activity);
    }

    private static void pollGrabber(Activity activity, int id, int generation, int attempt) {
        Integer current = generations.get(id);
        if (current == null || current != generation || activity.isFinishing()) {
            return;
        }
        boolean hooked = FaceFullFrame.install(activity);
        if (!hooked && attempt == GRAB_ATTEMPTS) {
            Log.w(TAG, "full-frame grabber was not installed: " + FaceFullFrame.installFailure());
        }
        MAIN.postDelayed(() -> pollGrabber(activity, id, generation, attempt + 1), POLL_MS);
    }

    private static void pollChin(Activity activity, int id, int generation, int attempt) {
        Integer current = generations.get(id);
        if (current == null || current != generation || activity.isFinishing()) {
            return;
        }
        if (extendForChin(activity, id) || attempt >= GRAB_ATTEMPTS) {
            return;
        }
        MAIN.postDelayed(() -> pollChin(activity, id, generation, attempt + 1), POLL_MS);
    }

    /**
     * Grows the recognition box up and down. The library turns that crop upright
     * before upload, so a 180 degree result would hide the chin if only the
     * bottom were extended.
     */
    private static boolean extendForChin(Activity activity, int id) {
        try {
            Region region = faceRegion(activity);
            int[] frame = frameSize(activity);
            if (region == null || frame == null) {
                return false;
            }
            Rect now = region.getBounds();
            if (now.width() < 2 || now.height() < 2) {
                return false;
            }
            Rect target = chinTargets.get(id);
            if (target == null) {
                target = padded(now, frame[0], frame[1]);
                chinTargets.put(id, target);
            }
            if (now.equals(target)) {
                return true;
            }
            region.set(target.left, target.top, target.right, target.bottom);
            Log.i(TAG, "face region extended for chin " + now + " -> " + target);
            return true;
        } catch (Throwable t) {
            Log.w(TAG, "extend face region for chin failed", t);
            return false;
        }
    }

    private static Rect padded(Rect source, int frameWidth, int frameHeight) {
        int padX = Math.max(1, Math.round(source.width() * CHIN_PAD_X));
        int padY = Math.max(1, Math.round(source.height() * CHIN_PAD_Y));
        int left = Math.max(0, source.left - padX);
        int top = Math.max(0, source.top - padY);
        int right = Math.min(frameWidth, source.right + padX);
        int bottom = Math.min(frameHeight, source.bottom + padY);
        return new Rect(left, top, Math.max(left + 2, right), Math.max(top + 2, bottom));
    }

    private static Region faceRegion(Activity activity) throws ReflectiveOperationException {
        if (USB_ACTIVITY.equals(activity.getClass().getName())) {
            Object region = field(activity, "faceRegion").get(activity);
            return region instanceof Region ? (Region) region : null;
        }
        Object mask = field(activity, "faceRegionMask").get(activity);
        if (mask == null) {
            return null;
        }
        Object region = invoke(mask, "getFaceMaskRegion");
        return region instanceof Region ? (Region) region : null;
    }

    private static int[] frameSize(Activity activity) throws ReflectiveOperationException {
        Object preview = field(activity, "previewSize").get(activity);
        if (preview == null) {
            return null;
        }
        int width;
        int height;
        if (USB_ACTIVITY.equals(activity.getClass().getName())) {
            width = (Integer) preview.getClass().getField("x").get(preview);
            height = (Integer) preview.getClass().getField("y").get(preview);
        } else {
            width = (Integer) invoke(preview, "getLonger");
            height = (Integer) invoke(preview, "getShorter");
        }
        if (width < 2 || height < 2) {
            return null;
        }
        return new int[]{width, height};
    }

    private static void pollZoom(Activity activity, int id, int generation, int attempt, int applied) {
        Integer current = generations.get(id);
        if (current == null || current != generation || activity.isFinishing()) {
            return;
        }
        int nextApplied = applied;
        if (applyWidestZoom(activity)) {
            nextApplied = applied + 1;
            if (nextApplied >= REPEAT_ZOOM) {
                return;
            }
        } else if (attempt >= MAX_ATTEMPTS) {
            return;
        }
        int followApplied = nextApplied;
        int followAttempt = attempt + 1;
        MAIN.postDelayed(
                () -> pollZoom(activity, id, generation, followAttempt, followApplied),
                POLL_MS);
    }

    private static boolean applyWidestZoom(Activity activity) {
        try {
            Object proxy = field(activity, "uvcCamera").get(activity);
            if (proxy == null || !Boolean.TRUE.equals(invoke(proxy, "isCameraOpen"))) {
                return false;
            }
            Object camera = invoke(proxy, "getUVCCamera");
            if (camera == null) {
                return false;
            }
            invoke(camera, "updateCameraParams");
            invoke(camera, "setZoom", new Class<?>[]{int.class}, ZOOM_WIDEST);
            Log.i(TAG, "usb zoom set to widest, current=" + invoke(camera, "getZoom"));
            return true;
        } catch (Throwable t) {
            Log.w(TAG, "set widest zoom failed", t);
            return false;
        }
    }

    private static Object invoke(Object target, String name) throws ReflectiveOperationException {
        return invoke(target, name, new Class<?>[0]);
    }

    private static Object invoke(Object target, String name, Class<?>[] types, Object... args)
            throws ReflectiveOperationException {
        Method method = target.getClass().getMethod(name, types);
        return method.invoke(target, args);
    }

    private static Field field(Activity activity, String name) throws NoSuchFieldException {
        Field field = activity.getClass().getDeclaredField(name);
        field.setAccessible(true);
        return field;
    }

    private static final Application.ActivityLifecycleCallbacks WATCHER =
            new Application.ActivityLifecycleCallbacks() {
                @Override
                public void onActivityCreated(Activity activity, Bundle savedInstanceState) {
                    if (isFaceActivity(activity)) {
                        watch(activity);
                    }
                }

                @Override
                public void onActivityStarted(Activity activity) {
                }

                @Override
                public void onActivityResumed(Activity activity) {
                    if (isFaceActivity(activity)) {
                        watch(activity);
                    }
                }

                @Override
                public void onActivityPrePaused(Activity activity) {
                    if (isFaceActivity(activity)) {
                        FaceSuccessGuard.beforePause(activity);
                    }
                }

                @Override
                public void onActivityPaused(Activity activity) {
                    if (isFaceActivity(activity)) {
                        generations.remove(System.identityHashCode(activity));
                    }
                }

                @Override
                public void onActivityStopped(Activity activity) {
                }

                @Override
                public void onActivitySaveInstanceState(Activity activity, Bundle outState) {
                }

                @Override
                public void onActivityDestroyed(Activity activity) {
                    if (isFaceActivity(activity)) {
                        stop(activity);
                    }
                }
            };
}
