package com.zijin.plugin.usbcamera;

import android.app.Activity;
import android.app.Application;
import android.os.Bundle;
import android.view.Window;
import android.view.WindowManager;

import java.util.concurrent.atomic.AtomicBoolean;

/**
 * Raises window brightness for face capture. Window-level override does not
 * change system settings and restores automatically when the activity finishes.
 */
final class ScreenBrightnessHelper {

    private static final AtomicBoolean installed = new AtomicBoolean();

    private ScreenBrightnessHelper() {
    }

    static void install(Activity host) {
        if (host == null) {
            return;
        }
        Application app = host.getApplication();
        if (app == null) {
            return;
        }
        if (installed.compareAndSet(false, true)) {
            app.registerActivityLifecycleCallbacks(WATCHER);
        }
    }

    static void boost(Activity activity) {
        Window window = activity.getWindow();
        if (window == null) {
            return;
        }
        window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
        WindowManager.LayoutParams lp = window.getAttributes();
        lp.screenBrightness = WindowManager.LayoutParams.BRIGHTNESS_OVERRIDE_FULL;
        window.setAttributes(lp);
    }

    private static boolean isFaceVerifyActivity(Activity activity) {
        String name = activity.getClass().getName();
        return "com.zijin.camera_lib.CameraActivity".equals(name)
                || "com.zijin.camera_lib.CameraUserInfoActivity".equals(name)
                || "com.zijin.camera_lib.UsbFaceVerifyActivity".equals(name);
    }

    private static final Application.ActivityLifecycleCallbacks WATCHER =
            new Application.ActivityLifecycleCallbacks() {
                @Override
                public void onActivityCreated(Activity activity, Bundle savedInstanceState) {
                    if (isFaceVerifyActivity(activity)) {
                        boost(activity);
                    }
                }

                @Override
                public void onActivityStarted(Activity activity) {
                }

                @Override
                public void onActivityResumed(Activity activity) {
                    if (isFaceVerifyActivity(activity)) {
                        boost(activity);
                    }
                }

                @Override
                public void onActivityPaused(Activity activity) {
                }

                @Override
                public void onActivityStopped(Activity activity) {
                }

                @Override
                public void onActivitySaveInstanceState(Activity activity, Bundle outState) {
                }

                @Override
                public void onActivityDestroyed(Activity activity) {
                }
            };
}
