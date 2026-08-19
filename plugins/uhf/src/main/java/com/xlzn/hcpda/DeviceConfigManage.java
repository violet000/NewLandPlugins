package com.xlzn.hcpda;

import android.os.Build;

import com.xlzn.hcpda.utils.LoggerUtils;

public class DeviceConfigManage {
    private String TAG = "DeviceConfigManage";

    private static DeviceConfigManage deviceConfigInfo = new DeviceConfigManage();

    public static String module_type = "R2000";
    private UHFConfig uhfConfig = null;

    public enum Platform {
        MTK,
        QUALCOMM
    }

    //设备型号
    private String model;
    //平台 高通、MTK
    private Platform platform;
    //uhf串口号
    private String uhfUart;

    public static DeviceConfigManage getInstance() {
        return deviceConfigInfo;
    }

    private DeviceConfigManage() {
        model = Build.MODEL;// Build.MODEL;
        LoggerUtils.d(TAG, " model=" + model + " Build.DISPLAY=" + Build.DISPLAY);
//        uhfUart = "/dev/ttysWK0";
        uhfUart = "/dev/ttysWK1";
//        LoggerUtils.d(TAG, " API 版本" + BuildConfig.API_VERSION);
        LoggerUtils.d(TAG, " 获取最终串口" + uhfUart);
    }

    private static class ConfigBase {
        //设备型号
        public String model;
        //平台 高通、MTK
        public Platform platform;

        public String getModel() {
            return model;
        }

        private void setModel(String model) {
            this.model = model;
        }

        public Platform getPlatform() {
            return platform;
        }

        private void setPlatform(Platform platform) {
            this.platform = platform;
        }
    }

    public static class UHFConfig extends ConfigBase {
        //uhf串口号
        private String uhfUart;

        public String getUhfUart() {
            return uhfUart;
        }

        public void setUhfUart(String uhfUart) {
            this.uhfUart = uhfUart;
        }
    }

    public UHFConfig getUhfConfig() {
        if (uhfConfig == null) {
            uhfConfig = new UHFConfig();
            uhfConfig.uhfUart = uhfUart;
            uhfConfig.model = model;
            uhfConfig.platform = platform;
        }
        return uhfConfig;
    }
}
