package com.nlscan.nlsdk;

import android.content.Context;

import java.io.File;


/**
 * It stipulates the calling interface of using USB CDC, HID, KBW composite device and UART serial port to communicate under the Android system.
 * The user performs various operations on the specified device through this interface.
 * 2020/6/14  beta1 Added firmware upgrade function.
 * 2020/7/2   beta2 Increase the physical serial port communication function.
 * 2020/7/22  v1.00.00 Added the function of obtaining device images; optimized the reading process, and no longer required the user to call the setRecvRouting interface.
 */
public interface NLDeviceStream {

    /**
     * USB communication interface type, DEV_SUREPOS is not supported yet.
     * DEV_CDC          usb virtual serial port
     * DEV_POS          USB pos interface
     * DEV_COMPOSITE    Composite device of USB keyboard and POS
     * DEV_SUREPOS      IBM SurePos interface
     * DEV_UART			physical serial port
     */
    enum DevClass {DEV_CDC, DEV_POS, DEV_COMPOSITE, DEV_SUREPOS, DEV_UART}

    /**
     * Firmware update status
     */
    enum NLUpdateState {
        STATE_PAESE_FORMATE,
        STATE_HANDSHAKE,
        STATE_RECONNECTED,
        STATE_ENTER_UPDATE,
        STATE_SERIAL_CHANGE,
        STATE_SET_PARAM,
        STATE_SEND_DATA,
        STATE_WAIT_UPDATE,
        STATE_UPDATE_COMPLETE
    }

    /**
     * This interface is used as a listener interface for system USB events, and is used to monitor USB unplugging events
     */
    interface NLUsbListener {
        /**
         * Notify the application when a USB device unplugging action is detected
         *
         * @param event 1:USB device plugged in,   0:Unplug the USB device
         */
        void actionUsbPlug(int event);


        /**
         * Notify the application when the communication port receives data
         *
         * @param RecvBuff receive buffer
         * @param len      buffer size
         */
        void actionUsbRecv(byte[] RecvBuff, int len);
    }

    /**
     * This interface is used as the interface for receiving data from the serial port.
     * When the serial port receives data, it will call back the method of this interface.
     */
    interface NLUartListener {
        void actionRecv(byte[] RecvBuff, int len);
    }


    /**
     * Monitor the progress of transferring images
     */
    interface NLTransImgListner {
        void curProgress(int percent);
    }

    /**
     * Monitor download progress
     */
    interface NLUpdateListner {

        /**
         * Report firmware update progress information
         *
         * @param type    boot:bootloader   kernel：kernel code    flash：other configuration files
         * @param state   Upgrade status indication, each type of download contains 5 different stages
         * @param percent Indicates the percentage of completion in each state
         */
        void curProgress(String type, NLUpdateState state, int percent);
    }

    /**
     * @return SDK version number
     */
    String nl_GetSdkVersion();

    /**
     * The USB device opens the interface, and you must have read and write permissions to the USB device node before calling.
     * When the device that needs to be opened is DEV_COMPOSITE, the device will be notified to perform
     * Communication port configuration
     *
     * @param context  The Android context is used to enumerate devices
     * @param listener System USB event listener
     * @return true or false
     */
    boolean nl_OpenDevice(Context context, NLUsbListener listener);


    /**
     * @return Returns the stream object created by the stream
     */
    NLCommStream nl_GetDevObj();

    /**
     * The serial device opens the interface
     *
     * @param pathName Serial device name like：/dev/ttys0
     * @param baudrate Serial baud rate
     * @param listener Serial port receiving event monitoring
     * @return Whether the serial port is opened successfully
     */
    boolean nl_OpenDevice(String pathName, int baudrate, NLUartListener listener);

    /**
     * turn off the device
     */
    void nl_CloseDevice();

    /**
     * Determine whether the device is turned on
     *
     * @return true or false
     */
    boolean nl_DeviceIsOpen();

    /**
     * Determine whether the device is normal
     *
     * @return true or false
     */
    boolean nl_GetDevStatus();

    /**
     * Obtaining device information: the return result of the unified instruction set QRYSYS instruction
     *
     * @return true or false
     */
    String nl_GetDeviceInfo();

    /**
     * The device starts to read the code
     *
     * @return true or false
     */
    boolean nl_StartScan();

    /**
     * The device stops reading codes
     *
     * @return true or false
     */
    boolean nl_StopScan();

    /**
     * Restart the device
     *
     * @return true or false
     */
    boolean nl_RestartDevice();

    /**
     * Send a single setting command Example: setConfig("128ENA1") The setting code will not be saved when power off
     * If you need to set the code to still take effect after power off, you can add the character "@" before the comman,
     * like setConfig("@128ENA1")
     *
     * @param command USC command
     * @return true or false
     */
    boolean nl_SendCommand(String command);

    /**
     * Query the current settings of the unified command, and only support the query of a single command.
     * For example, the SCNMOD* query returns the result as SCNMOD0
     *
     * @param command USC command
     * @return Returns the response to the current query command
     */
    String nl_ReadDevCfg(String command);

    /**
     * Update the firmware of the module head, and the firmware upgrade package will contain different contents according to the customer's requirements.
     * There are two formats of firmware, one is SOC type, and the other is MCU type. Besides the different file formats, the upgrade process is not the same.
     * Refer to the corresponding firmware upgrade instruction manual for details. Special attention should be paid to the action that MCU devices will reboot
     * during the update process when using the USB interface for communication, which will cause the Android system to detect the USB plug event,
     * which will require re-authorization. Of course, you can use the system certificate to sign applications using this SDK to avoid re-authorization.
     * But here we just call the delay function so that the operator has enough time to confirm.
     *
     * @param fireware Firmware Content Buffer
     * @param listner  Monitor progress listener for updating firmware
     * @return Error types described in{class NLError}
     */
    int nl_UpdateKernelDevice(byte[] fireware, NLUpdateListner listner);

    /**
     * Update the module header configuration. The configuration file of the device usually contains multiple pieces of configuration information.
     * After the configuration is sent to the device, it takes a long time to execute
     *
     * @param f Batch configuration file handle in xml format
     * @return >0:update completed;<0:update failed ; =0 the update was successful and the port switching was performed
     */
    int nl_WriteCfgToDev(File f);


    /**
     * Get the length information (length, width) of the current image on the device
     * Response data format:IMGGWH752W480H或IMGGWH1280W800H
     *
     * @return The height and width of the current image on the device
     */
    int[] nl_GetPicSize();

    /**
     * Obtain the current image of the device according to the method of obtaining the image in the unified instruction set.
     * Currently, this interface only supports obtaining the image in the original size and in bmp format
     *
     * @param ImgBuff Receive the acquired image cache
     * @param imgSize image cache size
     * @return whether the image was successfully fetched from the device
     */
    boolean nl_GetPicData(byte[] ImgBuff, int imgSize, NLTransImgListner listner);
}

