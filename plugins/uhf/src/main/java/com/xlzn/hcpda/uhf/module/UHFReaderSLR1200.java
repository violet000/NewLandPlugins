package com.xlzn.hcpda.uhf.module;

import android.os.SystemClock;
import android.util.Log;

import com.xlzn.hcpda.uhf.UHFReader;
import com.xlzn.hcpda.uhf.analysis.UHFProtocolAnalysisBase;
import com.xlzn.hcpda.uhf.entity.SelectEntity;
import com.xlzn.hcpda.uhf.entity.UHFReaderResult;
import com.xlzn.hcpda.uhf.entity.UHFTagEntity;
import com.xlzn.hcpda.uhf.enums.InventoryModeForPower;
import com.xlzn.hcpda.uhf.interfaces.IBuilderAnalysis;
import com.xlzn.hcpda.uhf.interfaces.IUHFProtocolAnalysis;
import com.xlzn.hcpda.uhf.interfaces.IUHFReader;
import com.xlzn.hcpda.utils.DataConverter;
import com.xlzn.hcpda.utils.LoggerUtils;

import java.util.List;

public class UHFReaderSLR1200 extends UHFReaderSLRBase implements IUHFReader {
    private String TAG = "UHFReaderSLR1200";
    private boolean isTid = false;
    private boolean isMoreTag = false;
    private boolean isLedKLV = false;
    private PowerSavingModeInventoryThread inventoryThread = null;
    private FastModeInventoryThread fastModeInventoryThread = null;
    private UHFCheckCodeErrorCallback uhfCheckCodeErrorCallback = new UHFCheckCodeErrorCallback();

    public UHFReaderSLR1200(IUHFProtocolAnalysis uhfProtocolAnalysisSLR, IBuilderAnalysis builderAnalysisSLR) {
        super(uhfProtocolAnalysisSLR, builderAnalysisSLR);
        uhfProtocolAnalysisSLR.setCheckCodeErrorCallback(uhfCheckCodeErrorCallback);
    }

    @Override
    public UHFReaderResult<Boolean> setInventoryKLVLED(boolean led) {
        this.isLedKLV = led;
        return new UHFReaderResult<Boolean>(UHFReaderResult.ResultCode.CODE_SUCCESS, "", true);
    }

    @Override
    public UHFReaderResult<Boolean> setInventoryTid(boolean isTid) {
//        this.isTid = true;
        this.isTid = isTid;
        return new UHFReaderResult<Boolean>(UHFReaderResult.ResultCode.CODE_SUCCESS, "", true);
    }

    @Override
    public UHFReaderResult<Boolean> getInventoryTidModel() {

        return new UHFReaderResult<Boolean>(UHFReaderResult.ResultCode.CODE_SUCCESS, "", isTid);
    }

