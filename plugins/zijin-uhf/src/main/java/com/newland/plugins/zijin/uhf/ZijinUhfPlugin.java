package com.newland.plugins.zijin.uhf;

import com.newland.plugins.core.HardwarePlugin;
import com.newland.plugins.core.PluginCategory;

public final class ZijinUhfPlugin implements HardwarePlugin {

    public static final String ID = "zijin-uhf";

    @Override
    public String id() {
        return ID;
    }

    @Override
    public String displayName() {
        return "Zijin / Chainway UHF";
    }

    @Override
    public PluginCategory category() {
        return PluginCategory.UHF_RFID;
    }

    @Override
    public String artifactId() {
        return ID;
    }
}
