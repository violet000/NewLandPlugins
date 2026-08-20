package com.newland.plugins.zijin;

import android.content.Context;

import androidx.annotation.Nullable;

import com.newland.plugins.core.PluginRegistry;

/**
 * Convenience entry for apps that depend on {@code zijin-all}.
 */
public final class Zijin {

    private Zijin() {
    }

    public static void init(@Nullable Context context) {
        PluginRegistry.discover(context);
    }
}
