package com.zjft.plugin.base;

import com.cw.barcodesdk.SoftDecodingAPI;

public abstract class AbstractBarcodeData implements SoftDecodingAPI.IBarCodeData {

    @Override
    public void sendScan() {
    }

    @Override
    public void onBarCodeData(String s) {
    }

    @Override
    public void getSettings(int i, int i1, int i2, String s, String s1, int i3, int i4) {
    }

    @Override
    public void setSettingsSuccess() {
    }
}