    @Override
    public UHFReaderResult<Boolean> startInventory(SelectEntity selectEntity) {
        uhfProtocolAnalysisSLR.cleanTagInfo();
        isMoreTag = UHFReader.isMoreTag;
        isLedKLV = UHFReader.isLedKLV;
        LoggerUtils.d(TAG, "快速模式，  isMoreTag = " +isMoreTag );
        if (InventoryMode == InventoryModeForPower.POWER_SAVING_MODE) {
            //普通模式
            startPowerSavingModeInventoryThread(selectEntity);
            return new UHFReaderResult(UHFReaderResult.ResultCode.CODE_SUCCESS, "", true);
        } else {
            //以下是快速模式
            byte[] data;
            LoggerUtils.d(TAG, "快速模式 制作指令，凯路威亮灯标签" + isLedKLV );
            if (isTid) {
                LoggerUtils.d(TAG, "快速模式，   AA48 要 TID" );
                data = builderAnalysisSLR.makeStartFastModeInventorySendDataNeedTid512(selectEntity, true);
            } else if (isMoreTag) {
                LoggerUtils.d(TAG, "快速模式 制作指令，是多标签  AA58" );
                data = builderAnalysisSLR.makeStartFastModeInventorySendDataMoreTag(null, false);
            } else if (isLedKLV) {
                LoggerUtils.d(TAG, "快速模式 制作指令，凯路威亮灯标签" );
                data = builderAnalysisSLR.makeLedKLV(selectEntity);
            }
            else {
                LoggerUtils.d(TAG, "快速模式，   AA48 不用TID " +builderAnalysisSLR.getClass() );
                data = builderAnalysisSLR.makeStartFastModeInventorySendData(selectEntity, false);
            }
//            LoggerUtils.d(TAG, "快速模式， 最终组装的 " + StringTool.ByteArrToHex(bytes));

            UHFProtocolAnalysisBase.DataFrameInfo dataFrameInfo = sendAndReceiveData(data);
            UHFReaderResult<Boolean> uhfReaderResult = null;

            //解析返回的
            if (isTid) {
                LoggerUtils.d(TAG, "快速模式，   AA48 不用TID   返回开始指令 " +dataFrameInfo );
                uhfReaderResult = builderAnalysisSLR.analysisStartFastModeInventoryReceiveDataNeedTid(dataFrameInfo, true);
            }else if (isMoreTag){
                //解析
                uhfReaderResult = builderAnalysisSLR.analysisStartFastModeInventoryReceiveDataNeedTidMoreTag(dataFrameInfo, false);

            } else if (isLedKLV) {
                uhfReaderResult = builderAnalysisSLR.analysisMakeLedInventoryReceiveData(dataFrameInfo, false);
        }
            else {
                uhfReaderResult = builderAnalysisSLR.analysisStartFastModeInventoryReceiveData(dataFrameInfo, false);
                LoggerUtils.d(TAG,"是我在解析 结果 = "+uhfReaderResult.getResultCode());
            }
            if (uhfReaderResult.getResultCode() != UHFReaderResult.ResultCode.CODE_SUCCESS) {
                return new UHFReaderResult(UHFReaderResult.ResultCode.CODE_FAILURE, "", false);
            }
//            if (selectEntity != null) {
//                isNeedFilter = true;
//            }
            startFastModeInventoryThread();
            return new UHFReaderResult(UHFReaderResult.ResultCode.CODE_SUCCESS, "", true);
        }

    }

    @Override
    public UHFReaderResult<Boolean> stopInventory() {
        if (InventoryMode == InventoryModeForPower.POWER_SAVING_MODE) {
            //普通模式
            stopPowerSavingModeInventoryThread();
            return new UHFReaderResult(UHFReaderResult.ResultCode.CODE_SUCCESS);
        } else {
            //以下上快速模式
            byte[] data = builderAnalysisSLR.makeStopFastModeInventorySendData();
            UHFProtocolAnalysisBase.DataFrameInfo dataFrameInfo = sendAndReceiveData(data);
            UHFReaderResult<Boolean> uhfReaderResult = builderAnalysisSLR.analysisStopFastModeInventoryReceiveData(dataFrameInfo);
            stopFastModeInventoryThread();
            if (uhfReaderResult.getResultCode() != UHFReaderResult.ResultCode.CODE_SUCCESS) {
                Log.e("ttt", "stopInventory: 停止解析错误 "  );
                return new UHFReaderResult(UHFReaderResult.ResultCode.CODE_FAILURE, "", false);
            }
            Log.e("ttt", "stopInventory: 停止解析 "  );

            return new UHFReaderResult(UHFReaderResult.ResultCode.CODE_SUCCESS, "", true);
        }

    }

    @Override
    public UHFReaderResult<UHFTagEntity> singleTagInventory(SelectEntity selectEntity) {

        //以下是快速模式
        byte[] data = builderAnalysisSLR.makeSingleTagInventorySendData(selectEntity);
        UHFProtocolAnalysisBase.DataFrameInfo dataFrameInfo = sendAndReceiveData(data);
        UHFReaderResult<UHFTagEntity> uhfReaderResult = builderAnalysisSLR.analysisSingleTagInventoryResultData(dataFrameInfo);
        return uhfReaderResult;
    }

