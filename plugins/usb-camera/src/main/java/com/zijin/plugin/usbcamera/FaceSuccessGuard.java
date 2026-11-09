package com.zijin.plugin.usbcamera;

import android.app.Activity;
import android.os.Handler;
import android.os.Message;
import android.os.SystemClock;
import android.util.Log;
import android.widget.TextView;

import org.json.JSONArray;
import org.json.JSONObject;

import java.lang.reflect.Field;
import java.lang.reflect.InvocationHandler;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.lang.reflect.Proxy;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.atomic.AtomicReference;

/**
 * The face page closes itself as soon as the login API succeeds, and that
 * tears down the USB camera while the preview callback is still returning.
 * Hold the success message until that callback has unwound, and hand the
 * original JSON back so nested {@code user_info} is not dropped.
 */
final class FaceSuccessGuard {

    private static final String TAG = "UsbCamera";
    private static final String USB_ACTIVITY = "com.zijin.camera_lib.UsbFaceVerifyActivity";
    private static final int STATUS_VERIFY_SUCCESS = 2070;
    /** Time for the preview callback to leave native code before the camera is closed. */
    private static final long SETTLE_MS = 500L;
    /** originalPhoto is kept out of the activity result; larger than this is a picture. */
    private static final int MAX_PHOTO_CHARS = 4096;
    private static final int MAX_RAW_CHARS = 2_000_000;

    private static final AtomicBoolean interceptorReady = new AtomicBoolean();
    private static final AtomicBoolean handlerHooked = new AtomicBoolean();
    private static final AtomicReference<String> lastRaw = new AtomicReference<>();
    private static final AtomicLong successAt = new AtomicLong();
    private static final Set<Integer> armed = ConcurrentHashMap.newKeySet();
    private static final Handler MAIN = new Handler(android.os.Looper.getMainLooper());

    private FaceSuccessGuard() {
    }

    static void arm(Activity activity) {
        if (activity == null) {
            return;
        }
        installResponseCapture();
        if (!armed.add(System.identityHashCode(activity))) {
            return;
        }
        if (!wrapSuccessHandler(activity)) {
            armed.remove(System.identityHashCode(activity));
        }
    }

    static void disarm(Activity activity) {
        if (activity == null) {
            return;
        }
        armed.remove(System.identityHashCode(activity));
    }

