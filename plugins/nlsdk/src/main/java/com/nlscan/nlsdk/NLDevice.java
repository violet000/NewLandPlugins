package com.nlscan.nlsdk;

import android.content.Context;
import android.util.Log;

import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.NodeList;

import java.io.File;
import java.util.zip.CRC32;

import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.DocumentBuilderFactory;

/**
 * This class mainly implements all the functions of the interface NLDeviceStream
 */
public class NLDevice implements NLDeviceStream {
    private NLCommStream curCommStream;
    private byte[] recvBuffer = new byte[4 * 1024];
    private String pathName;
    final private int frameSize = 512;
    private byte[] buffer = new byte[frameSize + 64];
    private CRC32 crc32 = new CRC32();
    private boolean runable = false;
    private boolean readFlag = true;
    private Context mContext;
    private int READ_TIMEOUT = 20;        // The read timeout of the reader thread
    private int WAIT_TIMEOUT = 30;      // Send command switch timeout,usually needs to be greater than READ_TIMEOUT, so that the reading thread can be suspended
    private NLUsbListener mListener;

    private void setCommandFlag(boolean flag) {
        readFlag = flag;
    }

    private boolean getCommandFlag() {
        return readFlag;
    }

    private String TAG = "NLDevice";

    static class UpdateInfo {
        int pos;
        int length;
        String type;
    }

    private UpdateInfo[] updateInfos = new UpdateInfo[4];


    public NLDevice(DevClass classType) {

        switch (classType) {
            case DEV_CDC:
                curCommStream = new NLUsbCdc();
                break;
            case DEV_POS:
                curCommStream = new NLUsbPos();
                break;
            case DEV_COMPOSITE:
                curCommStream = new NLUsbComposite();
                break;
            case DEV_UART:
                curCommStream = new NLUartStream();
            default:
                Log.e(TAG, "USB class is error!");
                break;
        }
        for (int i = 0; i < updateInfos.length; ++i)
            updateInfos[i] = new UpdateInfo();
    }

    public NLCommStream nl_GetDevObj() {
        return curCommStream;
    }

    @Override
    public String nl_GetSdkVersion() {
        return "V1.0.2";
    }

    @Override
    public boolean nl_OpenDevice(Context context, final NLUsbListener listener) {
        if (curCommStream.open(context)) {
            mContext = context;
            curCommStream.setUsbListener(listener);
            mListener = listener;
            runable = true;
            return true;
        }
        return false;
    }

    @Override
    public boolean nl_OpenDevice(String devPathName, int baudrate, final NLUartListener listener) {
        pathName = devPathName;
        if (curCommStream.open(pathName, baudrate)) {
            runable = true;
            class RecvDataStream implements Runnable {
                public void run() {
                    while (runable) {
                        if (getCommandFlag()) {
                            int recvLen = read(recvBuffer, READ_TIMEOUT);
                            if (recvLen > 0)
                                listener.actionRecv(recvBuffer, recvLen);
                        } else {
                            try {
                                Thread.sleep(50);
                            } catch (InterruptedException e) {
                                e.printStackTrace();
                            }
                        }
                    }
                }
            }
            Thread t = new Thread(new RecvDataStream());
            t.start();
            return true;
        }
        return false;
    }


    @Override
    public void nl_CloseDevice() {
        runable = false;
        curCommStream.close(mContext);
    }

    @Override
    public boolean nl_DeviceIsOpen() {
        return curCommStream.isOpen();
    }


    @Override
    public boolean nl_GetDevStatus() {
        boolean ret;

        setRecvRouting(false);
        try {
            Thread.sleep(WAIT_TIMEOUT);
        } catch (InterruptedException e) {
            e.printStackTrace();
        }
        ret = checkHealthCommand();
        setRecvRouting(true);

        return ret;
    }

