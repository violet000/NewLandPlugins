package com.zijin.plugin.usbcamera;

import android.app.Activity;
import android.content.Context;
import android.content.Intent;
import android.os.Bundle;

import com.newland.plugins.core.ResultCallback;
import com.zijin.camera_lib.CameraActivity;
import com.zijin.camera_lib.CameraUserInfoActivity;
import com.zijin.camera_lib.UsbFaceVerifyActivity;
import com.zijin.camera_lib.hepler.DataPersistenceHelper;

import org.json.JSONException;
import org.json.JSONObject;

/**
 * USB / local camera face-verify helper extracted from cordova-plugin-usbcamera.
 * Start with {@link #start(Activity)} then forward {@code onActivityResult} to
 * {@link #onActivityResult(Context, int, int, Intent, ResultCallback)}.
 */
public class UsbCamera {

    private int pendingDoWhat;
    private int pendingRequestCode = -1;

    public void startFaceVerifyByUsbCamera(Activity activity, String size, String baseUrl) {
        Intent intent = new Intent(activity, UsbFaceVerifyActivity.class);
        intent.putExtra("doWhat", UsbFaceVerifyActivity.FOR_LOGIN);
        intent.putExtra("size", size);
        intent.putExtra("base_url", baseUrl);
        pendingDoWhat = UsbFaceVerifyActivity.FOR_LOGIN;
        pendingRequestCode = UsbFaceVerifyActivity.REQ_START_USB_CAMERA;
        activity.startActivityForResult(intent, pendingRequestCode);
    }

    public void startGetUserInfoByUsbCamera(Activity activity, String size, String baseUrl, String authorization) {
        Intent intent = new Intent(activity, UsbFaceVerifyActivity.class);
        intent.putExtra("doWhat", UsbFaceVerifyActivity.FOR_USER_INFO);
        intent.putExtra("size", size);
        intent.putExtra("base_url", baseUrl);
        intent.putExtra("authorization", "Bearer " + authorization);
        pendingDoWhat = UsbFaceVerifyActivity.FOR_USER_INFO;
        pendingRequestCode = UsbFaceVerifyActivity.REQ_START_USB_CAMERA;
        activity.startActivityForResult(intent, pendingRequestCode);
    }

    public void startGetUserInfoByCamera(Activity activity, String baseUrl, String authorization) {
        Intent intent = new Intent(activity, CameraUserInfoActivity.class);
        intent.putExtra("base_url", baseUrl);
        intent.putExtra("authorization", "Bearer " + authorization);
        pendingDoWhat = 0;
        pendingRequestCode = CameraUserInfoActivity.REQ_START_CAMERA;
        activity.startActivityForResult(intent, pendingRequestCode);
    }

    public void startFaceVerifyByCamera(Activity activity, String baseUrl) {
        Intent intent = new Intent(activity, CameraActivity.class);
        intent.putExtra("base_url", baseUrl);
        pendingDoWhat = 0;
        pendingRequestCode = CameraActivity.REQ_START_CAMERA;
        activity.startActivityForResult(intent, pendingRequestCode);
    }

    public void onActivityResult(Context context, int requestCode, int resultCode, Intent data, ResultCallback callback) {
        if (callback == null) {
            return;
        }
        if (resultCode != Activity.RESULT_OK) {
            if (requestCode == pendingRequestCode) {
                callback.onError("canceled");
            }
            return;
        }
        if (data == null || data.getExtras() == null) {
            callback.onError("response is empty");
            return;
        }
        Bundle extras = data.getExtras();
        String response = extras.getString("response", "");
        if (requestCode == UsbFaceVerifyActivity.REQ_START_USB_CAMERA
                && pendingDoWhat == UsbFaceVerifyActivity.FOR_USER_INFO) {
            String base64Picture = DataPersistenceHelper.getBase64Picture(context);
            try {
                JSONObject json = new JSONObject(response);
                json.put("faceBase64", base64Picture);
                response = json.toString();
            } catch (JSONException e) {
                callback.onError(e.getMessage());
                return;
            }
        }
        callback.onSuccess(response);
    }
}
