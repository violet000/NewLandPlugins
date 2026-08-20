package com.newland.plugins.core;

/**
 * One-shot or streaming result from a hardware plugin.
 * Streaming APIs (inventory, continuous scan) may invoke {@link #onSuccess(String)}
 * multiple times until the operation is stopped.
 */
public interface ResultCallback {

    void onSuccess(String data);

    void onError(String message);
}