    private boolean checkHealthCommand() {
        if (!nl_DeviceIsOpen()) return false;

        byte[] data = packUnifyCommand("DEVQRY*".getBytes());
        if (data == null) return false;

        clean(20);
        if (!write(data)) return false;
        int len = readAck(recvBuffer, 0, recvBuffer.length, 50, 10, true);

        if (len < 16) return false;
        if (recvBuffer[0] != 2) return false;
        for (int i = 1; i < 13; ++i)
            if (data[i] != recvBuffer[i]) return false;
        if (recvBuffer[len - 3] != 6 || recvBuffer[len - 2] != 0x3b || recvBuffer[len - 1] != 3)
            return false;
        return (recvBuffer[13] == '0');
    }


    /**
     * Obtain device information: execute the return result of the unified instruction set QRYSYS command
     *
     * @return Device Information
     */
    @Override
    public String nl_GetDeviceInfo() {
        String stringInfo;

        setRecvRouting(false);
        try {
            Thread.sleep(WAIT_TIMEOUT);
        } catch (InterruptedException e) {
            e.printStackTrace();
        }
        stringInfo = getDeviceInformationCommand();
        setRecvRouting(true);

        return stringInfo;
    }


    private String getDeviceInformationCommand() {
        if (!nl_DeviceIsOpen()) return null;
        byte[] data = packUnifyCommand("QRYSYS".getBytes());
        if (data == null) return null;

        clean(20);
        if (!write(data)) return null;
        int len = readAck(recvBuffer, 0, recvBuffer.length, 300, 50, true);

        if (len < 16) return null;
        if (recvBuffer[0] != 2) return null;
        for (int i = 1; i < 13; ++i)
            if (data[i] != recvBuffer[i]) return null;
        if (recvBuffer[len - 3] != 6 || recvBuffer[len - 2] != 0x3b || recvBuffer[len - 1] != 3)
            return null;
        return new String(recvBuffer, 13, len - 13 - 3);
    }


    public boolean nl_StartScan() {
        byte[] data = {0x01, 0x54, 0x04};

        return write(data);


    }


    @Override
    public boolean nl_StopScan() {
        return nl_SendCommand("#SCNTRG0");
    }

    @Override
    public boolean nl_RestartDevice() {
        return nl_SendCommand("#REBOOT");
    }

    /**
     * Send a unified command and receive a command return, calling this method in a synchronous manner
     *
     * @param command UCS command
     * @return success or failure
     */
    @Override
    public boolean nl_SendCommand(String command) {
        boolean ret;

        setRecvRouting(false);
        try {
            Thread.sleep(WAIT_TIMEOUT);
        } catch (InterruptedException e) {
            e.printStackTrace();
        }
        ret = setConfigCommand(command);
        setRecvRouting(true);
        return ret;
    }

    private boolean setConfigCommand(String command) {
        if (!nl_DeviceIsOpen()) return false;
        byte[] data = packUnifyCommand(command.getBytes());
        if (data == null)
            return false;
        clean(20);
        if (!write(data))
            return false;

        int commandNum = 0;
        for (byte separate : data) {
            if (separate == 0x3b) commandNum++;
        }

        int len = readAck(recvBuffer, 0, data.length + commandNum, 200, 10, true);
        if (len != data.length + commandNum || len < 6) return false;

        data[0] = 2;
        if (commandNum == 1) {
            if (recvBuffer[len - 1] != 3) return false;
            if (recvBuffer[len - 2] != 0x3b) return false;
            if (recvBuffer[len - 3] != 6) return false;
            for (int i = 0; i < len - 3; ++i)
                if (data[i] != recvBuffer[i]) return false;
        } else {
            int commandAck = 0;
            for (byte byteAck : recvBuffer) {
                if (byteAck == 0x3b) commandAck++;
            }
            if (commandAck != commandNum) return false;

            int bytePosition = 0;
            for (byte separateAnalysis : recvBuffer) {
                if (separateAnalysis == 0x3b && recvBuffer[bytePosition - 1] != 6) return false;
                bytePosition++;
            }
            if (recvBuffer[len - 1] != 3) return false;
        }
        return true;
    }

    /**
     * Query the current settings of UCS command,
     * and only support the query of a single command.
     * For example, the SCNMOD* query returns the result as SCNMOD0
     *
     * @param command UCS command
     * @return Returns the response to the current query command
     */
    @Override
    public String nl_ReadDevCfg(String command) {
        String retString;
        setRecvRouting(false);
        try {
            Thread.sleep(WAIT_TIMEOUT);
        } catch (InterruptedException e) {
            e.printStackTrace();
        }

        retString = getConfigCommand(command);
        setRecvRouting(true);
        return retString;
    }


