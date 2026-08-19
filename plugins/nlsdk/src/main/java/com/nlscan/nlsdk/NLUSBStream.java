package com.nlscan.nlsdk;

import android.app.PendingIntent;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.hardware.usb.UsbConstants;
import android.hardware.usb.UsbDevice;
import android.hardware.usb.UsbDeviceConnection;
import android.hardware.usb.UsbEndpoint;
import android.hardware.usb.UsbInterface;
import android.hardware.usb.UsbManager;
import android.hardware.usb.UsbRequest;
import android.os.Build;
import android.os.Process;
import android.util.Log;

import java.nio.ByteBuffer;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.TimeUnit;

/**
 * This abstract class encapsulates the basic operation functions of USB devices,
 * such as USB device enumeration opening, closing, listening and unplugging, and USB permission operations.
 * The enumeration of USB devices here is only for the VID and PID specified by Newland's hardware decoding products.
 * If the VID and PID are customized, please modify the relevant code.
 */
abstract class NLUSBStream implements NLCommStream{
    private UsbDeviceConnection connection;
    private UsbInterface        dataInterface;
    private UsbEndpoint         readEndpoint;
    private UsbEndpoint         writeEndpoint;
    private UsbManager          usbManager;
    private boolean plugFlag=false;
    private UsbDevice usbDevice;
    private int devCls;
    private boolean mStop;
    static String TAG   = "NLUSB";
    private static final String ACTION_USB_PERMISSION = "com.android.example.USB_PERMISSION";
    private boolean isAck = true;
    private UsbRequest usbRequest;
    private int inMax;
    private BlockingQueue<ByteBuffer> ReadPacketQ;
    private UsbNativListener usbListener;
    private final byte[] lock = new byte[0];

    private final BroadcastReceiver mUsbPermissionActionReceiver = new BroadcastReceiver() {
        public void onReceive(Context context, Intent intent) {
            String action = intent.getAction();
            UsbDevice device = intent.getParcelableExtra(UsbManager.EXTRA_DEVICE);
            assert device != null;
            if(!device.equals(usbDevice))
                return;
            if(UsbManager.ACTION_USB_DEVICE_ATTACHED.equals(action))
            {
                usbListener.actionUsbPlug(1);
                plugFlag = true;
            }
            else if(UsbManager.ACTION_USB_DEVICE_DETACHED.equals(action))
            {
                usbListener.actionUsbPlug(0);
                plugFlag = false;
            }
        }
    };

    abstract static class UsbNativListener{
        abstract void actionUsbPlug(int event);
        abstract void actionUsbRecv(ByteBuffer Buff);
    }

    boolean openCtx(Context context, byte[] usbClass){
        if (isOpen())
            return false;
        usbManager = (UsbManager)context.getSystemService(Context.USB_SERVICE);
        IntentFilter filter = new IntentFilter(ACTION_USB_PERMISSION);
        filter.addAction(UsbManager.ACTION_USB_DEVICE_ATTACHED);
        filter.addAction(UsbManager.ACTION_USB_DEVICE_DETACHED);
        context.registerReceiver(mUsbPermissionActionReceiver, filter);
//        PendingIntent mPermissionIntent = PendingIntent.getBroadcast(context, 0, new Intent(ACTION_USB_PERMISSION), 0);
        PendingIntent mPermissionIntent = null;
//        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
//            mPermissionIntent = PendingIntent.getBroadcast(context, 0, new Intent(ACTION_USB_PERMISSION), PendingIntent.FLAG_MUTABLE);
//        } else {
//            mPermissionIntent = PendingIntent.getBroadcast(context, 0, new Intent(ACTION_USB_PERMISSION), 0);
//        }

        if(usbManager == null) {
            Log.e(TAG, "Don't support USB service.");
            return false;
        }
        //UsbDevice device = null;
        HashMap<String, UsbDevice> deviceMap  = usbManager.getDeviceList();
        for (Map.Entry<String, UsbDevice> entry : deviceMap.entrySet()) {
            UsbDevice usbdev = entry.getValue();
            final int vid = usbdev.getVendorId();
            final int pid = usbdev.getProductId();
            if (vid != 0x1EAB) continue;
            final int lpid = pid & 0xFF;
            for (byte aClass : usbClass) {
                if (lpid == aClass) {
                    devCls = getDevCls(lpid);
                    usbDevice = usbdev;
                    break;
                }
            }
            if (usbDevice != null)
                break;
        }
        if (usbDevice == null) {
            Log.e(TAG, "No Device found.");
            return false;
        }

        if (!usbManager.hasPermission(usbDevice)) {
            usbManager.requestPermission(usbDevice, mPermissionIntent);
            return false;
        }

        ReadPacketQ = new ArrayBlockingQueue<>(3);

        return openUsb(usbDevice);
    }

    void setNativListener(UsbNativListener listener)
    {
        usbListener = listener;
    }

    @Override
    public void setReadAck(boolean flag){
        isAck = flag;
    }

