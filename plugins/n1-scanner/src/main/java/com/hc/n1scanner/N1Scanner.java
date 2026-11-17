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
    private static final String CMD_STOP_TRIGGER = "#SCNTRG0";
    private static final String CMD_REREAD_TIMEOUT = "GRRDNT50";
    private static final String CMD_CR_SUFFIX = "233ENA1";
    /** How many times to ask the module before treating it as not booted. */
    private static final int READY_PROBES = 3;
    private static final int READY_PROBE_MS = 80;
    /** Low time so scanTrig is a rising edge, not a rewrite of an already-high pin. */
    private static final int TRIGGER_EDGE_MS = 30;
    private static final int MIN_BARCODE_LEN = 3;
    private static final int GOOD_READ_BEEP_MS = 80;
    private static final int GOOD_READ_BEEP_VOLUME = 100;
    /** Gap after cutting module power so the next power-on edge actually boots it. */
    private static final int POWER_RESET_MS = 120;

    public interface OnScanResultListener {
        void onScanResult(byte[] data, int length);
    }

    private final NLDeviceStream device =
            new NLDevice(NLDeviceStream.DevClass.DEV_UART);
    private final StringBuilder uartBuf = new StringBuilder();
    private final Object uartLock = new Object();
    private final Object sessionLock = new Object();
    private final Object powerLock = new Object();
    private HcPowerCtrl powerCtrl = new HcPowerCtrl();
    private OnScanResultListener listener;
    private String uartPath = DEFAULT_UART_PATH;
    private int baudrate = DEFAULT_BAUDRATE;
    private volatile boolean opened;
    private volatile boolean scanning;
    private volatile boolean oneShotEmitted;
    private volatile boolean powered;
    /** Bumped on close so a late UART callback cannot drive a session that already ended. */
    private volatile int session;
    private ToneGenerator tone;

    public void setListener(OnScanResultListener listener) {
        this.listener = listener;
    }

    public boolean open() {
        return open(DEFAULT_UART_PATH, DEFAULT_BAUDRATE);
    }

    public boolean open(String uartPath, int baudrate) {
        synchronized (sessionLock) {
            return openLocked(uartPath, baudrate);
        }
    }

    private boolean openLocked(String uartPath, int baudrate) {
        this.uartPath = uartPath != null ? uartPath : DEFAULT_UART_PATH;
        this.baudrate = baudrate > 0 ? baudrate : DEFAULT_BAUDRATE;

        if (opened || device.nl_DeviceIsOpen()) {
            releaseDeviceLocked();
        }
        if (powerCtrl == null) {
            powerCtrl = new HcPowerCtrl();
        }
        powerOn();
        SystemClock.sleep(100);

        boolean ok = false;
        try {
            ok = device.nl_OpenDevice(this.uartPath, this.baudrate, this);
        } catch (RuntimeException e) {
            Log.e(TAG, "open device error", e);
        }
        if (ok) {
            opened = true;
            device.nl_SendCommand(CMD_SERIAL_TRIGGER);
            Log.i(TAG, "open success path=" + this.uartPath + " baud=" + this.baudrate);
        } else {
            opened = false;
            Log.e(TAG, "open failed path=" + this.uartPath);
            releaseDeviceLocked();
            powerOff();
        }
        return ok;
    }

    /**
     * Open the module if needed, switch to continuous decode, and hold IR on
     * until the first barcode, {@link #stopScan()} or {@link #close()}.
     * If the head does not acknowledge the trigger, power-cycle and arm once more.
     */
    public boolean startContinuousScan() {
        return startContinuousScan(uartPath, baudrate);
    }

    public boolean startContinuousScan(String uartPath, int baudrate) {
        synchronized (sessionLock) {
            scanning = false;
            oneShotEmitted = false;
            clearUartBuf();
            if (armContinuousLocked(uartPath, baudrate, false)) {
                return true;
            }
            Log.w(TAG, "IR head did not arm, power-cycle and retry");
            if (armContinuousLocked(uartPath, baudrate, true)) {
                return true;
            }
            // Port is up but the module never ACKed. Still force a trigger edge
            // so the lamp is not left dark after the click.
            if (isOpen() && forceTriggerLocked()) {
                scanning = true;
                Log.w(TAG, "IR head forced on after retries");
                return true;
            }
            scanning = false;
            holdTrigger(false);
            Log.e(TAG, "startContinuousScan failed to light IR head");
            return false;
        }
    }

    /**
     * Same arming path as {@link #startContinuousScan()}. A bare trigger pulse
     * is not enough: the head sometimes misses it and the lamp stays off.
     */
    public boolean startScan() {
        return startContinuousScan();
    }

    /**
     * @param forceReset close the port and boot the module again before arming
     * @return true only after the module ACKs the start trigger
     */
    private boolean armContinuousLocked(String uartPath, int baudrate, boolean forceReset) {
        if (forceReset || !isOpen() || !powered) {
            if (opened || device.nl_DeviceIsOpen()) {
                releaseDeviceLocked();
            }
            opened = false;
            if (!openLocked(uartPath, baudrate)) {
                Log.e(TAG, "arm failed: open forceReset=" + forceReset);
                return false;
            }
            if (forceReset) {
                SystemClock.sleep(POWER_RESET_MS);
            }
        }
        if (!waitModuleReadyLocked()) {
            return false;
        }
        boolean suffix = sendCommand(CMD_CR_SUFFIX);
        boolean reread = sendCommand(CMD_REREAD_TIMEOUT);
        boolean mode = sendCommand(CMD_CONTINUOUS_MODE);
        if (!mode && !suffix && !reread) {
            Log.w(TAG, "arm failed: module ignored setup");
            return false;
        }
        if (!fireTriggerLocked()) {
            return false;
        }
        scanning = true;
        Log.i(TAG, "IR head armed forceReset=" + forceReset);
        return true;
    }

    /** Module is booted only after it echoes a command. A live tty is not enough. */
    private boolean waitModuleReadyLocked() {
        for (int i = 1; i <= READY_PROBES; i++) {
            if (sendCommand(CMD_SERIAL_TRIGGER)) {
                return true;
            }
            Log.w(TAG, "module not ready probe=" + i);
            SystemClock.sleep(READY_PROBE_MS);
        }
        return false;
    }

    /**
     * Release then press the trigger so the head sees an edge, then require
     * the serial start command to be acknowledged.
     */
    private boolean fireTriggerLocked() {
        pulseTrigger();
        boolean serialTrig = sendCommand(CMD_START_TRIGGER);
        boolean pulse = startScanLocked();
        if (serialTrig) {
            Log.i(TAG, "trigger ack pulse=" + pulse);
            return true;
        }
        SystemClock.sleep(READY_PROBE_MS);
        pulseTrigger();
        serialTrig = sendCommand(CMD_START_TRIGGER);
        pulse = startScanLocked();
        Log.i(TAG, "trigger retry ack=" + serialTrig + " pulse=" + pulse);
        return serialTrig;
    }

    /** Last-resort edge plus start command. Keeps the pin held even if ACK is lost. */
    private boolean forceTriggerLocked() {
        sendCommand(CMD_STOP_TRIGGER);
        pulseTrigger();
        sendCommand(CMD_START_TRIGGER);
        startScanLocked();
        return powered;
    }

    private boolean startScanLocked() {
        if (!opened) {
            Log.w(TAG, "startScan ignored: device not open");
            return false;
        }
        try {
            return device.nl_StartScan();
        } catch (RuntimeException e) {
            Log.e(TAG, "startScan error", e);
            return false;
        }
    }

    public boolean stopScan() {
        synchronized (sessionLock) {
            return stopScanLocked();
        }
    }

    private boolean stopScanLocked() {
        scanning = false;
        oneShotEmitted = true;
        clearUartBuf();
        holdTrigger(false);
        if (!opened) {
            return false;
        }
        try {
            return device.nl_StopScan();
        } catch (RuntimeException e) {
            Log.e(TAG, "stopScan error", e);
            return false;
        }
    }

    public void close() {
        synchronized (sessionLock) {
            session++;
            scanning = false;
            oneShotEmitted = true;
            clearUartBuf();
            try {
                // Release the hardware trigger before tearing down the UART.
                // While a scan has produced no barcode the trigger stays held
                // and the reader thread is busy; closing the fd under it crashes.
                holdTrigger(false);
                if (opened || device.nl_DeviceIsOpen()) {
                    // Do not send #SCNTRG0 here. With the trigger still streaming,
                    // that command drains the UART until it goes quiet and never
                    // returns. Cutting the port and the power stops the engine.
                    releaseDeviceLocked();
                }
            } finally {
                opened = false;
                scanning = false;
                powerOff();
                releaseTone();
            }
        }
    }

    private void releaseDeviceLocked() {
        try {
            device.nl_CloseDevice();
        } catch (RuntimeException e) {
            Log.w(TAG, "close device failed", e);
        }
        opened = false;
    }

    public boolean isOpen() {
        return opened && device.nl_DeviceIsOpen();
    }

    public boolean isScanning() {
        return scanning && isOpen();
    }

    public boolean sendCommand(String command) {
        synchronized (sessionLock) {
            if (!opened || command == null) {
                return false;
            }
            try {
                return device.nl_SendCommand(command);
            } catch (RuntimeException e) {
                Log.e(TAG, "sendCommand error", e);
                return false;
            }
        }
    }

    public NLDeviceStream getDevice() {
        return device;
    }

    @Override
    public void actionRecv(byte[] recvBuff, int len) {
        int token = session;
        OnScanResultListener current = listener;
        if (!scanning || oneShotEmitted || current == null || recvBuff == null || len <= 0) {
            return;
        }
        String chunk = new String(recvBuff, 0, len);
        String barcode;
        synchronized (uartLock) {
            if (token != session) {
                return;
            }
            uartBuf.append(chunk);
            barcode = takeNextBarcode();
        }
        if (barcode == null || oneShotEmitted || token != session) {
            return;
        }
        oneShotEmitted = true;
        scanning = false;
        holdTrigger(false);
        playGoodReadBeep();
        if (token != session) {
            return;
        }
        byte[] out = barcode.getBytes();
        current.onScanResult(out, out.length);
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
        synchronized (powerLock) {
            if (powerCtrl == null) {
                return;
            }
            // Lines may still be high after a scan that never decoded.
            // Drop them so wakeup / power-down see a real edge on the next open.
            powerCtrl.scanTrig(0);
            powerCtrl.scanPower(0);
            powerCtrl.scanWakeup(0);
            powerCtrl.scanPwrdwn(0);
            powered = false;
            SystemClock.sleep(POWER_RESET_MS);
            powerCtrl.scanTrig(1);
            powerCtrl.scanPower(1);
            powerCtrl.scanWakeup(1);
            powerCtrl.scanPwrdwn(1);
            powerCtrl.scanTrig(0);
            powered = true;
        }
    }

    private void powerOff() {
        synchronized (powerLock) {
            if (powerCtrl == null) {
                powered = false;
                return;
            }
            try {
                powerCtrl.scanTrig(0);
                powerCtrl.scanPower(0);
                powerCtrl.scanWakeup(0);
                powerCtrl.scanPwrdwn(0);
            } catch (RuntimeException e) {
                Log.w(TAG, "power off failed", e);
            }
            powered = false;
        }
    }

    private void holdTrigger(boolean hold) {
        synchronized (powerLock) {
            if (powerCtrl == null) {
                return;
            }
            if (hold && !powered) {
                return;
            }
            powerCtrl.scanTrig(hold ? 1 : 0);
        }
    }

    /** Drive scanTrig low then high. Writing 1 again does not restart a dark head. */
    private void pulseTrigger() {
        synchronized (powerLock) {
            if (powerCtrl == null || !powered) {
                return;
            }
            powerCtrl.scanTrig(0);
            SystemClock.sleep(TRIGGER_EDGE_MS);
            powerCtrl.scanTrig(1);
        }
    }
}