    private String getConfigCommand(String command) {
        if (!nl_DeviceIsOpen())
            return null;
        byte[] data = packUnifyCommand(command.getBytes());
        if (data == null)
            return null;
        //clean(20);
        if (!write(data))
            return null;
        int timeout = data.length * 2;
        int len = readAck(recvBuffer, 0, recvBuffer.length, timeout, 10, true);
        if (len < 6)
            return null;

        // Determine whether the packet has the end suffix Suffix ";<ETX ETX>" (HEX: 3B 03), consisting of 2 characters
        if (recvBuffer[len - 1] != 3)
            return null;
        if (recvBuffer[len - 2] != 0x3b)
            return null;

        // Response result judgment
        // <ACK> (HEX: 06 ) Successful operation
        // <NAK> (HEX: 15 ) The value of the data is not in the supported range
        // <ENQ> (HEX: 05 ) setting class or function does not exist
        if (recvBuffer[len - 3] != 6)
            return null;
        /* Return the query content after unpacking (remove the 7 bytes of the header and the 3 bytes of the tail) */
        return new String(recvBuffer, 7, len - (3 + 7));
    }

    private int setConfigBulk(String command) {
        int ret;

        setRecvRouting(false);
        try {
            Thread.sleep(WAIT_TIMEOUT);
        } catch (InterruptedException e) {
            e.printStackTrace();
        }
        ret = setConfigBulkCommand(command);
        setRecvRouting(true);
        return ret;
    }

    private int setConfigBulkCommand(String command) {
        if (!nl_DeviceIsOpen())
            return -1;
        setRecvRouting(false);
        try {
            Thread.sleep(WAIT_TIMEOUT);
        } catch (InterruptedException e) {
            e.printStackTrace();
        }
        byte[] data = packUnifyCommand(command.getBytes());
        if (data == null)
            return -2;
        //clean(20);
        if (!write(data))
            return -3;
        Log.i(TAG, "CommList:" + command);

        int timeout = data.length * 2 + 200;
        int len = readAck(recvBuffer, 0, recvBuffer.length, timeout, 10, true);
        if (len < 6)
            return -4;

        // Determine whether the packet has the end suffix Suffix ";<ETX ETX>" (HEX: 3B 03), consisting of 2 characters
        if (recvBuffer[len - 1] != 3)
            return -5;
        if (recvBuffer[len - 2] != 0x3b)
            return -6;
        return 1;
    }


    @Override
    public int nl_UpdateKernelDevice(final byte[] filedata, NLUpdateListner listner) {
        NLCommStream.DevClass firmwareType;    // 0->SOC products；1->MCU products
        final int McuMd5Prefix = 0x89abcdef;

        if (!nl_DeviceIsOpen()) return NLError.ERROR_INVALID_PARAMS;

        int error = NLError.ERROR_FIRMWARE_FILE;
        final int len = filedata.length;
        if (len < 600) return error;
        int tpos;
        int total = 0;
        UpdateInfo info;

        // SOC device update
        if (McuMd5Prefix != readLE(filedata, 0)) {
            firmwareType = NLCommStream.DevClass.DEV_SOC;
            tpos = len - 368;
            for (int i = 0; i < 4; ++i, tpos += 76) {
                final int offset = readLE(filedata, tpos);
                final int datalen = readLE(filedata, tpos + 4);
                final int type = readLE(filedata, tpos + 8);
                final int target = readLE(filedata, tpos + 40);
                if (datalen == 0) break;
                String str;
                switch (type) {
                    case 0x6E72656B:
                        str = "kern";
                        break;
                    case 0x746F6F62:
                        str = "boot";
                        break;
                    case 0x6c707061:
                        str = "appl";
                        break;
                    case 0x68616c66:
                        str = String.format("flah:%s", target);
                        break;
                    default:
                        return error;
                }

                if (offset < 0 || offset > len) return error;
                if (datalen < 0 || datalen > len) return error;
                if (offset + datalen > len) return error;
                info = updateInfos[total++];
                info.pos = offset;
                info.length = datalen;
                info.type = str;
            }
        } // MCU device update
        else {
            firmwareType = NLCommStream.DevClass.DEV_MCU;
            tpos = 0x5c;
            if (filedata[tpos] != 1)
                return error;

            info = updateInfos[total++];
            info.pos = readLE(filedata, tpos + 8);
            info.length = readLE(filedata, tpos + 4);
            info.type = "kern";

            tpos += 0x70;
            if (filedata[tpos] == 1) {
                info = updateInfos[total++];
                info.pos = readLE(filedata, tpos + 12);
                info.length = readLE(filedata, tpos + 8);
                info.type = "flah";
            }
        }
        if (total == 0) return error;

        setRecvRouting(false);
        try {
            Thread.sleep(WAIT_TIMEOUT);
        } catch (InterruptedException e) {
            e.printStackTrace();
        }
        listner.curProgress("updateFirmware", NLUpdateState.STATE_PAESE_FORMATE, 100);

        error = updateDevice(filedata, firmwareType, updateInfos, total, listner);
        if (error != NLError.ERROR_SUCCESS)
            setParam("@Exit");
        setRecvRouting(true);
        return error;
    }

