package com.newland.plugins.zijin.scan;

import com.newland.plugins.core.HardwarePlugin;
import com.newland.plugins.core.PluginCategory;

public final class ZijinScanPlugin implements HardwarePlugin {

    public static final String ID = "zijin-scan";

    @Override
    public String id() {
        return ID;
    }

    @Override
    public String displayName() {
        return "Zijin / Chainway Barcode Scanner";
    }

    @Override
    public PluginCategory category() {
        return PluginCategory.BARCODE_SCANNER;
    }

    @Override
    public String artifactId() {
        return ID;
    }
}
