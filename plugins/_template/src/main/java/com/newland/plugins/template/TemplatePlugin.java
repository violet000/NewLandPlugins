package com.newland.plugins.template;

import com.newland.plugins.core.HardwarePlugin;
import com.newland.plugins.core.PluginCategory;

public final class TemplatePlugin implements HardwarePlugin {

    public static final String ID = "your-plugin";

    @Override
    public String id() {
        return ID;
    }

    @Override
    public String displayName() {
        return "Your Plugin";
    }

    @Override
    public PluginCategory category() {
        return PluginCategory.OTHER;
    }

    @Override
    public String artifactId() {
        return ID;
    }
}