    @Override
    public int nl_WriteCfgToDev(File file) {
        if (!nl_DeviceIsOpen()) return -1;

        try {
            DocumentBuilderFactory dbFactory = DocumentBuilderFactory.newInstance();
            DocumentBuilder dbBuilder = dbFactory.newDocumentBuilder();
            Document doc = dbBuilder.parse(file);
            NodeList nList = doc.getElementsByTagName("Command");

            String prexTag = "";
            String command;
            StringBuilder sbCommandList = new StringBuilder();
            StringBuilder sbCommCommandList = new StringBuilder();  // Communication related command list
            boolean firstCmd = true;

            for (int i = 0; i < nList.getLength(); i++) {
                // Get every configuration command
                Element CommandElement = (Element) nList.item(i);

                String value = CommandElement.getAttribute("Value");
                String name = CommandElement.getAttribute("CommandName");
                //Log.i(TAG, "name:" + name + " value:" + value );

                String Tag = name.substring(0, 3);
                if (Tag.equals(prexTag)) {
                    command = "," + name.substring(3) + value;
                } else {
                    if (firstCmd)
                        command = name + value;
                    else
                        command = ";" + name + value;
                    prexTag = Tag;
                }
                firstCmd = false;
                if (name.equals("INTERF") || name.equals("AUTOUR") || name.startsWith("232")) {
                    sbCommCommandList.append(command);
                } else {
                    sbCommandList.append(command);
                }

                // Assume that the length of each batch configuration command does not exceed 200 characters
                if (sbCommandList.toString().length() > 200) {
                    String strCommanList = "@" + sbCommandList.toString() + ";";
                    if (setConfigBulk(strCommanList) < 0)
                        return -1;
                    sbCommandList = new StringBuilder();
                    prexTag = "";
                    firstCmd = true;
                }
            }

            // Send the remaining configuration commands
            String strCommanList = "@" + sbCommandList.toString() + ";";
            if (setConfigBulk(strCommanList) < 0)
                return -1;

            // Send communication related commands
            if (sbCommCommandList.length() > 0) {
                String strCommList = "@" + sbCommCommandList.toString().substring(1) + ";";
                setConfigBulk(strCommList);
                return 0;
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
        return 1;
    }

    private int read(byte[] dst, int timeout) {
        if (!nl_DeviceIsOpen()) return 0;
        int pos = 0, length = dst.length;

        int ret = 0;
        while (true) {
            if (timeout < 10) timeout = 10;
            int len = curCommStream.readPacket(dst, pos, length, timeout);
            if (len <= 0) break;
            ret += len;
            pos += len;
            length -= len;
            if (pos >= dst.length) break;
            if (length == 0) break;
            timeout = 20;
        }
        return ret;
    }

    /**
     * Get the length of the current image on the device
     * Response data format：IMGGWH752W480H或IMGGWH1280W800H
     *
     * @return The length of the current image on the device, 0 means the acquisition failed
     */
    @Override
    public int[] nl_GetPicSize() {
        int[] wh = new int[2];
        String imgSizeAck = nl_ReadDevCfg("IMGGWH");

        if (imgSizeAck != null) {
            String imgSizeWH = imgSizeAck.substring(6);
            int pointW = imgSizeWH.indexOf("W");
            int pointH = imgSizeWH.indexOf("H");
            String imgSizeW = imgSizeWH.substring(0, pointW);
            wh[0] = Integer.valueOf(imgSizeW, 10);
            String imgSizeH = imgSizeWH.substring(pointW + 1, pointH);
            wh[1] = Integer.valueOf(imgSizeH, 10);
        }
        return wh;
    }

    @Override
    public boolean nl_GetPicData(byte[] ImgBuff, int imgSize, NLTransImgListner listner) {
        final String command = "IMGGET0T0R0F";
        int recvLen;

        if (!nl_DeviceIsOpen()) return false;
        setRecvRouting(false);
        try {
            Thread.sleep(WAIT_TIMEOUT);
        } catch (InterruptedException e) {
            e.printStackTrace();
        }
        byte[] data = packUnifyCommand(command.getBytes());
        if (data == null)
            return false;

        // package response header (equal to 2 bytes 3b 03 after sending command removed) + image length field (8 bytes) + image content + suffix
        recvLen = (data.length - 2) + 8 + imgSize + 3;
        byte[] recvbuf = new byte[recvLen];
        clean(20);
        if (!write(data))
            return false;

        int len;
        int pos = 0;
        listner.curProgress(recvLen);

        while (pos < recvLen) {
            len = curCommStream.readPacket(recvbuf, pos, Math.min(4096, recvLen - pos), 100);
            pos += len;
            listner.curProgress((pos * 100) / recvLen);
            if (len == 0)
                break;
        }
        listner.curProgress(100);
        // Judging whether the header is correct
        for (int i = 1; i < data.length - 2; ++i)
            if (data[i] != recvbuf[i])
                return false;

        // Determine whether the end byte is correct
        if (recvbuf[pos - 1] != 3)
            return false;
        if (recvbuf[pos - 2] != 0x3b)
            return false;
        if (recvbuf[pos - 3] != 6)
            return false;

        System.arraycopy(recvbuf, data.length + 8, ImgBuff, 0, imgSize);
        setRecvRouting(true);
        return true;
    }

    /* ============================= private =====================================================*/

    /**
     * Whether to enable receiving USB data routing, enable when ready to receive codeword data,
     * must be disabled when sending configuration commands and firmware updates
     *
     * @param enable true：enable, the USB receive content will be thrown from the NLUsbListener.actionUsbRecv callback interface；
     *               false：disabled，
     */
    private void setRecvRouting(boolean enable) {
        setCommandFlag(enable);
        curCommStream.setReadAck(enable);
    }

    private void changeBaudrate(int baudrate) {
        curCommStream.close(mContext);
        curCommStream.open(pathName, baudrate);
    }

    private boolean write(byte[] src, @SuppressWarnings("SameParameterValue") int pos, int len) {
        return curCommStream.writePacket(src, pos, len);
    }


    private boolean write(byte[] src) {
        return write(src, 0, src.length);
    }


    /**
     * If the data cannot be read in continuous time, the task clears the buffer
     *
     * @param milliseconds continuous time
     */
    private void clean(@SuppressWarnings("SameParameterValue") int milliseconds) {
        byte[] buffer = new byte[8 * 1024];
        while (true) {
            if (read(buffer, milliseconds) == 0) break;
        }
    }

    /**
     * Receive data, if it is a unified command response data packet,
     * then according to the packet end mark (: ";<ETX ETX>" (HEX: 3B 03), consisting of 2 characters)
     *
     * @param dst        receive response buffer
     * @param pos        buffer offset
     * @param length     receive buffer length
     * @param timeout    Single packet receive timeout
     * @param interval   Subpacket Transmission Interval
     * @param isUnifyCmd Whether is UCS command
     * @return Correct response packet length, 0 means error
     */
    private int readAck(byte[] dst, @SuppressWarnings("SameParameterValue") int pos, int length, int timeout, int interval, boolean isUnifyCmd) {
        if (!nl_DeviceIsOpen()) return 0;
        final int minTimeout = 20;
        timeout = Math.max(minTimeout, timeout);
        interval = Math.max(minTimeout, interval);

        int ret = 0;
        while (true) {
            int len = curCommStream.readPacket(dst, pos, length, timeout);

            if (len <= 0)
                break;
            ret += len;
            pos += len;
            length -= len;
            // 判断统一指令应答数据包接收完成
            if (isUnifyCmd && (ret > 2) && (dst[pos - 1] == 3) && (dst[pos - 2] == 0x3b))
                break;
            if (pos >= dst.length)
                break;
            if (length == 0)
                break;
            timeout = interval;
        }
        return ret;
    }

    private static byte[] packUnifyCommand(byte[] data) {
        if (data == null || data.length == 0) return null;
        int len = data.length;
        int dpos = 0;
        if (data[len - 1] == (byte) ';') --len;
        if (data[0] == (byte) '@' || data[0] == (byte) '#') {
            dpos = 1;
            --len;
        }
        byte[] buffer = new byte[9 + len];
        buffer[0] = 0x7e;
        buffer[1] = 1;
        buffer[2] = 0x30;
        buffer[3] = 0x30;
        buffer[4] = 0x30;
        buffer[5] = 0x30;
        buffer[6] = (byte) '#';
        if (dpos != 0) buffer[6] = data[0];
        System.arraycopy(data, dpos, buffer, 7, len);
        buffer[len + 7] = (byte) ';';
        buffer[len + 8] = 0x03;
        return buffer;
    }

    static private void writeBE(byte[] str, int pos, int value) {
        str[pos] = (byte) (value >>> 24);
        str[pos + 1] = (byte) (value >>> 16);
        str[pos + 2] = (byte) (value >>> 8);
        str[pos + 3] = (byte) value;
    }

    static private int readBE(byte[] str, int pos) {
        // return ((str[pos] & 0xFF) << 24) | ((str[pos + 1] & 0xFF) << 16) |  ((str[pos + 2] & 0xFF) << 8) | (str[pos + 3] & 0xFF);
        return ((str[pos] & 0xFF) << 24) | ((str[pos + 1] & 0xFF) << 16) | ((str[pos + 2] & 0xFF) << 8);
    }

    static private int readLE(byte[] str, int pos) {
        return ((str[pos + 3] & 0xFF) << 24) | ((str[pos + 2] & 0xFF) << 16) | ((str[pos + 1] & 0xFF) << 8) | (str[pos] & 0xFF);
    }

    private int getCRC32(byte[] str, @SuppressWarnings("SameParameterValue") int pos, int len) {
        crc32.reset();
        crc32.update(str, pos, len);
        return (int) crc32.getValue();
    }

    private boolean setParam(String str, byte[] result) {
        if (result != null) result[0] = 0;
        //final int  timeout = 3000;
        final int len = str.length();
        if (len + 8 > buffer.length)
            return false;

        buffer[0] = 0x02;
        buffer[1] = 0x05;
        buffer[2] = (byte) ((len >>> 8) & 0xFF);
        buffer[3] = (byte) (len & 0xFF);
        for (int i = 0; i < len; ++i)
            buffer[4 + i] = (byte) (str.charAt(i));
        writeBE(buffer, len + 4, getCRC32(buffer, 0, 4 + len));

        clean(0);
        if (!write(buffer, 0, len + 8))
            return false;

        recvBuffer[0] = 0;
        recvBuffer[1] = 0;

        // The device replies with 9 bytes, but older firmware may reply with 10 bytes
        int rlen = readAck(recvBuffer, 0, 10, 2000, 20, false);
        if (rlen != 9 && rlen != 10)
            return false;
        if (recvBuffer[0] != 2 || recvBuffer[1] != 5 || recvBuffer[2] != 0)
            return false;

        final int datalen = recvBuffer[3];
        if (datalen == 0 || datalen > rlen - 8)
            return false;

        final int crc32 = getCRC32(recvBuffer, 0, datalen + 4);
        if ((crc32 & 0xffffff00) != readBE(recvBuffer, datalen + 4))
            return false;
        final byte response = recvBuffer[4];
        if (result != null) result[0] = response;
        return response == 0x30;
    }

    private boolean setParam(String str) {
        return setParam(str, null);
    }

    private boolean readExactly(byte wanted) {
        final int timeout = 3000;
        return readExactly(wanted, timeout);
    }

    private boolean readExactly(byte wanted, int timeout) {
        recvBuffer[0] = 0;
        if (readAck(recvBuffer, 0, 1, timeout, 0, false) < 1)
            return false;
        return recvBuffer[0] == wanted;
    }

    private int readExactlyEx(byte wanted, int timeout) {
        recvBuffer[0] = 0;
        if (readAck(recvBuffer, 0, 1, timeout, 0, false) < 1)
            return -1;
        if (recvBuffer[0] == wanted)
            return 1;
        else
            return 0;
    }


    private int updateDevice(byte[] data, NLCommStream.DevClass firmwareType, UpdateInfo[] updateInfos, int total, NLUpdateListner listner) {
        final int error = NLError.ERROR_COMMUNICATION;
        final byte[] quotes = {0x3f}; // '?'
        final byte[] cmdUpgrade = {0x7e, 0, 0, 0x09, 0x7e, 0x75, 0x70, 0x47, 0x72, 0x61, 0x64, 0x65, 0x7e, (byte) 0xa6};   // For SOC devices
        final byte[] cmdupDate = {0x7e, 0, 0, 0x08, 0x7e, 0x75, 0x70, 0x44, 0x61, 0x74, 0x65, 0x7e, (byte) 0xc6};         // For MCU devices
        final byte[] quotesProbe = {0x2a};   // '*'
        byte[] updateStar;
        byte[] result = new byte[1];

        // 1. Ready to enter update mode
        if (firmwareType == NLCommStream.DevClass.DEV_SOC) {
            updateStar = cmdUpgrade;
        } else {
            updateStar = cmdupDate;
        }
        write(updateStar);
        readExactly((byte) 0x06);

        listner.curProgress("updateDevice", NLUpdateState.STATE_HANDSHAKE, 100);

        // For MCU devices, after sending "~upDate~", the device will restart and enter Boot, which is equivalent to unplugging the device.
        if (!curCommStream.getClass().equals(NLUartStream.class) && (firmwareType == NLCommStream.DevClass.DEV_MCU)) {
            //curCommStream.close(mContext);
            try {
                Thread.sleep(1000);
            } catch (InterruptedException e) {
                e.printStackTrace();
            }

			/* Since the MCU restarts will cause the USB to be unplugged,
			after open, the system will pop up a permission confirmation dialog box and return failure，
			Here, use the delaying 2s method to reopen again,
			which requires the user to confirm that the permission is enabled within 2s!*/
            if (!curCommStream.open(mContext)) {
                try {
                    Thread.sleep(2000);
                } catch (InterruptedException e) {
                    e.printStackTrace();
                }

                if (!curCommStream.open(mContext))
                    return NLError.ERROR_DEVICE_NOT_EXIST;
            }
            curCommStream.setUsbListener(mListener);
            listner.curProgress("updateDevice", NLUpdateState.STATE_RECONNECTED, 100);
        }

        for (int idx = 0; idx < total; ++idx) {
            final UpdateInfo info = updateInfos[idx];
            final int datalen = info.length;
            int pos = info.pos;

            // 2. enter update mode
            if (curCommStream.getClass().equals(NLUartStream.class)) {
                changeBaudrate(9600);
            }

            for (int i = 0; ; ) {
                try {
                    Thread.sleep(20);
                } catch (InterruptedException e) {
                    e.printStackTrace();
                }

                if (!write(quotes)) return error;
                recvBuffer[0] = 0;
                if (readAck(recvBuffer, 0, 1, 100, 0, false) == 0) {
                    if (++i >= 20)
                        return error;
                    continue;
                }
                if (recvBuffer[0] == '<')
                    break;
            }
            listner.curProgress(info.type, NLUpdateState.STATE_ENTER_UPDATE, 100);

            // 3.  switch baud rate (optional)
            if (curCommStream.getClass().equals(NLUartStream.class)) {
                if (!setParam("#COMM:115200,8,0,1", result)) {
                    if (result[0] != '0')
                        return error;
                }
                changeBaudrate(115200);

                // Send a byte '*' directly after waiting 20 milliseconds, and the device will respond with a '*'
                try {
                    Thread.sleep(20);
                } catch (InterruptedException e) {
                    e.printStackTrace();
                }

                for (int i = 0; i < 20; i++) {
                    if (++i >= 20)
                        return error;
                    if (!write(quotesProbe)) return error;
                    recvBuffer[0] = 0;
                    if (readAck(recvBuffer, 0, 1, 100, 0, false) == 0)
                        continue;
                    if (recvBuffer[0] == '*')
                        break;
                }
                listner.curProgress(info.type, NLUpdateState.STATE_SERIAL_CHANGE, 100);
            }


            // 4. Set update block information
            final int frames = (datalen + frameSize - 1) / frameSize;
            if (!setParam(String.format("!DataLens:%s", datalen)))
                return error;
            if (!setParam(String.format("!FileType:%s", info.type)))
                return error;
            if (!setParam(String.format("!FrameSize:%s", frameSize)))
                return error;

            // 5. send update block type
            if (!setParam(String.format("!Frames:%s", frames)))
                return error;

            listner.curProgress(info.type, NLUpdateState.STATE_SET_PARAM, 100);

            // 6. start Update
            boolean erased = false;
            if (!setParam(">Start", result)) {
                if (result[0] != 0x34)
                    return error;
                if (!setParam(">Erase"))
                    return error;
                erased = true;
                for (; ; ) {
                    recvBuffer[0] = 0;
                    if (readAck(recvBuffer, 0, 1, 4000, 0, false) == 0)
                        return error;
                    if (recvBuffer[0] == '.')
                        continue;
                    if (recvBuffer[0] != ';')
                        return error;
                    break;
                }
            }

            // 7. send data
            int sendLen = 0;
            for (int i = 0, remain = datalen; i < frames; ++i) {
                buffer[0] = 0x02;

                final int sendbytes = Math.min(remain, frameSize);
                System.arraycopy(data, pos, buffer, 1, sendbytes);
                pos += sendbytes;
                sendLen += sendbytes;
                remain -= sendbytes;

                final int tsize = frameSize + 1;
                for (int j = sendbytes + 1; j < tsize; ++j) buffer[j] = 0;

                writeBE(buffer, tsize, getCRC32(buffer, 0, tsize));

                /* Send content, return '*' is normal, '!' resend 3 times if receiving error, and exit if 3 times are not successful.*/
                int j;
                for (j = 0; j < 3; j++) {
                    if (!write(buffer, 0, frameSize + 5))
                        continue;
                    int ret = readExactlyEx((byte) '*', 1000);    // Received send success reply
                    if (ret < 0)     // The timeout return indicates that the receiving communication has been destroyed, and the upgrade is terminated
                        return error;
                    else if (ret > 0) // If the comparison is correct, it means that the sending and receiving are correct
                        break;
                }
                if (j >= 3)
                    return error;
                listner.curProgress(info.type, NLUpdateState.STATE_SEND_DATA, (sendLen * 100) / datalen);

            }
            listner.curProgress(info.type, NLUpdateState.STATE_WAIT_UPDATE, 100);
            if (!readExactly((byte) '*'))
                return error;
            final int factor = info.type.startsWith("flah") ? 500 : 50;
            final int timeout = (erased ? 0 : (datalen / 1024 * factor)) + 5000;
            if (!readExactly((byte) '^', timeout))
                return error;
            listner.curProgress(info.type, NLUpdateState.STATE_UPDATE_COMPLETE, 100);
            if (firmwareType == NLCommStream.DevClass.DEV_SOC) {
                if (idx + 1 < total)
                    setParam("@NextDown");
                else
                    setParam("@Exit");
            }
        }
        listner.curProgress("END update", NLUpdateState.STATE_UPDATE_COMPLETE, 100);
        return NLError.ERROR_SUCCESS;
    }
}
