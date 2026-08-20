package com.zjft.plugin;

import android.app.Activity;
import android.util.Log;

import com.cw.barcodesdk.SoftDecodingAPI;
import com.newland.plugins.core.ResultCallback;
import com.zjft.plugin.base.AbstractBarcodeData;

/**
 * Chainway barcode scanner extracted from cordova-plugin-x-zijinutil.
 */
public class ZijinScan extends AbstractBarcodeData {

    private static final String TAG = "ZijinScan";

    public static final String ACTION_SCAN = "scan";
    public static final String ACTION_CONTINUE = "continueScanning";
    public static final String ACTION_RECEIVER = "openScanReceiver";

    private final SoftDecodingAPI api;
    private volatile ResultCallback callback;
    private volatile String action;
    private volatile boolean continueScanning;
    private volatile boolean receiverOpen;

    public ZijinScan(Activity activity) {
        this.api = new SoftDecodingAPI(activity, this);
        this.api.setGlobalSwicth(true);
        openReceiver();
    }

    public void onResume() {
        openReceiver();
        if (continueScanning) {
            api.ContinuousScanning();
        }
    }

    public void onPause() {
        closeReceiver();
        if (continueScanning) {
            api.CloseScanning();
        }
    }

    public void destroy() {
        closeReceiver();
        api.setGlobalSwicth(false);
    }

    public void openReceiver(ResultCallback callback) {
        this.action = ACTION_RECEIVER;
        this.callback = callback;
        if (!receiverOpen) {
            openReceiver();
        }
    }

    public void closeReceiver() {
        if (api != null && receiverOpen) {
            api.closeBarCodeReceiver();
            receiverOpen = false;
        }
    }

    public void scan(ResultCallback callback) {
        this.action = ACTION_SCAN;
        this.callback = callback;
        if (!receiverOpen) {
            openReceiver();
        }
        api.scan();
    }

    public void startContinueScanning(ResultCallback callback) {
        this.action = ACTION_CONTINUE;
        this.callback = callback;
        if (!receiverOpen) {
            openReceiver();
        }
        api.ContinuousScanning();
        continueScanning = true;
    }

    public void stopContinueScanning() {
        api.CloseScanning();
        continueScanning = false;
    }

    public void setScanInterval(int intervalTime) {
        api.setTime(intervalTime);
    }

    private void openReceiver() {
        receiverOpen = true;
        api.openBarCodeReceiver();
    }

    @Override
    public void onBarCodeData(String s) {
        ResultCallback cb = callback;
        if (cb == null) {
            return;
        }
        if (ACTION_CONTINUE.equals(action) || ACTION_RECEIVER.equals(action) || ACTION_SCAN.equals(action)) {
            cb.onSuccess(s);
        } else {
            Log.w(TAG, "drop barcode, action=" + action);
        }
    }
}
