package com.newland.plugins.uhf;

import com.newland.plugins.core.HardwarePlugin;
import com.newland.plugins.core.PluginCategory;

public final class UhfPlugin implements HardwarePlugin {

    public static final String ID = "uhf";

    @Override
    public String id() {
        return ID;
    }

    @Override
    public String displayName() {
        return "HC UHF RFID";
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
