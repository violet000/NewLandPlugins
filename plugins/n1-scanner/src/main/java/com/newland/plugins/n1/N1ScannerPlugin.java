package com.newland.plugins.n1;

import com.newland.plugins.core.HardwarePlugin;
import com.newland.plugins.core.PluginCategory;

public final class N1ScannerPlugin implements HardwarePlugin {

    public static final String ID = "n1-scanner";

    @Override
    public String id() {
        return ID;
    }

    @Override
    public String displayName() {
        return "HC N1 Infrared Scanner";
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
