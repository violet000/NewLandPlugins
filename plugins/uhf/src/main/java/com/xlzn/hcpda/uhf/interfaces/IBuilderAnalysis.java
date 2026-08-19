package com.xlzn.hcpda.uhf.interfaces;

import com.xlzn.hcpda.uhf.analysis.UHFProtocolAnalysisBase.DataFrameInfo;
import com.xlzn.hcpda.uhf.entity.SelectEntity;
import com.xlzn.hcpda.uhf.entity.UHFReaderResult;
import com.xlzn.hcpda.uhf.entity.UHFTagEntity;
import com.xlzn.hcpda.uhf.entity.UHFVersionInfo;
import com.xlzn.hcpda.uhf.enums.LockActionEnum;
import com.xlzn.hcpda.uhf.enums.LockMembankEnum;
import com.xlzn.hcpda.uhf.enums.UHFSession;

import java.util.List;

/*
 * 构建发送的数据和解析接接收后的数据
 */
public interface IBuilderAnalysis {


    /**
     * 凯路威亮灯标签
     */
    byte[] makeLedKLV(SelectEntity entity);

    /**
     * 解析亮灯标签
     *
     * @return return
     */
    public List<UHFTagEntity> analysisLedInventoryReceiveData(DataFrameInfo data, boolean isTID);
    /**
     * 解析亮灯标签盘存开启返回
     *
     * @return return
     */
    public UHFReaderResult<Boolean> analysisMakeLedInventoryReceiveData(DataFrameInfo data, boolean isTID);


    /**
    * 复旦微国军标协议设置
    */
   public byte[] makeSetGB(boolean model);

   public UHFReaderResult<Boolean> analysisSetGBResultData(DataFrameInfo data);




   /**
     * 设置 target 模式
     * 0：A 模式
     * 1：B 模式
     * 2：A-B 模式
     * 3：B-A 模式
     */
    public byte[] makeSetTargetModel(int model);

    /**
     *  获取发送盘点的指令
     *
     * @return return
     */
    public byte[] makeStartInventorySendData(SelectEntity selectEntity,boolean isTID);

    /**
     *  获取发送快速模式连续盘点的指令
     *
     * @return return
     */
    public byte[] makeStartFastModeInventorySendData(SelectEntity selectEntity,boolean isTID);

    /**
     *  获取发送快速模式连续盘点的指令  多标签，并且只支持盘存EPC，AA58指令
     *
     * @return return
     */
    public byte[] makeStartFastModeInventorySendDataMoreTag(SelectEntity selectEntity,boolean isTID);
    /**
     *  获取发送快速模式连续盘点的指令
     *
     * @return return
     */
    public byte[] makeStartFastModeInventorySendDataNeedTid(SelectEntity selectEntity,boolean isTID);
 /**
     *  获取发送快速模式连续盘点的指令
     *
     * @return return
     */
    public byte[] makeStartFastModeInventorySendDataNeedTid512(SelectEntity selectEntity,boolean isTID);

    /**
     * 解析接收的发送快速模式连续盘点的指令
     *
     * @return return
     */
    public UHFReaderResult<Boolean> analysisStartFastModeInventoryReceiveData(DataFrameInfo data, boolean isTID);

    /**
     *  解析接收的发送快速模式连续盘点的指令AA48 带TID的
     *
     * @return return
     */
    public UHFReaderResult<Boolean> analysisStartFastModeInventoryReceiveDataNeedTid(DataFrameInfo data,boolean isTID);

    /**
     *  解析接收的发送快速模式连续盘点的指令AA58
     *
     * @return return
     */
    public UHFReaderResult<Boolean> analysisStartFastModeInventoryReceiveDataNeedTidMoreTag(DataFrameInfo data,boolean isTID);

    /**
     *
     *  快速模式停止盘点
     *
     * @return return
     */
    public byte[] makeStopFastModeInventorySendData();
    /**
     *  解析接收的发送快速模式停止盘点的指令
     *
     * @return return
     */
    public UHFReaderResult<Boolean> analysisStopFastModeInventoryReceiveData(DataFrameInfo data);

    /**
     *  解析接收的开始盘点的指令
     *
     * @return return
     */
    public UHFReaderResult<Integer> analysisStartInventoryReceiveData(DataFrameInfo data);
    /**
     *  获取标签数据的命令
     *
     * @return return
     */
    public byte[] makeGetTagInfoSendData();
    /**
     *  解析盘点数据
     *
     * @return return
     */
    public List<UHFTagEntity> analysisTagInfoReceiveData(DataFrameInfo data) ;

    /**
     *  解析快速模式盘点数据
     *
     * @return return
     */
    public List<UHFTagEntity> analysisFastModeTagInfoReceiveData(DataFrameInfo data);
    /**
     *  解析快速模式盘点数据
     *
     * @return return
     */
    public List<UHFTagEntity> analysisFastModeTagInfoReceiveDataOld(DataFrameInfo data);
    /**
     *  获取模块版本
     *
     * @return return
     */
    public byte[] makeGetVersionSendData();
    /**
     *  解析模块版本数据
     *
     * @return return
     */
    public UHFReaderResult<UHFVersionInfo> analysisVersionData(DataFrameInfo data);
    /**
     *  设置session
     *
     * @return return
     */
    public byte[] makeSetSessionSendData(UHFSession value);
    /**
     *  解析设置 session数据
     *
     * @return return
     */
    public UHFReaderResult<Boolean> analysisSetSessionResultData(DataFrameInfo data);

