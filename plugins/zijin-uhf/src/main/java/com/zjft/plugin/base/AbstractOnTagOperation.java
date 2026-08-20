package com.zjft.plugin.base;

import com.cw.r2000uhfsdk.IOnTagOperation;
import com.cw.r2000uhfsdk.helper.OperateTagBuffer;

public abstract class AbstractOnTagOperation implements IOnTagOperation {
    @Override
    public void getAccessEpcMatch(OperateTagBuffer operateTagBuffer) {
    }

    @Override
    public void readTagResult(OperateTagBuffer operateTagBuffer) {
    }

    @Override
    public void writeTagResult(String s) {
    }

    @Override
    public void lockTagResult() {
    }

    @Override
    public void killTagResult() {
    }

    @Override
    public void onLog(String s, int i) {
    }
}
