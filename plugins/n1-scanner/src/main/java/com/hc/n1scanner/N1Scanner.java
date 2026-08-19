package com.hc.n1scanner;

import android.os.SystemClock;
import android.util.Log;

import com.hc.so.HcPowerCtrl;
import com.nlscan.nlsdk.NLDevice;
import com.nlscan.nlsdk.NLDeviceStream;

/**
 * HC N1 infrared barcode scanner facade.
 * Handles module power sequencing and Newland UART communication.
 */
public class N1Scanner implements NLDeviceStream.NLUartListener {

    public static final String DEFAULT_UART_PATH = "/dev/ttyS1";
    public static final int DEFAULT_BAUDRATE = 115200;

    private static final String TAG = "N1Scanner";
    private static final String CMD_CONTINUOUS = "SCNTCE1";

    public interface OnScanResultListener {
        void onScanResult(byte[] data, int length);
    }

    private final NLDeviceStream device =
            new NLDevice(NLDeviceStream.DevClass.DEV_UART);
    private HcPowerCtrl powerCtrl = new HcPowerCtrl();
    private OnScanResultListener listener;
    private String uartPath = DEFAULT_UART_PATH;
    private int baudrate = DEFAULT_BAUDRATE;
    private volatile boolean opened;

    public void setListener(OnScanResultListener listener) {
        this.listener = listener;
    }

    public boolean open() {
        return open(DEFAULT_UART_PATH, DEFAULT_BAUDRATE);
    }

    public boolean open(String uartPath, int baudrate) {
        this.uartPath = uartPath != null ? uartPath : DEFAULT_UART_PATH;
        this.baudrate = baudrate > 0 ? baudrate : DEFAULT_BAUDRATE;

        if (powerCtrl == null) {
            powerCtrl = new HcPowerCtrl();
        }
        powerOn();
        SystemClock.sleep(100);

        boolean ok = device.nl_OpenDevice(this.uartPath, this.baudrate, this);
        if (ok) {
            device.nl_SendCommand(CMD_CONTINUOUS);
            opened = true;
            Log.i(TAG, "open success path=" + this.uartPath + " baud=" + this.baudrate);
        } else {
            opened = false;
            Log.e(TAG, "open failed path=" + this.uartPath);
        }
        return ok;
    }

    public boolean startScan() {
        if (!opened) {
            Log.w(TAG, "startScan ignored: device not open");
            return false;
        }
        try {
            return device.nl_StartScan();
        } catch (Exception e) {
            Log.e(TAG, "startScan error", e);
            return false;
        }
    }

    public boolean stopScan() {
        if (!opened) {
            return false;
        }
        return device.nl_StopScan();
    }

    public void close() {
        try {
            if (opened) {
                device.nl_StopScan();
                device.nl_CloseDevice();
            }
        } finally {
            if (powerCtrl != null) {
                powerCtrl.scanPower(0);
            }
            opened = false;
        }
    }

    public boolean isOpen() {
        return opened && device.nl_DeviceIsOpen();
    }

    public boolean sendCommand(String command) {
        if (!opened) {
            return false;
        }
        return device.nl_SendCommand(command);
    }

    public NLDeviceStream getDevice() {
        return device;
    }

    @Override
    public void actionRecv(byte[] recvBuff, int len) {
        OnScanResultListener l = listener;
        if (l != null && recvBuff != null && len > 0) {
            l.onScanResult(recvBuff, len);
        }
    }

    private void powerOn() {
        powerCtrl.scanTrig(1);
        powerCtrl.scanPower(1);
        powerCtrl.scanWakeup(1);
        powerCtrl.scanPwrdwn(1);
        powerCtrl.scanTrig(0);
    }
}
