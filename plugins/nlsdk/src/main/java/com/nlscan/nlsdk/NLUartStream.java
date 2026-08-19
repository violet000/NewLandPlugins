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
    private SerialPort serialPort;
    private OutputStream outputStream;
    private InputStream inputStream;
    private boolean isUartOpen = false;

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
        long stime, etime;

        if (dst == null || length == 0)
            return 0;

        stime = SystemClock.uptimeMillis();
        do {
            try {
                if (inputStream.available() > 0) {
                    size += inputStream.read(dst, (size + pos), length - size);
                    stime = SystemClock.uptimeMillis();
                } else {
                    Thread.sleep(10);
                }

            } catch (IOException | InterruptedException e) {
                e.printStackTrace();
            }
            etime = SystemClock.uptimeMillis();
        } while ((size < length) && ((etime - stime) < timeout));
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
        if ((dst == null) || (length <= pos))
            return false;
        try {
            outputStream.write(dst, pos, length);
        } catch (IOException e) {
            e.printStackTrace();
        }
        return true;
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
        try {
            // Open the serial port of the /dev/ttyUSB0 path device
            serialPort = new SerialPort(new File(pathName), baudrate, 0);
            inputStream = serialPort.getInputStream();
            outputStream = serialPort.getOutputStream();
        } catch (IOException e) {
            Log.e("NLUartStream","The device file could not be found");
            return false;
        }
        isUartOpen = true;
        return true;
    }

    @Override
    public void setUsbListener(NLDeviceStream.NLUsbListener listener) {

    }


    /**
     *
     */
    @Override
    public void close(Context context) {
        if (isUartOpen) {
            isUartOpen = false;
            serialPort.close();
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