    @Override
    public UHFReaderResult<Boolean> setInventorySelectEntity(SelectEntity selectEntity) {
        byte[] data = builderAnalysisSLR.makeInventorySelectEntity(selectEntity);
        UHFProtocolAnalysisBase.DataFrameInfo dataFrameInfo = sendAndReceiveData(data);
        UHFReaderResult<Boolean> uhfReaderResult = builderAnalysisSLR.analysisInventorySelectEntityResultData(dataFrameInfo);
        return uhfReaderResult;
    }

    @Override
    public UHFReaderResult<Boolean> setModuleType(String moduleType) {
        return null;
    }

    @Override
    public UHFReaderResult<String> getModuleType() {
        return null;
    }

    @Override
    public UHFReaderResult<Boolean> writeEPCID(String password, int address, String selectData, String writeData) {
        byte[] data = builderAnalysisSLR.makeWriteEPCIDSendData(password, address, selectData, writeData);
        Log.e("TAG", "writeEPCID: " + DataConverter.bytesToHex(data));
        UHFProtocolAnalysisBase.DataFrameInfo dataFrameInfo = sendAndReceiveData(data);
        UHFReaderResult<Boolean> uhfReaderResult = builderAnalysisSLR.analysisWriteEPCIDResultData(dataFrameInfo);
        return uhfReaderResult;
    }

    @Override
    public UHFReaderResult<Boolean> setGB(boolean enable) {
        byte[] data = builderAnalysisSLR.makeSetGB(enable);
        UHFProtocolAnalysisBase.DataFrameInfo dataFrameInfo = sendAndReceiveData(data);
        UHFReaderResult<Boolean> uhfReaderResult = builderAnalysisSLR.analysisSetGBResultData(dataFrameInfo);
        return uhfReaderResult;
    }


    public void startPowerSavingModeInventoryThread(SelectEntity selectEntity) {
        if (inventoryThread == null) {
            inventoryThread = new PowerSavingModeInventoryThread(selectEntity);
            inventoryThread.start();
        }
    }

    public void stopPowerSavingModeInventoryThread() {
        if (inventoryThread != null) {
            inventoryThread.stopThread();
            inventoryThread = null;
        }
    }

    public void startFastModeInventoryThread() {
        if (fastModeInventoryThread == null) {
            fastModeInventoryThread = new FastModeInventoryThread();
            fastModeInventoryThread.start();
        }
    }

    public void stopFastModeInventoryThread() {
        if (fastModeInventoryThread != null) {
            fastModeInventoryThread.stopThread();
            fastModeInventoryThread = null;
        }
    }

    //快速模式
    class FastModeInventoryThread extends Thread {
        private boolean isStop = false;
        private Object lock = new Object();

        @Override
        public void run() {
            while (!isStop) {
                {
                    UHFProtocolAnalysisBase.DataFrameInfo dataFrameInfo = uhfProtocolAnalysisSLR.getTagInfo();
                    List<UHFTagEntity> list = null;

//                    list = builderAnalysisSLR.analysisFastModeTagInfoReceiveDataOld(dataFrameInfo);
//                    LoggerUtils.d(TAG, "解析到了这.............="+isMoreTag);
//                    LoggerUtils.d(TAG, "解析到了这.............="+isTid);

                    if (isTid) {
                        LoggerUtils.d(TAG, "需要TID 解析到了这.............="+dataFrameInfo);
                        list = builderAnalysisSLR.analysisFastModeTagInfoReceiveData(dataFrameInfo);
                    } else if (isMoreTag){
//                        LoggerUtils.d(TAG, "解析到了这.............=");
                        list = builderAnalysisSLR.analysisFastModeTagInfoReceiveDataMoreTag(dataFrameInfo);
                    }else if (isLedKLV) {
                        list = builderAnalysisSLR.analysisLedInventoryReceiveData(dataFrameInfo,false);

                    }else {
                        list = builderAnalysisSLR.analysisFastModeTagInfoReceiveDataOld(dataFrameInfo);
                    }

                    if (list != null && list.size() > 0) {
                        if (onInventoryDataListener != null) {
                            onInventoryDataListener.onInventoryData(list);
                        }
                    } else {
                        sleep(1);

                    }
                }
            }
        }

