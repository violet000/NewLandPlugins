package com.nlscan.nlsdk;

import android.content.Context;

import java.nio.ByteBuffer;
import java.util.Arrays;
import java.util.Timer;
import java.util.TimerTask;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.BlockingQueue;


/**
 * Here, the operation interface of USB HIDPOS class is encapsulated,
 * and the POS protocol of Newland is installed and unpacked for the read and write functions.
 */
class NLUsbPos extends NLUSBStream {
    private NLDeviceStream.NLUsbListener usbListener;
    private Timer readTimer = null;
    private TimerTask timerTask;
    private byte[] codeBuffer = new byte[4096];
    private int coderPos = 0;
    private BlockingQueue<ByteBuffer> coderPacketQ;
    private int PACKET_Q_SIZE = 64;             // 64 * 64 = 4096

    @Override
    public boolean open(Context context) {
        final byte[] usbClass = {0x10};   //

        return super.openCtx(context, usbClass);
    }

    @Override
    public boolean open(String pathName, int baudrate) {
        return false;
    }

    @Override
    public void close(Context context) {
        super.close(context);
    }

    /**
     * /**
     * Write NL definition POS protocol package
     * A brief description is as follows, and the detailed agreement format can be found in the relevant documents of the company
     * -------------------------------------
     * | byte0       byte1  ...  byte63    |
     * | pos protocol header   length        end package flag |
     * -----------------------------------
     *
     * @param src write content
     * @param pos buffer offset
     * @param len content length
     * @return true on success or false on failure
     */
    @Override
    public boolean writePacket(byte[] src, int pos, int len) {
        byte packageSize = 64;
        byte[] buffer = new byte[packageSize];
        byte maxSendLen = (byte) (packageSize - 2);

        while (len > 0) {
            buffer[0] = 4;
            if (len >= maxSendLen) {
                buffer[1] = maxSendLen;
                System.arraycopy(src, pos, buffer, 2, maxSendLen);
                pos += maxSendLen;
                len -= maxSendLen;
            } else {
                buffer[1] = (byte) len;
                System.arraycopy(src, pos, buffer, 2, len);
                Arrays.fill(buffer, 2 + len, packageSize, (byte) 0);
                pos += len;
                len -= len;
            }
            int bytes = write(buffer, packageSize, 3000);
            if (bytes != packageSize) return false;
        }
        return true;
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

                                int len = retData[1] & 0xFF;
                                if (len > packageSize - 2) return;

                                System.arraycopy(retData, 2, codeBuffer, coderPos, len);
                                coderPos += len;
                            } catch (InterruptedException e) {
                                e.printStackTrace();
                            }

                        }
                        if (coderPos > 0)
                            usbListener.actionUsbRecv(codeBuffer, coderPos);
                    }
                };
                readTimer.schedule(timerTask, 50);
            }
        });
    }


    /**
     * Read the POS protocol package defined by NL, the brief description is as follows,
     * and the detailed protocol format can be found in the company's relevant documents
     * -------------------------------------
     * | byte0       byte1  ...  byte63    |
     * | pos protocol header  length        end package flag |
     * -----------------------------------
     *
     * @param dst     receive response buffer
     * @param pos     buffer offset
     * @param length  receive buffer length
     * @param timeout Single packet receive timeout
     * @return Receive data length (greater than 0), return a negative value if an error occurs
     */
    public int readPacket(byte[] dst, int pos, int length, int timeout) {
        int packageSize = 64;
        byte[] buffer = new byte[packageSize];

        int ret = super.read(buffer, packageSize, timeout);
        if (ret < 0) return ret;
        if (ret != packageSize || buffer[0] != 2) return 0;
        int len = buffer[1] & 0xFF;
        if (len > packageSize - 2) return 0;
        if (len > length) len = length;            // drop data
        System.arraycopy(buffer, 2, dst, pos, len);
        return len;
    }
}