    /**
     * Used when the handler cannot be wrapped. A short pause on the main thread
     * lets the preview callback return before {@code onPause} closes the camera.
     */
    static void beforePause(Activity activity) {
        if (activity == null || handlerHooked.get()) {
            return;
        }
        long at = successAt.get();
        if (at == 0L || SystemClock.uptimeMillis() - at > 5000L) {
            return;
        }
        successAt.set(0L);
        try {
            Thread.sleep(SETTLE_MS);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }

    private static boolean wrapSuccessHandler(Activity activity) {
        try {
            Field field = activity.getClass().getDeclaredField("messageHandler");
            field.setAccessible(true);
            Object value = field.get(activity);
            if (!(value instanceof Handler)) {
                return false;
            }
            Handler handler = (Handler) value;
            Field callbackField = Handler.class.getDeclaredField("mCallback");
            callbackField.setAccessible(true);
            Handler.Callback previous = (Handler.Callback) callbackField.get(handler);
            callbackField.set(handler, (Handler.Callback) msg -> {
                if (msg != null && msg.what == STATUS_VERIFY_SUCCESS) {
                    scheduleSuccess(activity, handler, previous, msg.obj);
                    return true;
                }
                return previous != null && previous.handleMessage(msg);
            });
            handlerHooked.set(true);
            Log.i(TAG, "face success delivery delayed until the camera callback returns");
            return true;
        } catch (Throwable t) {
            Log.w(TAG, "could not delay face success delivery", t);
            return false;
        }
    }

    private static void scheduleSuccess(Activity activity, Handler handler, Handler.Callback previous, Object model) {
        showSuccess(activity);
        Object payload = responsePayload(model);
        MAIN.postDelayed(() -> deliverSuccess(activity, handler, previous, payload), SETTLE_MS);
    }

    private static void deliverSuccess(Activity activity, Handler handler, Handler.Callback previous, Object payload) {
        if (activity.isFinishing() || activity.isDestroyed()) {
            return;
        }
        releaseUsbCamera(activity);
        Message copy = Message.obtain();
        copy.what = STATUS_VERIFY_SUCCESS;
        copy.obj = payload;
        try {
            if (previous != null) {
                previous.handleMessage(copy);
            } else {
                handler.handleMessage(copy);
            }
        } catch (Throwable t) {
            Log.w(TAG, "deliver face success failed", t);
        } finally {
            copy.recycle();
        }
    }

    private static Object responsePayload(Object model) {
        String raw = lastRaw.getAndSet(null);
        if (raw != null && raw.length() <= MAX_RAW_CHARS) {
            try {
                Object parsed = jsonValue(new JSONObject(raw));
                if (parsed instanceof Map) {
                    @SuppressWarnings("unchecked")
                    Map<String, Object> fields = (Map<String, Object>) parsed;
                    omitLargePhoto(fields);
                    return fields;
                }
            } catch (Throwable t) {
                Log.w(TAG, "parse face response failed", t);
            }
        }
        clearModelPhoto(model);
        return model;
    }

    private static void omitLargePhoto(Map<String, Object> fields) {
        Object photo = fields.get("originalPhoto");
        if (photo instanceof String && ((String) photo).length() > MAX_PHOTO_CHARS) {
            fields.remove("originalPhoto");
            Log.i(TAG, "omit originalPhoto from activity result, len=" + ((String) photo).length());
        }
    }

    private static void clearModelPhoto(Object model) {
        if (model == null) {
            return;
        }
        try {
            Method getter = model.getClass().getMethod("getOriginalPhoto");
            Object photo = getter.invoke(model);
            if (!(photo instanceof String) || ((String) photo).length() <= MAX_PHOTO_CHARS) {
                return;
            }
            model.getClass().getMethod("setOriginalPhoto", String.class).invoke(model, "");
            Log.i(TAG, "cleared originalPhoto before activity result, len=" + ((String) photo).length());
        } catch (ReflectiveOperationException ignored) {
            // Login results have no original photo.
        }
    }

    private static Object jsonValue(Object value) throws org.json.JSONException {
        if (value == null || value == JSONObject.NULL) {
            return null;
        }
        if (value instanceof JSONObject) {
            JSONObject object = (JSONObject) value;
            Map<String, Object> map = new LinkedHashMap<>();
            Iterator<String> keys = object.keys();
            while (keys.hasNext()) {
                String key = keys.next();
                map.put(key, jsonValue(object.get(key)));
            }
            return map;
        }
        if (value instanceof JSONArray) {
            JSONArray array = (JSONArray) value;
            List<Object> list = new ArrayList<>(array.length());
            for (int i = 0; i < array.length(); i++) {
                list.add(jsonValue(array.get(i)));
            }
            return list;
        }
        return value;
    }

    private static void showSuccess(Activity activity) {
        String[] names = {"tvNotify", "tvMessage"};
        for (String name : names) {
            try {
                Field field = activity.getClass().getDeclaredField(name);
                field.setAccessible(true);
                Object view = field.get(activity);
                if (view instanceof TextView) {
                    ((TextView) view).setText("人脸识别成功");
                    ((TextView) view).setTextColor(0xFF00FF00);
                }
            } catch (NoSuchFieldException ignored) {
                // The other face page uses a different label field.
            } catch (ReflectiveOperationException e) {
                Log.w(TAG, "show face success failed", e);
            }
        }
    }

    private static void releaseUsbCamera(Activity activity) {
        if (!USB_ACTIVITY.equals(activity.getClass().getName())) {
            return;
        }
        try {
            Field field = activity.getClass().getDeclaredField("uvcCamera");
            field.setAccessible(true);
            Object proxy = field.get(activity);
            if (proxy == null) {
                return;
            }
            invokeOptional(proxy, "setPreviewCallback", new Object[]{null});
            invokeOptional(proxy, "stopPreview", new Object[0]);
            invokeOptional(proxy, "closeCamera", new Object[0]);
            invokeOptional(proxy, "unregisterReceiver", new Object[0]);
            field.set(activity, null);
        } catch (Throwable t) {
            Log.w(TAG, "release usb camera before close failed", t);
        }
    }

    private static void invokeOptional(Object target, String name, Object[] args) {
        for (Method method : target.getClass().getMethods()) {
            if (!name.equals(method.getName()) || method.getParameterTypes().length != args.length) {
                continue;
            }
            try {
                method.invoke(target, args);
            } catch (Throwable t) {
                Log.w(TAG, name + " failed", t);
            }
            return;
        }
    }

    private static void installResponseCapture() {
        if (interceptorReady.get()) {
            return;
        }
        try {
            Class<?> helper = Class.forName("com.zijin.camera_lib.hepler.RetrofitHelper");
            Field retrofitField = helper.getDeclaredField("sRetrofit");
            retrofitField.setAccessible(true);
            Object retrofit = retrofitField.get(null);
            if (retrofit == null) {
                return;
            }
            Field clientField = null;
            Object client = null;
            for (Field field : retrofit.getClass().getDeclaredFields()) {
                field.setAccessible(true);
                Object value = field.get(retrofit);
                if (value != null && "okhttp3.OkHttpClient".equals(value.getClass().getName())) {
                    clientField = field;
                    client = value;
                    break;
                }
            }
            if (clientField == null || client == null) {
                return;
            }
            Object builder = client.getClass().getMethod("newBuilder").invoke(client);
            Class<?> interceptorClass = Class.forName("okhttp3.Interceptor");
            Object interceptor = Proxy.newProxyInstance(
                    interceptorClass.getClassLoader(),
                    new Class<?>[]{interceptorClass},
                    responseCapture());
            builder.getClass().getMethod("addInterceptor", interceptorClass).invoke(builder, interceptor);
            clientField.set(retrofit, builder.getClass().getMethod("build").invoke(builder));
            interceptorReady.set(true);
            Log.i(TAG, "capturing face API JSON so user_info is returned");
        } catch (Throwable t) {
            Log.w(TAG, "could not capture face API JSON", t);
        }
    }

    private static InvocationHandler responseCapture() {
        return (proxy, method, args) -> {
            if (!"intercept".equals(method.getName()) || args == null || args.length != 1) {
                return null;
            }
            Object chain = args[0];
            Object request = chain.getClass().getMethod("request").invoke(chain);
            Object response;
            try {
                response = proceed(chain, request);
            } catch (InvocationTargetException e) {
                Throwable cause = e.getCause() == null ? e : e.getCause();
                if (cause instanceof RuntimeException) {
                    throw (RuntimeException) cause;
                }
                if (cause instanceof Error) {
                    throw (Error) cause;
                }
                throw cause;
            }
            rememberFaceBody(request, response);
            return response;
        };
    }

    private static Object proceed(Object chain, Object request) throws Throwable {
        for (Method method : chain.getClass().getMethods()) {
            if ("proceed".equals(method.getName()) && method.getParameterTypes().length == 1) {
                return method.invoke(chain, request);
            }
        }
        throw new NoSuchMethodException("Interceptor.Chain.proceed");
    }

    private static void rememberFaceBody(Object request, Object response) {
        try {
            Object url = request.getClass().getMethod("url").invoke(request);
            String urlText = String.valueOf(url);
            if (!urlText.contains("faceLogin") && !urlText.contains("validateFace")) {
                return;
            }
            Method peek = response.getClass().getMethod("peekBody", long.class);
            Object peeked = peek.invoke(response, 512L * 1024L);
            String text = (String) peeked.getClass().getMethod("string").invoke(peeked);
            if (text != null && text.contains("000000")) {
                lastRaw.set(text);
                successAt.set(SystemClock.uptimeMillis());
            }
        } catch (Throwable t) {
            Log.w(TAG, "read face response failed", t);
        }
    }

}
