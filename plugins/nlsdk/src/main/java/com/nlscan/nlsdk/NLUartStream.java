package com.nlscan.nlsdk;

import android.content.Context;
import android.os.SystemClock;
import android.util.Log;

import com.aill.androidserialport.SerialPort;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;

/**
 * This class is implemented based on Google's official SerialPort class (https://github.com/cepr/android-serialport-api),
 * Implemented a unified communication device interface NLCommStream.
 * If you need to realize the query function of the serial port, you can use the methods in the SerialPortFinder class.
 * If you encounter the problem that the serial port cannot be opened due to permission reasons, please set the open permission of the serial device by yourself.
 * The SerialPort class has a reference example, it is not implemented here.
 * Note: The implementation of SerialPort here has not been rigorously tested.
 */

public class NLUartStream implements NLCommStream {
    private final Object ioLock = new Object();
    private SerialPort serialPort;
    private OutputStream outputStream;
    private InputStream inputStream;
    private volatile boolean isUartOpen = false;
    /**
     * Set before the fd is closed so a reader blocked in {@link #readPacket}
     * returns without touching the port. Close then takes {@link #ioLock}.
     */
    private volatile boolean abortRead = false;

    /**
     * @param dst     receive buffer
     * @param pos     receive buffer offset
     * @param length  maximum receiving length
     * @param timeout overtime time
     * @return The number of bytes read within the specified time
     */
    @Override
    public int readPacket(byte[] dst, int pos, int length, int timeout) {
        int size = 0;

        if (dst == null || length <= 0 || pos < 0 || pos >= dst.length)
            return 0;

        long stime = SystemClock.uptimeMillis();
        do {
            if (abortRead || !isUartOpen)
                return size;
            int avail = 0;
            synchronized (ioLock) {
                InputStream in = inputStream;
                if (abortRead || !isUartOpen || in == null)
                    return size;
                try {
                    avail = in.available();
                    if (avail > 0) {
                        int n = in.read(dst, size + pos, length - size);
                        if (n < 0)
                            return size;
                        if (n > 0) {
                            size += n;
                            stime = SystemClock.uptimeMillis();
                        }
                    }
                } catch (IOException e) {
                    return size;
                }
            }
            if (avail <= 0) {
                try {
                    Thread.sleep(10);
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                    return size;
                }
            }
        } while ((size < length) && ((SystemClock.uptimeMillis() - stime) < timeout)
                && !abortRead && isUartOpen);
        return size;
    }

    /**
     * @param dst    send buffer
     * @param pos    buffer offset
     * @param length send length
     * @return success or failure
     */
    @Override
    public boolean writePacket(byte[] dst, int pos, int length) {
        if ((dst == null) || (length <= 0) || pos < 0 || pos + length > dst.length)
            return false;
        synchronized (ioLock) {
            OutputStream out = outputStream;
            if (!isUartOpen || out == null)
                return false;
            try {
                out.write(dst, pos, length);
                return true;
            } catch (IOException e) {
                Log.w("NLUartStream", "write failed", e);
                return false;
            }
        }
    }

    @Override
    public boolean open(Context context) {
        return false;
    }

    /**
     * @param pathName Serial device name
     * @param baudrate baud rate
     * @return success or failure
     */
    @Override
    public boolean open(String pathName, int baudrate) {
        synchronized (ioLock) {
            if (isUartOpen || serialPort != null)
                releasePortLocked();
            try {
                SerialPort port = new SerialPort(new File(pathName), baudrate, 0);
                inputStream = port.getInputStream();
                outputStream = port.getOutputStream();
                serialPort = port;
                abortRead = false;
                isUartOpen = true;
                return true;
            } catch (IOException | RuntimeException e) {
                Log.e("NLUartStream", "The device file could not be opened", e);
                releasePortLocked();
                return false;
            }
        }
    }

    @Override
    public void setUsbListener(NLDeviceStream.NLUsbListener listener) {

    }


    /**
     *
     */
    @Override
    public void close(Context context) {
        // Visible to the reader before we wait for ioLock, so it leaves readPacket
        // instead of entering another read on a port we are about to close.
        abortRead = true;
        isUartOpen = false;
        synchronized (ioLock) {
            releasePortLocked();
        }
    }

    /**
     * Close streams before the native fd. The reader must not be inside read/write;
     * caller holds {@link #ioLock}, and {@link #abortRead} is already set.
     */
    private void releasePortLocked() {
        abortRead = true;
        isUartOpen = false;
        InputStream in = inputStream;
        OutputStream out = outputStream;
        SerialPort port = serialPort;
        inputStream = null;
        outputStream = null;
        serialPort = null;
        try {
            if (in != null)
                in.close();
        } catch (IOException ignored) {
        }
        try {
            if (out != null)
                out.close();
        } catch (IOException ignored) {
        }
        if (port != null) {
            try {
                port.close();
            } catch (RuntimeException ignored) {
            }
        }
    }

    /**
     * @return port open status
     */
    @Override
    public boolean isOpen() {
        return isUartOpen;
    }

    @Override
    public boolean isPlug() {
        return false;
    }

    @Override
    public void setReadAck(boolean flag) {
    }
}
