package com.newland.plugins;

import android.content.Context;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.newland.plugins.core.HardwarePlugin;
import com.newland.plugins.core.PluginRegistry;

import java.util.List;

/**
 * Convenience entry for apps that depend on {@code newland-all}.
 * Hardware APIs remain on each plugin (UHFReader, N1Scanner, ...).
 */
public final class NewLand {

    private NewLand() {
    }

    public static void init(@Nullable Context context) {
        PluginRegistry.discover(context);
    }

    @NonNull
    public static List<HardwarePlugin> plugins() {
        return PluginRegistry.all();
    }
}
