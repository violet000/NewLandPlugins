package com.newland.plugins.core;

import android.content.Context;
import android.content.pm.ApplicationInfo;
import android.content.pm.PackageManager;
import android.os.Bundle;
import android.util.Log;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Optional;

/**
 * Discovers plugin implementations merged into the host app via AndroidManifest meta-data.
 * <p>
 * This is the same approach used by Glide modules / WorkManager: library manifests
 * merge {@code <meta-data>} entries, then a single scan at runtime loads them.
 * Adding or removing an AAR is enough — no central switch-case to maintain.
 */
public final class PluginRegistry {

    public static final String META_DATA_PREFIX = "com.newland.plugin.";

    private static final String TAG = "NewLandPlugins";
    private static final List<HardwarePlugin> PLUGINS = new ArrayList<>();
    private static volatile boolean discovered;

    private PluginRegistry() {
    }

    /**
     * Scan the merged application manifest and instantiate declared plugins.
     * Safe to call multiple times; subsequent calls are no-ops after the first success.
     */
    public static synchronized void discover(@Nullable Context context) {
        if (discovered) {
            return;
        }
        if (context == null) {
            Log.w(TAG, "discover skipped: context is null");
            return;
        }
        Context app = context.getApplicationContext() != null
                ? context.getApplicationContext()
                : context;
        try {
            ApplicationInfo info = app.getPackageManager().getApplicationInfo(
                    app.getPackageName(), PackageManager.GET_META_DATA);
            Bundle meta = info.metaData;
            if (meta != null) {
                for (String key : meta.keySet()) {
                    if (key == null || !key.startsWith(META_DATA_PREFIX)) {
                        continue;
                    }
                    String className = meta.getString(key);
                    loadPlugin(className);
                }
            }
            discovered = true;
            Log.i(TAG, "discovered " + PLUGINS.size() + " plugin(s)");
        } catch (PackageManager.NameNotFoundException e) {
            Log.w(TAG, "discover failed", e);
        }
    }

    public static synchronized void register(@NonNull HardwarePlugin plugin) {
        if (plugin.id() == null || plugin.id().isEmpty()) {
            return;
        }
        for (HardwarePlugin existing : PLUGINS) {
            if (existing.id().equals(plugin.id())) {
                return;
            }
        }
        PLUGINS.add(plugin);
    }

    @NonNull
    public static synchronized List<HardwarePlugin> all() {
        return Collections.unmodifiableList(new ArrayList<>(PLUGINS));
    }

    @NonNull
    public static Optional<HardwarePlugin> findById(@Nullable String id) {
        if (id == null) {
            return Optional.empty();
        }
        for (HardwarePlugin plugin : all()) {
            if (id.equals(plugin.id())) {
                return Optional.of(plugin);
            }
        }
        return Optional.empty();
    }

    @NonNull
    public static List<HardwarePlugin> byCategory(@NonNull PluginCategory category) {
        List<HardwarePlugin> result = new ArrayList<>();
        for (HardwarePlugin plugin : all()) {
            if (plugin.category() == category) {
                result.add(plugin);
            }
        }
        return Collections.unmodifiableList(result);
    }

    public static boolean contains(@Nullable String id) {
        return findById(id).isPresent();
    }

    private static void loadPlugin(@Nullable String className) {
        if (className == null || className.isEmpty()) {
            return;
        }
        try {
            Class<?> clazz = Class.forName(className);
            Object instance = clazz.getDeclaredConstructor().newInstance();
            if (instance instanceof HardwarePlugin) {
                register((HardwarePlugin) instance);
            } else {
                Log.w(TAG, className + " does not implement HardwarePlugin");
            }
        } catch (Throwable t) {
            Log.w(TAG, "unable to load plugin " + className, t);
        }
    }
}