    /**
     *  获取session
     *
     * @return return
     */
    public byte[] makeGetSessionSendData();
    /**
     *  解析 获取session数据
     *
     * @return return
     */
    public UHFReaderResult<UHFSession> analysisGetSessionResultData(DataFrameInfo data);


    /**
     *  设置功率
     *
     * @return return
     */
    public byte[] makeSetPowerSendData(int power);
    /**
     *  解析设设置功率数据
     *
     * @return return
     */
    public UHFReaderResult<Boolean> analysisSetPowerResultData(DataFrameInfo data);

    /**
     *  设置功率
     *
     * @return return
     */
    public byte[] makeGetPowerSendData();
    /**
     *  解析设设置功率数据
     *
     * @return return
     */
    public UHFReaderResult<Integer> analysisGetPowerResultData(DataFrameInfo data);


    /**
     *  设置频率
     *
     * @return return
     */
    public byte[] makeSetFrequencyRegionSendData(int frequencyRegion);
    /**
     *  解析设置频率数据
     *
     * @return return
     */
    public UHFReaderResult<Boolean> analysisSetFrequencyRegionResultData(DataFrameInfo data);

    /**
     *  获取频率
     *
     * @return return
     */
    public byte[] makeGetFrequencyRegionSendData();
    /**
     *  解析获取频率数据
     *
     * @return return
     */
    public UHFReaderResult<Integer> analysisGetFrequencyRegionResultData(DataFrameInfo data);

    /**
     *  获取模块温度
     *
     * @return return
     */
    public byte[] makeGetTemperatureSendData();
    /**
     *  解析模块温度数据
     *
     * @return return
     */
    public UHFReaderResult<Integer> analysisGetTemperatureResultData(DataFrameInfo data);


    /**
     *  动态
     *
     * @return return
     */
    public byte[] makeSetDynamicTargetSendData(int value);
    /**
     *  动态
     *
     * @return return
     */
    public UHFReaderResult<Boolean> analysisSetDynamicTargetResultData(DataFrameInfo data);

    /**
     *  静态
     *
     * @return return
     */
    public byte[] makeSetStaticTargetSendData(int value);
    /**
     *  静态
     *
     * @return return
     */
    public UHFReaderResult<Boolean> analysisSetStaticTargetResultData(DataFrameInfo data);

    /**
     *
     *
     * @return return
     */
    public byte[] makeGetTargetSendData();
    /**
     *
     *
     * @return return
     */
    public UHFReaderResult<int[]> analysisGetTargetResultData(DataFrameInfo data);

    /**
     *
     *
     * @return return
     */
    public byte[] makeReadSendData(String password,int membank,int address,int wordCount,SelectEntity selectEntity);
    /**
     *
     *
     * @return return
     */
    public UHFReaderResult<String> analysisReadResultData(DataFrameInfo data);

    /**
     *
     *
     * @return return
     */
    public byte[] makeWriteSendData(String password,int membank,int address,int wordCount,String data,SelectEntity selectEntity);
    /**
     *
     *
     * @return return
     */
    public UHFReaderResult<Boolean> analysisWriteResultData(DataFrameInfo data);

   /**
    *
    *
    * @return return
    */
   public byte[] makeWriteEPCIDSendData(String password,int address,String selectData,String writeData);
   /**
    *
    *
    * @return return
    */
   public UHFReaderResult<Boolean> analysisWriteEPCIDResultData(DataFrameInfo data);

    /**
     *
     *
     * @return return
     */
    public byte[] makeKillSendData(String password,SelectEntity selectEntity);
    /**
     *
     *
     * @return return
     */
    public UHFReaderResult<Boolean> analysisKillResultData(DataFrameInfo data);
    /**
     *
     *
     * @return return
     */
    public byte[] makeLockSendData(String password, LockMembankEnum hexMask, LockActionEnum hexAction, SelectEntity selectEntity);
    /**
     *
     *
     * @return return
     */
    public UHFReaderResult<Boolean> analysisLockResultData(DataFrameInfo data);
    /**
     *单标签盘点
     *
     * @return return
     */
    public byte[] makeSingleTagInventorySendData(SelectEntity selectEntity);
    /**
     *
     *
     * @return return
     */
    public UHFReaderResult<UHFTagEntity> analysisSingleTagInventoryResultData(DataFrameInfo data);

    public byte[] makeInventorySelectEntity(SelectEntity selectEntity);
    public UHFReaderResult<Boolean> analysisInventorySelectEntityResultData(DataFrameInfo data);

    public byte[] makeSetBaudRate(int baudrate);
    public UHFReaderResult<Boolean> analysisSetBaudRateResultData(DataFrameInfo data);

    public byte[] makeSetFrequencyPoint(int frequencyPoint);
    public UHFReaderResult<Boolean> analysisSetFrequencyPointResultData(DataFrameInfo data);

    public byte[] makeSetRFLink(int mode);
    public UHFReaderResult<Boolean> analysisSetRFLinkResultData(DataFrameInfo data);

    List<UHFTagEntity> analysisFastModeTagInfoReceiveDataMoreTag(DataFrameInfo dataFrameInfo);
}
