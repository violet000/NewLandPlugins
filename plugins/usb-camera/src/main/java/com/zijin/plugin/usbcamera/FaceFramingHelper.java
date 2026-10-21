package com.zijin.plugin.usbcamera;

import android.app.Activity;
import android.app.Application;
import android.graphics.Point;
import android.graphics.Region;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.util.Log;

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * Sends the full preview frame to the backend and, on USB cameras that
 * support it, pulls zoom to the widest setting.
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
    private static final Set<Integer> widened = ConcurrentHashMap.newKeySet();

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
        if (USB_ACTIVITY.equals(activity.getClass().getName())) {
            useFullUsbFrame(activity, id);
            pollZoom(activity, id, generation, 0, 0);
        } else {
            pollFullLocalFrame(activity, id, generation, 0, 0);
        }
    }

    private static void stop(Activity activity) {
        if (activity == null) {
            return;
        }
        int id = System.identityHashCode(activity);
        generations.remove(id);
        widened.remove(id);
    }

    private static void useFullUsbFrame(Activity activity, int id) {
        if (!widened.add(id)) {
            return;
        }
        try {
            Point preview = (Point) field(activity, "previewSize").get(activity);
            Region region = (Region) field(activity, "faceRegion").get(activity);
            if (preview == null || region == null || preview.x < 2 || preview.y < 2) {
                widened.remove(id);
                return;
            }
            region.set(0, 0, preview.x, preview.y);
            Log.i(TAG, "upload full usb frame " + preview.x + "x" + preview.y);
        } catch (ReflectiveOperationException e) {
            widened.remove(id);
            Log.w(TAG, "use full usb frame failed", e);
        }
    }

    private static void pollFullLocalFrame(Activity activity, int id, int generation, int attempt, int applied) {
        Integer current = generations.get(id);
        if (current == null || current != generation || activity.isFinishing()) {
            return;
        }
        int nextApplied = applied;
        if (useFullLocalFrame(activity)) {
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
                () -> pollFullLocalFrame(activity, id, generation, followAttempt, followApplied),
                POLL_MS);
    }

    private static boolean useFullLocalFrame(Activity activity) {
        try {
            Object mask = field(activity, "faceRegionMask").get(activity);
            Object preview = field(activity, "previewSize").get(activity);
            if (mask == null || preview == null) {
                return false;
            }
            int longer = (Integer) invoke(preview, "getLonger");
            int shorter = (Integer) invoke(preview, "getShorter");
            if (longer < 2 || shorter < 2) {
                return false;
            }
            Region region = (Region) invoke(mask, "getFaceMaskRegion");
            if (region == null) {
                return false;
            }
            region.set(0, 0, longer, shorter);
            Log.i(TAG, "upload full camera frame " + longer + "x" + shorter);
            return true;
        } catch (ReflectiveOperationException e) {
            Log.w(TAG, "use full camera frame failed", e);
            return false;
        }
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