        private void sleep(int time) {
            synchronized (lock) {
                try {
                    lock.wait(time);
                } catch (InterruptedException e) {
                    e.printStackTrace();
                }
            }
        }

        public void stopThread() {
            isStop = true;
            synchronized (lock) {
                lock.notifyAll();
            }
        }
    }

    //省电模式
    class PowerSavingModeInventoryThread extends Thread {
        private boolean isStop = false;
        private Object lock = new Object();
        private int tagNumber = 0;
        private long starTime = SystemClock.elapsedRealtime();
        private long tempTime = SystemClock.elapsedRealtime();
        private SelectEntity selectEntity = null;

        public PowerSavingModeInventoryThread(SelectEntity selectEntity) {
            this.selectEntity = selectEntity;
        }

        @Override
        public void run() {
            while (!isStop) {
                if (tagNumber <= 0) {
                    //发送盘点命令
                    UHFProtocolAnalysisBase.DataFrameInfo dataFrameInfo = sendAndReceiveData(builderAnalysisSLR.makeStartInventorySendData(selectEntity, isTid));
                    UHFReaderResult<Integer> result = builderAnalysisSLR.analysisStartInventoryReceiveData(dataFrameInfo);
                    if (result.getResultCode() == UHFReaderResult.ResultCode.CODE_SUCCESS && result.getData() > 0) {
                        starTime = SystemClock.elapsedRealtime();
                        tagNumber = result.getData();
                        LoggerUtils.d(TAG, "发送盘点命令获取到标签张数:" + result.getData());
                    }
                } else {
                    //发送获取标签的命令
                    boolean result = sendData(builderAnalysisSLR.makeGetTagInfoSendData());
                    if (result) {
                        int count = 50;
                        tempTime = SystemClock.elapsedRealtime();
                        for (int k = 0; k < count; k++) {
                            UHFProtocolAnalysisBase.DataFrameInfo dataFrameInfo = uhfProtocolAnalysisSLR.getTagInfo();
                            List<UHFTagEntity> list = builderAnalysisSLR.analysisTagInfoReceiveData(dataFrameInfo);
                            if (list != null && list.size() > 0) {
                                if (onInventoryDataListener != null) {
                                    onInventoryDataListener.onInventoryData(list);
                                }
                                tagNumber = tagNumber - list.size();
                                LoggerUtils.d(TAG, "当前获取的标签张数:" + list.size() + "  剩余的标签张数：" + tagNumber);
                                break;
                            }
                            sleep(1);
                            if (SystemClock.elapsedRealtime() - tempTime > count) {
                                break;
                            }
                        }
                    } else {
                        //勾选了TID
                        //FF1FAA4D6F64756C6574656368AA480006009004010928000000020000000006C6BBD321

                        // 未勾选TID
                        //FF13AA4D6F64756C6574656368AA4800060090038BBB97DA

                        sleep(10);
                        LoggerUtils.d(TAG, "发送数据失败");
                    }
                    if (SystemClock.elapsedRealtime() - starTime > 800) {
                        //2秒还没有接收完，直接发下一次盘点命令
                        tagNumber = 0;
                    }

                }
            }
        }

        private void sleep(int time) {
            synchronized (lock) {
                try {
                    lock.wait(time);
                } catch (InterruptedException e) {
                    e.printStackTrace();
                }
            }
        }

        public void stopThread() {
            isStop = true;
            synchronized (lock) {
                lock.notifyAll();
            }
        }
    }

    class UHFCheckCodeErrorCallback implements com.xlzn.hcpda.uhf.interfaces.IUHFCheckCodeErrorCallback {
        @Override
        public void checkCodeError(int mode, int cmd, byte[] errorData) {
            LoggerUtils.d(TAG, "校验码错误!");
            if (inventoryThread != null) {
                inventoryThread.tagNumber = 0;
            }
        }
    }
}
