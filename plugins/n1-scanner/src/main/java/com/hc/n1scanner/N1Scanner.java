package com.hc.n1scanner;

import android.media.AudioManager;
import android.media.ToneGenerator;
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
    private static final String CMD_SERIAL_TRIGGER = "SCNTCE1";
    private static final String CMD_CONTINUOUS_MODE = "SCNMOD3";
    private static final String CMD_START_TRIGGER = "#SCNTRG1";
    private static final String CMD_REREAD_TIMEOUT = "GRRDNT50";
    private static final String CMD_CR_SUFFIX = "233ENA1";
    private static final int MIN_BARCODE_LEN = 3;
    private static final int GOOD_READ_BEEP_MS = 80;
    private static final int GOOD_READ_BEEP_VOLUME = 100;

    public interface OnScanResultListener {
        void onScanResult(byte[] data, int length);
    }

    private final NLDeviceStream device =
            new NLDevice(NLDeviceStream.DevClass.DEV_UART);
    private final StringBuilder uartBuf = new StringBuilder();
    private final Object uartLock = new Object();
    private HcPowerCtrl powerCtrl = new HcPowerCtrl();
    private OnScanResultListener listener;
    private String uartPath = DEFAULT_UART_PATH;
    private int baudrate = DEFAULT_BAUDRATE;
    private volatile boolean opened;
    private volatile boolean scanning;
    private volatile boolean oneShotEmitted;
    private ToneGenerator tone;

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
            device.nl_SendCommand(CMD_SERIAL_TRIGGER);
            opened = true;
            Log.i(TAG, "open success path=" + this.uartPath + " baud=" + this.baudrate);
        } else {
            opened = false;
            Log.e(TAG, "open failed path=" + this.uartPath);
        }
        return ok;
    }

    /**
     * Open the module if needed, switch to continuous decode, and hold IR on
     * until the first barcode, {@link #stopScan()} or {@link #close()}.
     */
    public boolean startContinuousScan() {
        return startContinuousScan(uartPath, baudrate);
    }

    public boolean startContinuousScan(String uartPath, int baudrate) {
        oneShotEmitted = false;
        clearUartBuf();
        if (!opened && !open(uartPath, baudrate)) {
            Log.e(TAG, "startContinuousScan failed: open");
            return false;
        }
        sendCommand(CMD_CR_SUFFIX);
        sendCommand(CMD_REREAD_TIMEOUT);
        boolean mode = sendCommand(CMD_CONTINUOUS_MODE);
        holdTrigger(true);
        boolean serialTrig = sendCommand(CMD_START_TRIGGER);
        boolean pulse = startScan();
        scanning = true;
        Log.i(TAG, "startContinuousScan mode=" + mode
                + " serialTrig=" + serialTrig + " pulse=" + pulse);
        return mode || serialTrig || pulse || isOpen();
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
        scanning = false;
        oneShotEmitted = true;
        clearUartBuf();
        if (!opened) {
            return false;
        }
        boolean stopped = device.nl_StopScan();
        holdTrigger(false);
        return stopped;
    }

    public void close() {
        try {
            if (opened) {
                stopScan();
                device.nl_CloseDevice();
            }
        } finally {
            holdTrigger(false);
            if (powerCtrl != null) {
                powerCtrl.scanPower(0);
            }
            opened = false;
            scanning = false;
            releaseTone();
        }
    }

    public boolean isOpen() {
        return opened && device.nl_DeviceIsOpen();
    }

    public boolean isScanning() {
        return scanning && isOpen();
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
        if (!scanning || oneShotEmitted || listener == null || recvBuff == null || len <= 0) {
            return;
        }
        String chunk = new String(recvBuff, 0, len);
        String barcode;
        synchronized (uartLock) {
            uartBuf.append(chunk);
            barcode = takeNextBarcode();
        }
        if (barcode == null) {
            return;
        }
        if (oneShotEmitted) {
            return;
        }
        oneShotEmitted = true;
        scanning = false;
        holdTrigger(false);
        playGoodReadBeep();
        byte[] out = barcode.getBytes();
        listener.onScanResult(out, out.length);
    }

    private synchronized void playGoodReadBeep() {
        try {
            ToneGenerator player = tone;
            if (player == null) {
                player = new ToneGenerator(AudioManager.STREAM_MUSIC, GOOD_READ_BEEP_VOLUME);
                tone = player;
            }
            player.startTone(ToneGenerator.TONE_PROP_BEEP, GOOD_READ_BEEP_MS);
        } catch (RuntimeException e) {
            Log.w(TAG, "good read beep failed", e);
            releaseTone();
        }
    }

    private synchronized void releaseTone() {
        ToneGenerator player = tone;
        tone = null;
        if (player == null) {
            return;
        }
        try {
            player.release();
        } catch (RuntimeException e) {
            Log.w(TAG, "release beep failed", e);
        }
    }

    private String takeNextBarcode() {
        String raw = uartBuf.toString();
        int consumed = 0;
        String found = null;
        int start = 0;
        for (int i = 0; i < raw.length(); i++) {
            char c = raw.charAt(i);
            if (c != '\r' && c != '\n' && c != '\u0000') {
                continue;
            }
            String piece = normalizeBarcode(raw.substring(start, i));
            start = i + 1;
            consumed = start;
            if (piece != null) {
                found = piece;
                break;
            }
        }
        if (found == null) {
            String rest = raw.substring(start);
            String collapsed = collapseRepeats(rest);
            if (collapsed != null && collapsed.length() >= MIN_BARCODE_LEN
                    && collapsed.length() < rest.length()) {
                found = collapsed;
                consumed = raw.length();
            }
        }
        if (consumed > 0) {
            uartBuf.delete(0, consumed);
        }
        return found;
    }

    /**
     * Extract a single barcode from a UART payload that may contain
     * concatenated copies such as {@code SM4-010104SM4-010104...}.
     */
    public static String normalizeBarcode(String raw) {
        if (raw == null) {
            return null;
        }
        String cleaned = raw.replace("\u0000", "").trim();
        if (cleaned.isEmpty()) {
            return null;
        }
        if (isProtocolNoise(cleaned)) {
            return null;
        }
        String[] parts = cleaned.split("[\\r\\n]+");
        for (String part : parts) {
            String value = part.trim();
            if (value.isEmpty() || isProtocolNoise(value) || !isPrintablePayload(value)) {
                continue;
            }
            String collapsed = collapseRepeats(value);
            if (collapsed != null && collapsed.length() >= MIN_BARCODE_LEN) {
                return collapsed;
            }
        }
        return null;
    }

    static String collapseRepeats(String value) {
        if (value == null) {
            return null;
        }
        String s = value.trim();
        int n = s.length();
        if (n < MIN_BARCODE_LEN) {
            return s.isEmpty() ? null : s;
        }
        for (int len = MIN_BARCODE_LEN; len <= n / 2; len++) {
            if (!s.regionMatches(0, s, len, len)) {
                continue;
            }
            String unit = s.substring(0, len);
            if (isProtocolNoise(unit)) {
                continue;
            }
            int pos = len;
            int count = 1;
            while (pos + len <= n && s.regionMatches(pos, unit, 0, len)) {
                count++;
                pos += len;
            }
            String remain = s.substring(pos);
            boolean remainOk = remain.isEmpty()
                    || unit.startsWith(remain)
                    || unit.endsWith(remain);
            if (count >= 2 && remainOk) {
                return unit;
            }
        }
        return s;
    }

    private static boolean isProtocolNoise(String value) {
        if (value.isEmpty()) {
            return true;
        }
        char first = value.charAt(0);
        return first == 0x02
                || first == '~'
                || value.contains("SCNMOD")
                || value.contains("SCNTRG")
                || value.contains("SCNTCE")
                || value.contains("GRRDNT")
                || value.contains("233ENA");
    }

    private static boolean isPrintablePayload(String value) {
        for (int i = 0; i < value.length(); i++) {
            if (value.charAt(i) < 32) {
                return false;
            }
        }
        return !value.isEmpty();
    }

    private void clearUartBuf() {
        synchronized (uartLock) {
            uartBuf.setLength(0);
        }
    }

    private void powerOn() {
        powerCtrl.scanTrig(1);
        powerCtrl.scanPower(1);
        powerCtrl.scanWakeup(1);
        powerCtrl.scanPwrdwn(1);
        powerCtrl.scanTrig(0);
    }

    private void holdTrigger(boolean hold) {
        if (powerCtrl != null) {
            powerCtrl.scanTrig(hold ? 1 : 0);
        }
    }
}
