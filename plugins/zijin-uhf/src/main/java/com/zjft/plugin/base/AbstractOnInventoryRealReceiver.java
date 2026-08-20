package com.zjft.plugin.base;

import com.cw.r2000uhfsdk.IOnInventoryRealReceiver;
import com.cw.r2000uhfsdk.helper.InventoryBuffer;

public abstract class AbstractOnInventoryRealReceiver implements IOnInventoryRealReceiver {
    @Override
    public void realTimeInventory() {
    }

    @Override
    public void customized_session_target_inventory(InventoryBuffer inventoryBuffer) {
    }

    @Override
    public void inventoryErr() {
    }

    @Override
    public void inventoryErrEnd() {
    }

    @Override
    public void inventoryEnd(InventoryBuffer inventoryBuffer) {
    }

    @Override
    public void inventoryRefresh(InventoryBuffer inventoryBuffer) {
    }

    @Override
    public void onLog(String s, int i) {
    }
}
