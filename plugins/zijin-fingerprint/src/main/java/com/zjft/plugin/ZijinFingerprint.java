package com.zjft.plugin;

import android.app.Activity;
import android.hardware.usb.UsbDevice;
import android.hardware.usb.UsbManager;
import android.os.AsyncTask;
import android.util.Log;

import com.cw.fpfbbsdk.FingerPrintAPI;
import com.cw.serialportsdk.USB.USBFingerManager;
import com.cw.serialportsdk.utils.DataUtils;
import com.newland.plugins.core.ResultCallback;

import java.io.File;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

import cn.com.aratek.fp.Bione;
import cn.com.aratek.fp.FingerprintImage;
import cn.com.aratek.fp.FingerprintScanner;
import cn.com.aratek.util.Result;

/**
 * Fingerprint module extracted from cordova-plugin-x-zijinutil.
 */
public class ZijinFingerprint {

    private static final String TAG = "ZijinFingerprint";

    private final Activity activity;
    private final ExecutorService io = Executors.newSingleThreadExecutor();

    private volatile ResultCallback callback;
    private FingerprintScanner scanner;
    private FingerPrintAPI fpApi;
    private String dbPath = "";
    private FingerprintAsyncTask fingerprintTask;
    private volatile boolean taskDone;

    public ZijinFingerprint(Activity activity) {
        this.activity = activity;
        initDbPath();
    }

    public void open(ResultCallback callback) {
        this.callback = callback;
        USBFingerManager.getInstance(activity).openUSB(new USBFingerManager.OnUSBFingerListener() {
            @Override
            public void onOpenUSBFingerSuccess(String s, UsbManager usbManager, UsbDevice usbDevice) {
                scanner = new FingerprintScanner(activity);
                fpApi = FingerPrintAPI.getInstance();
                openScanner();
            }

            @Override
            public void onOpenUSBFingerFailure(String s, int i) {
                // SDK may invoke this even when open succeeds.
            }
        });
    }

    public void close(ResultCallback callback) {
        if (callback != null) {
            this.callback = callback;
        }
        closeInternal();
    }

    public void destroy() {
        closeInternal();
        io.shutdownNow();
    }

    public void scan(ResultCallback callback) {
        this.callback = callback;
        if (fpApi == null || scanner == null) {
            error("please turn on the fingerprint before performing this operation.");
            return;
        }
        fingerprintTask = new FingerprintAsyncTask();
        fingerprintTask.execute("enroll");
    }

    public void verify(ResultCallback callback) {
        this.callback = callback;
        if (fpApi == null || scanner == null) {
            error("please turn on the fingerprint before performing this operation.");
            return;
        }
        fingerprintTask = new FingerprintAsyncTask();
        fingerprintTask.execute("identify");
    }

    public void loadFpData(List<String> fpDataList, ResultCallback callback) {
        this.callback = callback;
        io.execute(() -> {
            clearFpDb();
            batchEnroll(fpDataList == null ? new ArrayList<>() : fpDataList);
        });
    }

    private void openScanner() {
        if (scanner == null || fpApi == null) {
            return;
        }
        if (scanner.open() != FingerprintScanner.RESULT_OK) {
            error("open fingerprint failed");
            return;
        }
        if (fpApi.initialize(activity, dbPath) != Bione.RESULT_OK) {
            error("open fpApi failed");
            return;
        }
        success("open fingerprint success, current version: " + fpApi.getVersion());
    }

    private void closeInternal() {
        if (scanner == null) {
            return;
        }
        if (fingerprintTask != null && fingerprintTask.getStatus() != AsyncTask.Status.FINISHED) {
            fingerprintTask.cancel(false);
            fingerprintTask.waitForDone();
        }
        scanner.close();
        if (fpApi != null) {
            fpApi.exit();
        }
        USBFingerManager.getInstance(activity).closeUSB();
        scanner = null;
        fpApi = null;
    }

    private void initDbPath() {
        File dir = new File(activity.getFilesDir(), "fingerprint_data");
        if (!dir.exists()) {
            dir.mkdirs();
        }
        dbPath = new File(dir, "fingerprint.db").getPath();
    }

    private void clearFpDb() {
        if (fpApi != null) {
            fpApi.clear();
        }
    }

