package com.newland.plugins.usbcamera;

import com.newland.plugins.core.HardwarePlugin;
import com.newland.plugins.core.PluginCategory;

public final class UsbCameraPlugin implements HardwarePlugin {

    public static final String ID = "usb-camera";

    @Override
    public String id() {
        return ID;
    }

    @Override
    public String displayName() {
        return "Zijin USB Camera";
    }

    @Override
    public PluginCategory category() {
        return PluginCategory.USB_CAMERA;
    }

    @Override
    public String artifactId() {
        return ID;
    }
}
