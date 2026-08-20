package com.newland.plugins.zijin.fp;

import com.newland.plugins.core.HardwarePlugin;
import com.newland.plugins.core.PluginCategory;

public final class ZijinFingerprintPlugin implements HardwarePlugin {

    public static final String ID = "zijin-fingerprint";

    @Override
    public String id() {
        return ID;
    }

    @Override
    public String displayName() {
        return "Zijin / Chainway Fingerprint";
    }

    @Override
    public PluginCategory category() {
        return PluginCategory.FINGERPRINT;
    }

    @Override
    public String artifactId() {
        return ID;
    }
}