    /**
     *  Use asynchronous IO to receive and buffer the request to receive IN packets.
     *  The purpose is to solve the packet loss caused by system scheduling when using bulkTransfer blocking calls.
     *  The independent receiving thread will wait for the request of the IN packet until the data is received or the request is canceled.
     *  Receive requests are controlled by mStop.
     */
    private void ReadRequest(){
        if(usbRequest == null) {
            usbRequest = new UsbRequest();
            usbRequest.initialize(connection, readEndpoint);
            inMax = readEndpoint.getMaxPacketSize();
        }

        mStop = false;
        class RecvDataStream implements Runnable {
            public  void run() {
                Process.setThreadPriority(Process.THREAD_PRIORITY_URGENT_AUDIO);
                while(true){
                    try {
                        synchronized (lock) {
                            if (mStop) {
                                return;
                            }

                            ByteBuffer byteBuffer = ByteBuffer.allocate(inMax);
                            if (Build.VERSION.SDK_INT >= 26)
                                usbRequest.queue(byteBuffer);
                            else
                                usbRequest.queue(byteBuffer, inMax);
                            if (connection.requestWait() == usbRequest) {
                                int recvLen = byteBuffer.position();
                                if (isAck) {
//                                Log.i(TAG, "dt:"+recvLen);
                                    if (recvLen > 0) {
                                        usbListener.actionUsbRecv(byteBuffer);
                                    }
                                } else {
                                    try {
//                                    Log.i(TAG, "rv:"+recvLen);
                                        if (recvLen > 0)
                                            ReadPacketQ.put(byteBuffer);
                                    } catch (InterruptedException e) {
                                        e.printStackTrace();
                                    }
                                }
                            } else {
//                            Log.i(TAG, "other endpoint");
                            }
                        }
                    }catch (Exception e){
                        e.printStackTrace();
                        Log.i(TAG, "ReadRequest Error");
                    }
                }
            }
        }
        Thread t = new Thread(new RecvDataStream());
        t.start();
    }


    int read(byte[] dst, int length, int timeout)	{
        try {
            ByteBuffer byteBuffer = ReadPacketQ.poll(timeout, TimeUnit.MILLISECONDS);
            if(byteBuffer == null)
                return -1;
            byte[] retData = byteBuffer.array();
            int recvLen = byteBuffer.position();
            if(recvLen > length)
                return -1;
            System.arraycopy(retData, 0, dst, 0, recvLen);
            return recvLen;
        } catch (InterruptedException e) {
            e.printStackTrace();
        }
        return -2;
    }

    int write(byte[] src, int len, int timeout) {
        if(connection != null)
            return connection.bulkTransfer(writeEndpoint, src,  len, timeout);
        return -2;
    }

    /**
     *  First stop the usbRequest, and set the acceptance task stop flag mStop to true,
     *  and then enter the security lock area to release resources.
     */
    public void close(Context context) {
        if (connection == null)
            return;
        context.unregisterReceiver(mUsbPermissionActionReceiver);

        mStop = true;
        usbRequest.cancel();
        synchronized (lock) {
            usbRequest = null;
            connection.releaseInterface(dataInterface);
            connection.close();
            connection = null;
            dataInterface = null;
            readEndpoint = null;
            writeEndpoint = null;
            usbManager = null;
        }
    }


    public boolean isOpen() {
        return  connection != null;
    }

    public boolean isPlug() {
        return plugFlag;
    }

    private byte[] cmdFeature = {(byte)0xFE, 0};
    boolean hidChangeInterface(boolean change) {
        if(dataInterface == null)
            return false;
        cmdFeature[0]     = (byte)0xFE;
        cmdFeature[1]     = (byte)(change ? 1 : 0);
        final int timeout = 2000;
        final int type    = UsbConstants.USB_TYPE_CLASS | 1;
        final int request = 0x09;
        final int value   = (3 << 8) | 0xFE;
        final int index   = dataInterface.getId();
        final int ret = connection.controlTransfer(type, request, value, index, cmdFeature, 2, timeout);
        return ret == 0 || ret == 2;
    }


    private boolean openUsb(UsbDevice device) {
        int endpointType;

        switch (devCls)
        {
            case UsbConstants.USB_CLASS_HID:
                endpointType = UsbConstants.USB_ENDPOINT_XFER_INT;
                break;
            case UsbConstants.USB_CLASS_CDC_DATA:
                endpointType = UsbConstants.USB_ENDPOINT_XFER_BULK;
                break;
            default:
                endpointType = UsbConstants.USB_ENDPOINT_XFERTYPE_MASK;
                break;
        }

        connection = usbManager.openDevice(device);
        if (connection == null) {
            Log.e(TAG, "Failed to open device.");
            return false;
        }
        dataInterface    = null;
        readEndpoint     = null;
        writeEndpoint    = null;
        final int count   = device.getInterfaceCount();
        for (int i = 0; i < count; ++i) {
            UsbInterface iface = device.getInterface(i);
            final int cls = iface.getInterfaceClass();
            if (cls != devCls) continue;
            if (iface.getInterfaceProtocol() != 0) continue;

            dataInterface = iface;
            final int eps  = dataInterface.getEndpointCount();
            if (eps < 2) break;

            for (int j = 0; j < eps; ++j) {
                UsbEndpoint ep = dataInterface.getEndpoint(j);
                if (ep.getType() != endpointType) continue;
                if (ep.getDirection() == UsbConstants.USB_DIR_IN) {
                    readEndpoint  = ep;
                } else {
                    writeEndpoint = ep;
                }
            }

            if (readEndpoint == null || writeEndpoint == null) break;


            if (!connection.claimInterface(dataInterface, true)) {
                break;
            }
            plugFlag = true;
            ReadRequest();
            return true;
        }
        connection.close();
        connection    = null;
        dataInterface = null;
        readEndpoint  = null;
        writeEndpoint = null;
        usbManager    = null;
        return false;
    }


    private int getDevCls(int usbProtocolID)
    {
        switch(usbProtocolID)
        {
            case 0x22:
            case 0x10:
                return UsbConstants.USB_CLASS_HID;
            case 0x06:
                return UsbConstants.USB_CLASS_CDC_DATA;
        }
        return UsbConstants.USB_CLASS_VENDOR_SPEC;
    }
}