    private void batchEnroll(List<String> fpDataList) {
        if (fpApi == null || scanner == null) {
            error("please turn on the fingerprint before performing this operation.");
            return;
        }
        for (String fpData : fpDataList) {
            int freeId = fpApi.getFreeID();
            if (freeId < 0) {
                clearFpDb();
                error("batch enroll fp failed, cause: cant get usable free fp ID.");
                return;
            }
            int ret = fpApi.enroll(freeId, DataUtils.hexStringTobyte(fpData));
            if (ret != Bione.RESULT_OK) {
                clearFpDb();
                error("batch enroll fp failed.");
                return;
            }
        }
        success("batch enroll fp success.");
    }

    private void success(String data) {
        ResultCallback cb = callback;
        if (cb != null) {
            cb.onSuccess(data);
        }
    }

    private void error(String message) {
        ResultCallback cb = callback;
        if (cb != null) {
            cb.onError(message);
        }
    }

    private class FingerprintAsyncTask extends AsyncTask<String, String, FpStatus> {

        @Override
        protected FpStatus doInBackground(String... params) {
            taskDone = false;
            try {
            String action = params[0];
            FingerprintImage fi = null;
            byte[] fpFeat = null;
            byte[] fpTemp;
            Result res;
            if ("enroll".equals(action) || "identify".equals(action)) {
                int retry = 0;
                scanner.prepare();
                while (true) {
                    res = scanner.capture();
                    fi = (FingerprintImage) res.data;
                    if (fi != null) {
                        int quality = fpApi.getFingerprintQuality(fi);
                        if (quality < 50 && retry < 3 && !isCancelled()) {
                            retry++;
                            continue;
                        }
                    }
                    if (res.error != FingerprintScanner.NO_FINGER || isCancelled()) {
                        break;
                    }
                }
                scanner.finish();
                if (isCancelled()) {
                    return FpStatus.CANCELED;
                }
                if (res.error != FingerprintScanner.RESULT_OK) {
                    return FpStatus.CAPTURE_FAILED;
                }
            }

            res = fpApi.extractFeature(fi);
            if (res.error != Bione.RESULT_OK) {
                return FpStatus.EXTRACT_FAILED;
            }
            fpFeat = (byte[]) res.data;

            if ("enroll".equals(action)) {
                res = fpApi.makeTemplate(fpFeat, fpFeat, fpFeat);
                if (res.error != Bione.RESULT_OK) {
                    return FpStatus.GENERALIZE_FAILED;
                }
                fpTemp = (byte[]) res.data;
                int id = fpApi.getFreeID();
                if (id < 0) {
                    return FpStatus.GENERALIZE_FAILED;
                }
                if (fpApi.enroll(id, fpTemp) != Bione.RESULT_OK) {
                    return FpStatus.ENTRY_FAILED;
                }
                success(DataUtils.bytesToHexString(fpTemp));
                return FpStatus.NONE;
            }

            int id = fpApi.identify(fpFeat);
            if (id < 0) {
                return FpStatus.IDENTIFY_FAILED;
            }
            return FpStatus.OK;
            } finally {
                taskDone = true;
            }
        }

        @Override
        protected void onPostExecute(FpStatus status) {
            if (status == FpStatus.CANCELED) {
                error("fingerprint async task was canceled.");
            } else if (status == FpStatus.CAPTURE_FAILED) {
                error("capture fingerprint failed.");
            } else if (status == FpStatus.EXTRACT_FAILED) {
                error("extract feature failed.");
            } else if (status == FpStatus.GENERALIZE_FAILED) {
                error("generalize fingerprint failed.");
            } else if (status == FpStatus.ENTRY_FAILED) {
                error("entry fingerprint failed.");
            } else if (status == FpStatus.IDENTIFY_FAILED) {
                error("fingerprint identify failed.");
            } else if (status == FpStatus.OK) {
                success("success.");
            }
        }

        void waitForDone() {
            while (!taskDone) {
                try {
                    Thread.sleep(50);
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                    break;
                }
            }
        }
    }

    private enum FpStatus {
        CANCELED,
        CAPTURE_FAILED,
        EXTRACT_FAILED,
        GENERALIZE_FAILED,
        ENTRY_FAILED,
        IDENTIFY_FAILED,
        OK,
        NONE
    }
}
