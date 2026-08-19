package com.newland.plugins.core;

/**
 * Hardware capability provided by a NewLand plugin AAR.
 * <p>
 * Add a plugin by implementing this interface and declaring it in the
 * library manifest:
 * <pre>{@code
 * <application>
 *     <meta-data
 *         android:name="com.newland.plugin.your-id"
 *         android:value="com.example.YourPlugin" />
 * </application>
 * }</pre>
 * Then call {@link PluginRegistry#discover(android.content.Context)} once at process start
 * (or {@code NewLand.init(context)} if you depend on {@code newland-all}).
 */
public interface HardwarePlugin {

    /** Stable id used in Maven artifact / manifest, e.g. {@code uhf}. */
    String id();

    String displayName();

    PluginCategory category();

    /** Published Maven artifactId, usually the same as {@link #id()}. */
    String artifactId();

    /**
     * Whether this plugin can run on the current device.
     * Override after you have a Context if you need hardware probing.
     */
    default boolean isAvailable() {
        return true;
    }
}
