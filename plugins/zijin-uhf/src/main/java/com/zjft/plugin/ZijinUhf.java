package com.zjft.plugin;

import android.app.Activity;
import android.util.Log;

import com.cw.r2000uhfsdk.R2000UHFAPI;
import com.cw.r2000uhfsdk.helper.InventoryBuffer;
import com.cw.r2000uhfsdk.helper.OperateTagBuffer;
import com.google.gson.Gson;
import com.newland.plugins.core.ResultCallback;
import com.zjft.plugin.base.AbstractOnInventoryRealReceiver;
import com.zjft.plugin.base.AbstractOnTagOperation;

import org.json.JSONException;
import org.json.JSONObject;

import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * Chainway R2000 UHF API extracted from cordova-plugin-x-zijinutil.
 */
public class ZijinUhf {

    private static final String TAG = "ZijinUhf";

    private final Activity activity;
    private final R2000UHFAPI uhfApi = R2000UHFAPI.getInstance();
    private final ExecutorService io = Executors.newSingleThreadExecutor();
    private final Gson gson = new Gson();

    private volatile ResultCallback callback;
    private volatile boolean startInventory;

    public ZijinUhf(Activity activity) {
        this.activity = activity;
    }

    public void onResume() {
        if (startInventory) {
            startInventoryReal();
        }
    }

    public void onPause() {
        if (startInventory) {
            stopInventoryReal();
        }
    }

    public void destroy() {
        close(null);
        io.shutdownNow();
    }

    public void open(ResultCallback callback) {
        this.callback = callback;
        io.execute(() -> {
            uhfApi.open(activity);
            Log.i(TAG, "uhf open success");
            success("uhf module open success.");
        });
    }

    public void close(ResultCallback callback) {
        if (callback != null) {
            this.callback = callback;
        }
        io.execute(() -> {
            uhfApi.close();
            success("close uhf success.");
        });
    }

    public void startInventory(ResultCallback callback) {
        this.callback = callback;
        startInventory = true;
        startInventoryReal();
    }

    public void stopInventory(ResultCallback callback) {
        this.callback = callback;
        startInventory = false;
        stopInventoryReal();
    }

    public void setOutputPower(int power, ResultCallback callback) {
        this.callback = callback;
        if (power <= 0 || power > 33) {
            error("please input output power in（1 ~ 33dBm)");
            return;
        }
        uhfApi.setOutputPower(power);
        success("set output power success");
    }

    public void reset() {
        uhfApi.reset();
    }

    public void readTag(JSONObject params, ResultCallback callback) {
        this.callback = callback;
        uhfApi.setOnTagOperation(new AbstractOnTagOperation() {
            @Override
            public void readTagResult(OperateTagBuffer operateTagBuffer) {
                success(gson.toJson(operateTagBuffer));
            }
        });
        try {
            byte btMemBank = Byte.parseByte(params.getString("btMemBank"));
            String btWordAdd = params.getString("btWordAdd");
            String btWordCnt = params.getString("btWordCnt");
            String btAryPassWord = params.getString("btAryPassWord");
            int resultCode = uhfApi.readTag(btMemBank, btWordAdd, btWordCnt, btAryPassWord);
            if (resultCode != 0) {
                error("read tag failed, result code: " + resultCode);
            }
        } catch (JSONException e) {
            error("read tag failed, cause: " + e.getMessage());
        }
    }

    public void writeTag(JSONObject params, ResultCallback callback) {
        this.callback = callback;
        uhfApi.setOnTagOperation(new AbstractOnTagOperation() {
            @Override
            public void onLog(String s, int i) {
                if (s != null && s.contains("失败")) {
                    error(s);
                } else {
                    success("写入成功");
                }
            }
        });
        try {
            byte btMemBank = Byte.parseByte(params.getString("btMemBank"));
            String btWordAdd = params.getString("btWordAdd");
            String btWordCnt = params.getString("btWordCnt");
            String btAryPassWord = params.getString("btAryPassWord");
            String data = params.getString("data");
            uhfApi.writeTag(btMemBank, btWordAdd, btWordCnt, btAryPassWord, data);
        } catch (JSONException e) {
            error("write tag failed, cause: " + e.getMessage());
        }
    }

    public void lockTag(JSONObject params, ResultCallback callback) {
        this.callback = callback;
        uhfApi.setOnTagOperation(new AbstractOnTagOperation() {
            @Override
            public void lockTagResult() {
                success("lock tag success.");
            }
        });
        try {
            String btAryPassWord = params.getString("btAryPassWord");
            byte btMemBank = Byte.parseByte(params.getString("btMemBank"));
            byte btLockType = Byte.parseByte(params.getString("btLockType"));
            int resultCode = uhfApi.lockTag(btAryPassWord, btMemBank, btLockType);
            if (resultCode != 0) {
                error("lock tag failed, result code: " + resultCode);
            }
        } catch (JSONException e) {
            error("lock tag failed, cause: " + e.getMessage());
        }
    }

    public void killTag(JSONObject params, ResultCallback callback) {
        this.callback = callback;
        uhfApi.setOnTagOperation(new AbstractOnTagOperation() {
            @Override
            public void killTagResult() {
                success("kill tag success.");
            }
        });
        try {
            String btAryPassWord = params.getString("btAryPassWord");
            int resultCode = uhfApi.killTag(btAryPassWord);
            if (resultCode != 0) {
                error("kill tag failed, result code: " + resultCode);
            }
        } catch (JSONException e) {
            error("kill tag failed, cause: " + e.getMessage());
        }
    }

    private void startInventoryReal() {
        try {
            uhfApi.startInventoryReal("1");
            uhfApi.setOnInventoryRealReceiver(new AbstractOnInventoryRealReceiver() {
                @Override
                public void inventoryEnd(InventoryBuffer inventoryBuffer) {
                    success(gson.toJson(inventoryBuffer.lsTagList));
                }
            });
        } catch (Exception e) {
            error("start inventory failed: " + e.getMessage());
        }
    }

    private void stopInventoryReal() {
        io.execute(() -> {
            if (uhfApi.getReaderHelper() != null && uhfApi.getReaderHelper().getInventoryFlag()) {
                uhfApi.stopInventoryReal();
                success("stop inventory success.");
            }
        });
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
}
