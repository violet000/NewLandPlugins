package com.nlscan.nlsdk;

import android.content.Context;

/**
 * Unified operation interface of the communication port,
 * It is the common abstract operation interface definition of USB and UART.
 * This interface is used internally and is not open to the outside.
 */
public interface NLCommStream {
    enum DevClass {DEV_SOC, DEV_MCU}

    int readPacket(byte[] dst, int pos, int length, int timeout);

    boolean writePacket(byte[] dst, int pos, int length);

    boolean open(Context context);

    boolean open(String pathName, int baudrate);

    void setUsbListener(NLDeviceStream.NLUsbListener listener);

    void close(Context context);

    boolean isOpen();

    boolean isPlug();

    void setReadAck(boolean flag);
}
