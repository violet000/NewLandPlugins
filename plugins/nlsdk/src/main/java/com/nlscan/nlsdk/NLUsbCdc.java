package com.nlscan.nlsdk;

import android.content.Context;

import java.nio.ByteBuffer;
import java.util.Timer;
import java.util.TimerTask;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.BlockingQueue;

/**
 * This encapsulates the communication operation interface of the USB CDC class
 */
class NLUsbCdc extends NLUSBStream {
    private NLDeviceStream.NLUsbListener usbListener;
    private Timer readTimer = null;
    private TimerTask timerTask;
    private byte[] codeBuffer = new byte[4096];
    private int coderPos = 0;
    private BlockingQueue<ByteBuffer> coderPacketQ;
    private int PACKET_Q_SIZE = 64;             // 64 * 64 = 4096

    @Override
    public boolean open(Context context) {
        final byte[] usbClass = {0x06};   //
        return super.openCtx(context, usbClass);
    }

    @Override
    public boolean open(String pathName, int baudrate) {
        return false;
    }

    @Override
    public void setUsbListener(NLDeviceStream.NLUsbListener listener) {
        usbListener = listener;
        coderPacketQ = new ArrayBlockingQueue<>(PACKET_Q_SIZE);

        if (readTimer != null) {
            readTimer.cancel();
            readTimer = null;
            timerTask = null;
        }
        readTimer = new Timer();

        setNativListener(new UsbNativListener() {
            @Override
            public void actionUsbPlug(int event) {
                usbListener.actionUsbPlug(event);
            }

            @Override
            public void actionUsbRecv(ByteBuffer recvBuff) {
                try {
                    coderPacketQ.put(recvBuff);
                } catch (InterruptedException e) {
                    e.printStackTrace();
                }
                timerTask = new TimerTask() {
                    public void run() {
                        int entry = PACKET_Q_SIZE - coderPacketQ.remainingCapacity();
                        ByteBuffer byteBuffer;
                        coderPos = 0;
                        for (int i = 0; i < entry; i++) {
                            try {
                                byteBuffer = coderPacketQ.take();
                                int packageSize = 64;
                                byte[] retData = byteBuffer.array();

                                int len = byteBuffer.position();
                                if (len > packageSize) return;

                                System.arraycopy(retData, 0, codeBuffer, coderPos, len);
                                coderPos += len;
                            } catch (InterruptedException e) {
                                e.printStackTrace();
                            }

                        }
                        if (coderPos > 0)
                            usbListener.actionUsbRecv(codeBuffer, coderPos);
                    }
                };
                readTimer.schedule(timerTask, 20);
            }
        });
    }

    @Override
    public void close(Context context) {
        super.close(context);
    }


    @Override
    public boolean writePacket(byte[] src, int pos, int len) {
        int bytes = write(src, len, 3000);
        return bytes == len;
    }

    public int readPacket(byte[] dst, int pos, int length, int timeout) {
        byte[] buffer = new byte[length];

        int len = super.read(buffer, length, timeout);
        if (len < 0)
            return len;

        if (len > length) len = length;            // drop data
        System.arraycopy(buffer, 0, dst, pos, len);
        return len;
    }

}
