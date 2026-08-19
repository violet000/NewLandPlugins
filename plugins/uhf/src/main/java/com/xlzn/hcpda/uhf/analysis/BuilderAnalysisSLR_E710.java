package com.xlzn.hcpda.uhf.analysis;

import com.xlzn.hcpda.uhf.entity.SelectEntity;
import com.xlzn.hcpda.uhf.entity.UHFReaderResult;
import com.xlzn.hcpda.uhf.entity.UHFTagEntity;
import com.xlzn.hcpda.uhf.module.UHFReaderSLR;
import com.xlzn.hcpda.utils.DataConverter;
import com.xlzn.hcpda.utils.LoggerUtils;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

public class BuilderAnalysisSLR_E710 extends BuilderAnalysisSLR {

    private String TAG = "BuilderAnalysisSLR_E710";

    @Override
    public List<UHFTagEntity> analysisLedInventoryReceiveData(UHFProtocolAnalysisBase.DataFrameInfo data, boolean isTID) {
        return super.analysisLedInventoryReceiveData(data, isTID);
    }


    //FF F4 29 00 00 00 BF 00 08 01 C1 01 0D FF 02 00 00 00 0F 01 A9 00 00 00 80 34 00 22 22 22 22 22 22 22 22 22 22 22 22 01 A3 01 C7 01 0D FF 02 00 00 00 13 06 43 00 00 00 80 30 00 00 00 68 90 00 00 00 03 17 04 D7 98 06 4A 01 C2 01 0D FF 02 00 00 00 17 0E B2 00 00 00 60 20 00 00 00 00 00 CC FF CC FF 0E C1 02 E1 01 0D FF 02 00 00 00 1B 01 F1 00 00 00 80 30 00 00 00 00 00 00 00 00 00 00 00 00 00 01 F1 01 CF 01 0D FF 02 00 00 00 27 0C 62 00 00 00 60 24 00 00 00 00 00 CC FF CC FF 0C 5F 01 C0 01 0D FF 02 00 00 00 2B 05 B5 00 00 00 80 30 00 E2 82 78 83 00 00 00 00 00 47 56 55 05 E9 01 C8 01 0D FF 02 00 00 00 2E 0F D4 00 00 00 40 14 00 CC FF CC FF 0F D3 01 C2 01 0D FF 02 00 00 00 34 02 2D 00 00 00 80 30 00 30 39 60 62 83 B0 1E C0 00 5B 7E 46 02 4D EF E7
    /**
     * 解析接收的发送快速模式连续盘点的指令AA48 带TID的
     * @param data
     * @param isTID
     * @return return
     */
    @Override
    public UHFReaderResult<Boolean> analysisStartFastModeInventoryReceiveDataNeedTid(UHFProtocolAnalysisBase.DataFrameInfo data, boolean isTID) {
        this.isTID = isTID;
        //0xFF+DATALEN+0XAA+STATUS +”Moduletech”+SubCmdHighByte+SubCmdLowByte+data+CRC
        if (data != null) {
            if (data.status == 00) {
                LoggerUtils.d(TAG, "开始盘点指令返回Data:" + DataConverter.bytesToHex(data.data));
                if ((data.data[10] & 0xFF) == 0xAA && (data.data[11] & 0xFF) == 0x48) {
                    return new UHFReaderResult<Boolean>(UHFReaderResult.ResultCode.CODE_SUCCESS, "", true);
                }
                //4D 6F 64 75 6C 65 74 65 63 68 AA 48
            }
        }
        return new UHFReaderResult<Boolean>(UHFReaderResult.ResultCode.CODE_FAILURE, "", false);

    }

    /**
     * 获取发送盘点的指令（快速模式）AA48
     * 注意 AA58 不支持盘存过滤
     *
     * @return return
     */
    public byte[] makeStartFastModeInventorySendData(SelectEntity selectEntity, boolean isTID) {
        LoggerUtils.d(TAG,"呀是来的这啊 过滤 = " + selectEntity);
        if(selectEntity==null) {
            byte[] senddata = new byte[512];
            byte[] moduletech = "Moduletech".getBytes();
            //************Moduletech*************
            System.arraycopy(moduletech, 0, senddata, 0, moduletech.length);
            //***********SubCmdHighByte+SubCmdLowByte************
            int index = moduletech.length;
            int subcrcIndex = index;
            senddata[index++] = (byte) 0xAA;
            senddata[index++] = (byte) 0x48;
            //************data*******************
            senddata[index++] = 0x00;
            senddata[index++] = (byte) 0x02;
            //1字节OPTION
            senddata[index++] = 0x00;//不启用匹配过滤
            senddata[index++] = (0x00);
//            82 00 00 03 77 BB 64 F8
            if(!isTID) {
                senddata[index++] = 0x03;
                senddata[index++] = (byte) 0xF7;
            }else {
                senddata[index++] = 0x04;
                //**********************************************
                //嵌入命令数量，目前该值只能为1.
                senddata[index++] = (byte) 0x01;
                //嵌入命令的数据域的字节长度。
                senddata[index++] = (byte) 0x09;
                //嵌入的命令码。目前只能嵌入（0X28命令）
                senddata[index++] = (byte) 0x28;
                //嵌入命令的数据域
                //Emb Cmd Timeout
                senddata[index++] = (byte) 0x00;
                senddata[index++] = (byte) 0x00;
                //Emb Cmd Option
                senddata[index++] = (byte) 0x00;
                //Read Membank
                senddata[index++] = (byte) 0x02;
                //Read Address
                senddata[index++] = (byte) 0x00;
                senddata[index++] = (byte) 0x00;
                senddata[index++] = (byte) 0x00;
                senddata[index++] = (byte) 0x00;
                //Read Word Count
                senddata[index++] = (byte) 0x06;
            }

            //****************data****************
            //****************SubCrc****************
            int subcrcTemp = 0;
            for (int k = subcrcIndex; k < index; k++) {
                subcrcTemp = subcrcTemp + (senddata[k] & 0xFF);
            }
//            senddata[index++] = (byte) (subcrcTemp & 0xFF);
            //****************SubCrc****************
            senddata[index++] = (byte) 0xbb;
            return buildSendData(0XAA, Arrays.copyOf(senddata, index));
        }

        // 0xFF+DATALEN+0xAA+”Moduletech”+SubCmdHighByte+SubCmdLowByte+data+SubCrc+0xbb+CRC
        //FF+DATALEN+ 0XAA+”Moduletech”+AA+48+data+SubCrc+0xbb+CRC
        //data:N字节，2字节METADATAFLAG+1字节OPTION+2字节SEARCHFLAGS+匹配过滤相关数据（由OPTION决定，同0X22指令）+盘存嵌入命令（由SEARCHFLAGS决定，同0X22指令）
        // 1.SubCrc：1字节，为SubCmdHighByte开始到data结束的所有数据相加结果的低8位值；
        byte[] senddata = new byte[512];
        byte[] moduletech = "Moduletech".getBytes();
        //************Moduletech*************
        System.arraycopy(moduletech, 0, senddata, 0, moduletech.length);
        //***********SubCmdHighByte+SubCmdLowByte************
        int index = moduletech.length;
        int subcrcIndex = index;
        senddata[index++] = (byte) 0xAA;
        senddata[index++] = (byte) 0x48;
        //************data*******************
        //2字节METADATAFLAG
        final int count = 0X0001;//Bit0置位即标签在盘存时间内被盘存到的次数将会返回
        final int rssi = 0x0002;//BIT1置位即标签的RSSI信号值将会被返回
        final int ant = 0X0004;//BIT2置位即标签 被盘存到时所用的天线 ID号将会被返回。（逻辑天线号）
        final int flag = count | rssi | ant;
        senddata[index++] = (flag >> 8) & 0xFF;
        senddata[index++] = flag & 0xFF;
        //1.字节OPTION
        senddata[index++] = (byte) (selectEntity.getOption());// 启用匹配过滤
        //2.字节SEARCHFLAGS,SEARCHFLAGS高字节的低4位表示不停止盘存过程中的停顿时间dd
        //0x10:每工作1秒中盘存时间950毫秒，停顿时间50毫秒
        //0x20:停顿时间100毫秒,0x30:停顿150，0x00:不停顿
        senddata[index++] = (0x00 | 0x10);
        senddata[index++] = 0x00;
        //3. 4字节AccessPassword
        senddata[index++] = 0x00;
        senddata[index++] = 0x00;
        senddata[index++] = 0x00;
        senddata[index++] = 0x00;
        //4. Select Address(bits)
        int address=selectEntity.getAddress();
        senddata[index++]=(byte)((address>>24) &0xFF);
        senddata[index++]=(byte)((address>>16) &0xFF);
        senddata[index++]=(byte)((address>>8) &0xFF);
        senddata[index++]=(byte)(address&0xFF);
        //Select data length(bits)
        senddata[index++]=(byte)selectEntity.getLength();
        //Select data
        byte[] byteData=DataConverter.hexToBytes(selectEntity.getData());
        int len=selectEntity.getLength()/8;
        if(selectEntity.getLength()%8!=0) {
            len += 1;
        }
        for(int k=0;k<len;k++){
            senddata[index++]=byteData[k];
        }

        //****************data****************
        //****************SubCrc****************
        int subcrcTemp = 0;
        for (int k = subcrcIndex; k < index; k++) {
            subcrcTemp = subcrcTemp + (senddata[k] & 0xFF);
        }
        senddata[index++] = (byte) (subcrcTemp & 0xFF);
        //****************SubCrc****************
        senddata[index++] = (byte) 0xbb;
        return buildSendData(0XAA, Arrays.copyOf(senddata, index));

        /**
         * 下面是备份旧的
         * ******************************************************************************
         */

//        if (selectEntity == null) {
//            // 0xFF+DATALEN+0xAA+”Moduletech”+SubCmdHighByte+SubCmdLowByte+data+SubCrc+0xbb+CRC
//            //FF+DATALEN+ 0XAA+”Moduletech”+AA+48+data+SubCrc+0xbb+CRC
//            //data:N字节，2字节METADATAFLAG+1字节OPTION+2字节SEARCHFLAGS+匹配过滤相关数据（由OPTION决定，同0X22指令）+盘存嵌入命令（由SEARCHFLAGS决定，同0X22指令）
//            // 1.SubCrc：1字节，为SubCmdHighByte开始到data结束的所有数据相加结果的低8位值；
//            byte[] senddata = new byte[512];
//            byte[] moduletech = "Moduletech".getBytes();
//            //************Moduletech*************
//            System.arraycopy(moduletech, 0, senddata, 0, moduletech.length);
//            //***********SubCmdHighByte+SubCmdLowByte************
//            int index = moduletech.length;
//            int subcrcIndex = index;
//            senddata[index++] = (byte) 0xAA;
//            senddata[index++] = (byte) 0x58;
//
//            for (int k = 0; k < 20; k++) {
//                senddata[index++] = (byte) 0x00;
//            }
//
//            //************data*******************
//            //2字节METADATAFLAG
//            final int count = 0X0001;//Bit0置位即标签在盘存时间内被盘存到的次数将会返回
//            final int rssi = 0x0002;//BIT1置位即标签的RSSI信号值将会被返回
//            final int ant = 0X0004;//BIT2置位即标签 被盘存到时所用的天线 ID号将会被返回。（逻辑天线号）
//            final int tagData = 0x0080;//返回嵌入命令内存数据
////            final int flag = count | rssi | ant | tagData;
//            final int flag = rssi | ant;
//            senddata[index++] = (flag >> 8) & 0xFF;
//            senddata[index++] = (byte) (flag & 0xFF);
//            //1字节OPTION
//            senddata[index++] = 0x00;//不启用匹配过滤
//            //2字节SEARCHFLAGS,SEARCHFLAGS高字节的低4位表示不停止盘存过程中的停顿时间dd
//            //0x10:每工作1秒中盘存时间950毫秒，停顿时间50毫秒
//            //0x20:停顿时间100毫秒,0x30:停顿150，0x00:不停顿
//            senddata[index++] = (0x10);
//            //senddata[index++] = 0x04;//todo 0x00
//            if (!isTID) {
//                LoggerUtils.d(TAG, "不需要TID");
//                senddata[index++] = 0x00;
//            } else {
//                senddata[index++] = 0x04;
//                //**********************************************
//                //嵌入命令数量，目前该值只能为1.
//                senddata[index++] = (byte) 0x01;
//                //嵌入命令的数据域的字节长度。
//                senddata[index++] = (byte) 0x09;
//                //嵌入的命令码。目前只能嵌入（0X28命令）
//                senddata[index++] = (byte) 0x28;
//                //嵌入命令的数据域
//                //Emb Cmd Timeout
//                senddata[index++] = (byte) 0x00;
//                senddata[index++] = (byte) 0x00;
//                //Emb Cmd Option
//                senddata[index++] = (byte) 0x00;
//                //Read Membank
//                senddata[index++] = (byte) 0x02;
//                //Read Address
//                senddata[index++] = (byte) 0x00;
//                senddata[index++] = (byte) 0x00;
//                senddata[index++] = (byte) 0x00;
//                senddata[index++] = (byte) 0x00;
//                //Read Word Count
//                senddata[index++] = (byte) 0x06;
//            }
//
//            //****************data****************
//            //****************SubCrc****************
//            int subcrcTemp = 0;
//            for (int k = subcrcIndex; k < index; k++) {
//                subcrcTemp = subcrcTemp + (senddata[k] & 0xFF);
//            }
//            senddata[index++] = (byte) (subcrcTemp & 0xFF);
//            //****************SubCrc****************
//            senddata[index++] = (byte) 0xbb;
//            LoggerUtils.d(TAG,"构建快速模式指令发出,不需要过滤 AA58");
//            //FF 27 AA 4D 6F 64 75 6C 65 74 65 63 68 AA 58 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 03 05 BB B3 19
//            byte[] senddataTT = new byte[512];
//
//            return buildSendData(0XAA, Arrays.copyOf(senddata, index));
//        }
//
//        // 0xFF+DATALEN+0xAA+”Moduletech”+SubCmdHighByte+SubCmdLowByte+data+SubCrc+0xbb+CRC
//        //FF+DATALEN+ 0XAA+”Moduletech”+AA+48+data+SubCrc+0xbb+CRC
//        //data:N字节，2字节METADATAFLAG+1字节OPTION+2字节SEARCHFLAGS+匹配过滤相关数据（由OPTION决定，同0X22指令）+盘存嵌入命令（由SEARCHFLAGS决定，同0X22指令）
//        // 1.SubCrc：1字节，为SubCmdHighByte开始到data结束的所有数据相加结果的低8位值；
//        byte[] senddata = new byte[512];
//        byte[] moduletech = "Moduletech".getBytes();
//        //************Moduletech*************
//        System.arraycopy(moduletech, 0, senddata, 0, moduletech.length);
//        //***********SubCmdHighByte+SubCmdLowByte************
//        int index = moduletech.length;
//        int subcrcIndex = index;
//        senddata[index++] = (byte) 0xAA;
//        senddata[index++] = (byte) 0x58;
//
//        for (int k = 0; k < 20; k++) {
//            senddata[index++] = (byte) 0x00;
//        }
//        //************data*******************
//        //2字节METADATAFLAG
//        final int count = 0X0001;//Bit0置位即标签在盘存时间内被盘存到的次数将会返回
//        final int rssi = 0x0002;//BIT1置位即标签的RSSI信号值将会被返回
//        final int ant = 0X0004;//BIT2置位即标签 被盘存到时所用的天线 ID号将会被返回。（逻辑天线号）
//        //TODO 需要增加获取嵌入式命令数据, 要不然analysisF astModeTagInfoReceiveData 函数在解析数据的时候，下面这行会获取错误数据导致数组内存溢出
//        //TODO int tidLen= ((taginfo[++statIndex] & 0xFF) << 8) | (taginfo[++statIndex] & 0xFF);//嵌入命令的数据
//        final int tagData = 0x0080;//返回嵌入命令内存数据
//        final int flag = count | rssi | ant | tagData;
//        senddata[index++] = (flag >> 8) & 0xFF;
//        senddata[index++] = (byte) (flag & 0xFF);
//        //1.字节OPTION
//        senddata[index++] = (byte) (selectEntity.getOption());// 启用匹配过滤
//        //2.字节SEARCHFLAGS,SEARCHFLAGS高字节的低4位表示不停止盘存过程中的停顿时间dd
//        //0x10:每工作1秒中盘存时间950毫秒，停顿时间50毫秒
//        //0x20:停顿时间100毫秒,0x30:停顿150，0x00:不停顿
//        senddata[index++] = (0x00 | 0x10);
//        senddata[index++] = 0x00;
//        //3. 4字节AccessPassword
//        senddata[index++] = 0x00;
//        senddata[index++] = 0x00;
//        senddata[index++] = 0x00;
//        senddata[index++] = 0x00;
//        //4. Select Address(bits)
//        int address = selectEntity.getAddress();
//        senddata[index++] = (byte) ((address >> 24) & 0xFF);
//        senddata[index++] = (byte) ((address >> 16) & 0xFF);
//        senddata[index++] = (byte) ((address >> 8) & 0xFF);
//        senddata[index++] = (byte) (address & 0xFF);
//        //Select data length(bits)
//        senddata[index++] = (byte) selectEntity.getLength();
//        //Select data
//        byte[] byteData = DataConverter.hexToBytes(selectEntity.getData());
//        int len = selectEntity.getLength() / 8;
//        if (selectEntity.getLength() % 8 != 0) {
//            len += 1;
//        }
//        for (int k = 0; k < len; k++) {
//            senddata[index++] = byteData[k];
//        }
//
//        //****************data****************
//        //****************SubCrc****************
//        int subcrcTemp = 0;
//        for (int k = subcrcIndex; k < index; k++) {
//            subcrcTemp = subcrcTemp + (senddata[k] & 0xFF);
//        }
//        senddata[index++] = (byte) (subcrcTemp & 0xFF);
//        //****************SubCrc****************
//        senddata[index++] = (byte) 0xbb;
//        LoggerUtils.d(TAG,"构建快速模式指令发出,需要过滤 AA58");
//        return buildSendData(0XAA, Arrays.copyOf(senddata, index));

        /**
         * 下面是备份旧的
         * ******************************************************************************
         */

    }

    /**
     * 解析接收的开始盘点的指令（快速模式）AA58 的
     *
     * @return return
     */
    public UHFReaderResult<Boolean> analysisStartFastModeInventoryReceiveDataMoreTag(UHFProtocolAnalysisBase.DataFrameInfo data, boolean isTID) {
        this.isTID = isTID;
        //0xFF+DATALEN+0XAA+STATUS +”Moduletech”+SubCmdHighByte+SubCmdLowByte+data+CRC
        if (data != null) {
            if (data.status == 00) {
                LoggerUtils.d(TAG, "E710开始盘点指令返回Data:" + DataConverter.bytesToHex(data.data));
                if ((data.data[10] & 0xFF) == 0xAA && (data.data[11] & 0xFF) == 0x58) {
                    return new UHFReaderResult<Boolean>(UHFReaderResult.ResultCode.CODE_SUCCESS, "", true);
                }
                //4D 6F 64 75 6C 65 74 65 63 68 AA 48
            }
        }
        return new UHFReaderResult<Boolean>(UHFReaderResult.ResultCode.CODE_FAILURE, "", false);
    }


    /**
     * 解析连续盘点数据-之前的快速模式 AA58
     *
     * @return return
     */

    public List<UHFTagEntity> analysisFastModeTagInfoReceiveDataMoreTag(UHFProtocolAnalysisBase.DataFrameInfo data ) {
        if (data != null) {
            if (data.status == 0) {

                LoggerUtils.d(TAG, "解析盘点数据："+DataConverter.bytesToHex(data.data));
                byte[] taginfo = data.data;
//                int tagsTotal = taginfo[3] & 0xFF;//标签张数
                int statIndex = 2;
                //00 06 D3 01 10 3400 300833B2DDD9014000000000 C41E
                //00 02 E1 10 3400 300833B2DDD9014000000000 C41E
                List<UHFTagEntity> list = new ArrayList<>();
                int rssi = taginfo[statIndex];   //RSSI
                LoggerUtils.d(TAG, "解析盘点数据："+statIndex++ );

                int count = taginfo[statIndex++] & 0xFF; //读取次数
                int epcLen = (taginfo[statIndex++] & 0xFF);   //EPC长度 包括:pc值+epc+epcCRC

                UHFTagEntity uhfTagEntity = new UHFTagEntity();

                LoggerUtils.d(TAG, "解析盘点数据 epcLen："+epcLen);
                LoggerUtils.d(TAG, "解析盘点数据 RSSI："+rssi);
                LoggerUtils.d(TAG, "解析盘点数据 次数："+count);
                byte[] pcBytes = new byte[]{
                        taginfo[statIndex++],
                        taginfo[statIndex++]
                };
                int epcIdLen = epcLen - 2 - 2;
                byte[] epcBytes = new byte[epcIdLen];
                for (int m = 0; m < epcIdLen; m++) {
                    epcBytes[m] = taginfo[statIndex++];
                }
                uhfTagEntity.setRssi(rssi);
                uhfTagEntity.setCount(count);
                uhfTagEntity.setEcpHex(DataConverter.bytesToHex(epcBytes));
                if (uhfTagEntity.getEcpHex() == null) {
                    uhfTagEntity.setEcpHex("");
                }
                uhfTagEntity.setPcHex(DataConverter.bytesToHex(pcBytes));
                list.add(uhfTagEntity);

                return list;
            } //00 07 00 01    01  C9 11   00 80   30 00 E2 00 00 17 01 0B 00 50 17 50 61 70 BB 55
        }
        return null;
    }

    /**
     * 解析接收的开始盘点的指令（快速模式）AA48 的
     *
     * @return return
     */
    public UHFReaderResult<Boolean> analysisStartFastModeInventoryReceiveData(UHFProtocolAnalysisBase.DataFrameInfo data, boolean isTID) {
        this.isTID=isTID;
        //0xFF+DATALEN+0XAA+STATUS +”Moduletech”+SubCmdHighByte+SubCmdLowByte+data+CRC
        if (data != null) {
            if (data.status == 00) {
                LoggerUtils.d(TAG, "开始盘点指令返回Data:" + DataConverter.bytesToHex(data.data));
                if ((data.data[10] & 0xFF) == 0xAA && (data.data[11] & 0xFF) == 0x48) {
                    return new UHFReaderResult<Boolean>(UHFReaderResult.ResultCode.CODE_SUCCESS, "", true);
                }
                //4D 6F 64 75 6C 65 74 65 63 68 AA 48
            }
        }
        return new UHFReaderResult<Boolean>(UHFReaderResult.ResultCode.CODE_FAILURE, "", false);
    }


    /**
     * 解析连续盘点数据-之前的快速模式  AA48
     *
     * @return return
     */

    public List<UHFTagEntity> analysisFastModeTagInfoReceiveDataOld(UHFProtocolAnalysisBase.DataFrameInfo data) {

//        if (data != null) {
//            if (data.status == 0) {
//                LoggerUtils.d(TAG,"解析盘点数据："+DataConverter.bytesToHex(data.data));
//                byte[] taginfo = data.data;
//                int tagsTotal = taginfo[2] & 0xFF;//标签张数
//                int statIndex = 3;
//                List<UHFTagEntity> list = new ArrayList<>();
//                for (int k = 0; k < tagsTotal; k++) {
//                    int rssi = taginfo[statIndex++];   //RSSI
//                    int ant = (taginfo[statIndex++] & 0xFF) >> 4; //天线
//                    byte[] tidBytes=null;
//                    int tidLen= ((taginfo[statIndex++] & 0xFF) << 8) | (taginfo[statIndex++] & 0xFF);//嵌入命令的数据
//                    if(tidLen>0){
//                        tidLen = (tidLen / 8);
//                        int starAdd=statIndex;
//                        int endAdd=starAdd+tidLen;
//                        tidBytes=Arrays.copyOfRange(taginfo,starAdd,endAdd);
//                        statIndex=endAdd;
//                        // LoggerUtils.d(TAG, "解析盘点数据 statIndex："+statIndex);
//                    }
//                    UHFTagEntity uhfTagEntity = new UHFTagEntity();
//                    if(tidBytes!=null){
//                        uhfTagEntity.setTidHex(DataConverter.bytesToHex(tidBytes));
//                    }else{
//                        if(isTID){
//                            continue;
//                        }
//                    }
//
//                    int epcLen = (taginfo[statIndex++] & 0xFF);   //EPC长度 包括:pc值+epc+epcCRC
//                    byte[] pcBytes = new byte[]{
//                            taginfo[statIndex++],
//                            taginfo[statIndex++]
//                    };
//                    int epcIdLen = epcLen - 2 - 2;
//                    byte[] epcBytes = new byte[epcIdLen];
//                    for (int m = 0; m < epcIdLen; m++) {
//                        epcBytes[m] = taginfo[statIndex++];
//                    }
//                    //最后有两个字节的epc校验码，所以加3，就直接跳到下一张标签
//                    statIndex = statIndex + 2;
//
//                    uhfTagEntity.setAnt(ant);
//                    uhfTagEntity.setRssi(rssi);
//                    uhfTagEntity.setCount(1);
//                    uhfTagEntity.setEcpHex(DataConverter.bytesToHex(epcBytes));
//                    if(uhfTagEntity.getEcpHex()==null){
//                        uhfTagEntity.setEcpHex("");
//                    }
//                    uhfTagEntity.setPcHex(DataConverter.bytesToHex(pcBytes));
//                    list.add(uhfTagEntity);
//                }
//                return list;
//            }
//        }

        if (data != null) {
            if (data.status == 0) {
                //f发送带过滤指令
                //FF1FAA4D6F64756C6574656368AA480007041000000000000000002018BBBB00BBBB743F
                //返回
//                00 07
//                01 标签张数
//                CF 信号强度
//                01   天线
//                10   后面数据长度  PC+EPC+CRC
//                3000
//                BBBB00000000000000000000
//                4C52

                //发送不带过滤指令
                //FF13AA4D6F64756C6574656368AA480002
                // 000003F7BB39C0

                //返回
                //0002
                //C3
                // 10
                // 3000
                // E200001D9305015418408133
                // 1AE8
                LoggerUtils.d(TAG, "解析盘点数据： = " + DataConverter.bytesToHex(data.data));
                byte[] taginfo = data.data;
                int tagsTotal = taginfo[2] & 0xFF;//标签张数

                int type = taginfo[1]& 0xFF;;//盘点类型，如果是 02就是普通盘点，07就是过滤盘点

                int statIndex = 3;
                int rssi = taginfo[statIndex] & 0xFF;//信号强度



                if (type == 2) {
                    rssi = taginfo[2] & 0xFF;//信号强度
//                    statIndex = 3;
                } else {
                    statIndex = 5;
                }
                int nowRssi = rssi - 256;
                LoggerUtils.d(TAG, "信号强度 ： = " + nowRssi);
                List<UHFTagEntity> list = new ArrayList<>();
                    UHFTagEntity uhfTagEntity = new UHFTagEntity();

                    int epcLen = (taginfo[statIndex++] & 0xFF);   //EPC长度 包括:pc值+epc+epcCRC
                    LoggerUtils.d(TAG, "解析盘点数据 epcLen："+epcLen);
                    byte[] pcBytes = new byte[]{
                            taginfo[statIndex++],
                            taginfo[statIndex++]
                    };
                    int epcIdLen = epcLen - 2 - 2;
                    byte[] epcBytes = new byte[epcIdLen];
                    for (int m = 0; m < epcIdLen; m++) {
                        epcBytes[m] = taginfo[statIndex++];
                    }

                    uhfTagEntity.setRssi(nowRssi);
                    uhfTagEntity.setCount(1);
                    uhfTagEntity.setEcpHex(DataConverter.bytesToHex(epcBytes));
                    if (uhfTagEntity.getEcpHex() == null) {
                        uhfTagEntity.setEcpHex("");
                    }
                    uhfTagEntity.setPcHex(DataConverter.bytesToHex(pcBytes));
                    list.add(uhfTagEntity);

                if (list == null ) {
                    LoggerUtils.d(TAG, "解析盘点数据异常  list.size()=" + list.size());
                }
                return list;
            }
            //00 07 00 01    01  C9 11   00 80   30 00 E2 00 00 17 01 0B 00 50 17 50 61 70 BB 55
        }
        return null;
    }

    /**
     * 获取发送盘点的指令（快速模式）
     *
     * @return return
     */
    public byte[] makeStartFastModeInventorySendDataNeedTid(SelectEntity selectEntity, boolean isTID) {
        LoggerUtils.d(TAG,"0000000000000000000000---------------" + selectEntity);
        if (!UHFReaderSLR.isR2000) {
            if (selectEntity != null) {
                if (isTID) {
                    return makeStartFastModeInventorySendDataNeedTid3(selectEntity, true);
                }
            }
        }

        if (selectEntity == null) {
            // 0xFF+DATALEN+0xAA+”Moduletech”+SubCmdHighByte+SubCmdLowByte+data+SubCrc+0xbb+CRC
            //FF+DATALEN+ 0XAA+”Moduletech”+AA+48+data+SubCrc+0xbb+CRC
            //data:N字节，2字节METADATAFLAG+1字节OPTION+2字节SEARCHFLAGS+匹配过滤相关数据（由OPTION决定，同0X22指令）+盘存嵌入命令（由SEARCHFLAGS决定，同0X22指令）
            // 1.SubCrc：1字节，为SubCmdHighByte开始到data结束的所有数据相加结果的低8位值；
            byte[] senddata = new byte[512];
            byte[] moduletech = "Moduletech".getBytes();
            //************Moduletech*************
            System.arraycopy(moduletech, 0, senddata, 0, moduletech.length);
            //***********SubCmdHighByte+SubCmdLowByte************
            int index = moduletech.length;
            int subcrcIndex = index;
            senddata[index++] = (byte) 0xAA;
            senddata[index++] = (byte) 0x48;

//            EPC+TID+USER+保留区   保留区0~3块+EPC区0~7块，TID区0~5块，用户区0~3块
//             0080 00 0004  04 1B 28 0000 00 00 00000000 04 01

//           0080 00 0004  02 0F 28 0000 00  02 00000000 06 03 00000000 43 D9 BB DB23

//            AA 48 00 80 00 00 04 02 0F 28 00 00 00 02 00 00 00 00 06 03 00 00 00 00 2B E5
//            AA 48 00 82 00 00 04 03 15 28 00 00 00 02 00 00 00 00 06 03 00 00 00 00 20 03 00 00 00 20 0B 11 BB 53 EA
            //AA 48 00 82 00 00 04 02 0F 28 00 00 00 02 00 00 00 00 06 03 00 00 00 00 20 DC BB 94 12
            senddata[index++] = (byte) 0x00;
            senddata[index++] = (byte) 0x82;
            senddata[index++] = (byte) 0x00;
            senddata[index++] = (byte) 0x00;
            senddata[index++] = (byte) 0x04;
            senddata[index++] = (byte) 0x03;

            senddata[index++] = (byte) 0x15;
            senddata[index++] = (byte) 0x28;
            senddata[index++] = (byte) 0x00;
            senddata[index++] = (byte) 0x00;
            senddata[index++] = (byte) 0x00;
            senddata[index++] = (byte) 0x02;

            senddata[index++] = (byte) 0x00;
            senddata[index++] = (byte) 0x00;
            senddata[index++] = (byte) 0x00;
            senddata[index++] = (byte) 0x00;

            senddata[index++] = (byte) 0x06;
            senddata[index++] = (byte) 0x03;


            senddata[index++] = (byte) 0x00;
            senddata[index++] = (byte) 0x00;
            senddata[index++] = (byte) 0x00;
            senddata[index++] = (byte) 0x00;
            senddata[index++] = (byte) 0x20;

            senddata[index++] = (byte) 0x03;
            senddata[index++] = (byte) 0x00;
            senddata[index++] = (byte) 0x00;
            senddata[index++] = (byte) 0x00;
            senddata[index++] = (byte) 0x20;
            senddata[index++] = (byte) 0x0B;






            /**
             * 备份保存
             * **************************************************************
             */
//
//            //************data*******************
//            //2字节METADATAFLAG
//            final int count = 0X0001;//Bit0置位即标签在盘存时间内被盘存到的次数将会返回
//            final int rssi = 0x0002;//BIT1置位即标签的RSSI信号值将会被返回
//            final int ant = 0X0004;//BIT2置位即标签 被盘存到时所用的天线 ID号将会被返回。（逻辑天线号）
//            final int tagData = 0x0080;//返回嵌入命令内存数据
//            final int flag = count | rssi | ant | tagData;
//
//            if (isTID) {
//                senddata[index++] = (flag >> 8) & 0xFF;
//                senddata[index++] = (byte) (flag & 0xFF);
//                senddata[index++] = 0x00;//不启用匹配过滤
//                //2字节SEARCHFLAGS,SEARCHFLAGS高字节的低4位表示不停止盘存过程中的停顿时间dd
//                //0x10:每工作1秒中盘存时间950毫秒，停顿时间50毫秒
//                //0x20:停顿时间100毫秒,0x30:停顿150，0x00:不停顿
//                senddata[index++] = (0x00 | 0x10);
//            } else {
//                senddata[index++] = 0x00;
//                senddata[index++] = 0x06;
//                //1字节OPTION
//                senddata[index++] = 0x00;//不启用匹配过滤
//                //2字节SEARCHFLAGS,SEARCHFLAGS高字节的低4位表示不停止盘存过程中的停顿时间dd
//                //0x10:每工作1秒中盘存时间950毫秒，停顿时间50毫秒
//                //0x20:停顿时间100毫秒,0x30:停顿150，0x00:不停顿
////            senddata[index++] = (0x00 | 0x20);
//                senddata[index++] = (byte) 0x90;//todo 0x00
//
//            }
//            if (!isTID) {
////                senddata[index++] = 0x00;
//                senddata[index++] = 0x03;
//            } else {
//                senddata[index++] = 0x04;
//                //**********************************************
//                //嵌入命令数量，目前该值只能为1.
//                senddata[index++] = (byte) 0x01;
//                //嵌入命令的数据域的字节长度。
//                senddata[index++] = (byte) 0x09;
//                //嵌入的命令码。目前只能嵌入（0X28命令）
//                senddata[index++] = (byte) 0x28;
//                //嵌入命令的数据域
//                //Emb Cmd Timeout
//                senddata[index++] = (byte) 0x00;
//                senddata[index++] = (byte) 0x00;
//                //Emb Cmd Option
//                senddata[index++] = (byte) 0x00;
//                //Read Membank
//                senddata[index++] = (byte) 0x02;
//                //Read Address
//                senddata[index++] = (byte) 0x00;
//                senddata[index++] = (byte) 0x00;
//                senddata[index++] = (byte) 0x00;
//                senddata[index++] = (byte) 0x00;
//                //Read Word Count
//                senddata[index++] = (byte) 0x06;
//            }


            /**
             * **************************************************************
             */



            //****************data****************
            //****************SubCrc****************
            int subcrcTemp = 0;
            for (int k = subcrcIndex; k < index; k++) {
                subcrcTemp = subcrcTemp + (senddata[k] & 0xFF);
            }
            senddata[index++] = (byte) (subcrcTemp & 0xFF);
            //****************SubCrc****************
            senddata[index++] = (byte) 0xbb;
            return buildSendData(0XAA, Arrays.copyOf(senddata, index));
        }

        // 0xFF+DATALEN+0xAA+”Moduletech”+SubCmdHighByte+SubCmdLowByte+data+SubCrc+0xbb+CRC
        //FF+DATALEN+ 0XAA+”Moduletech”+AA+48+data+SubCrc+0xbb+CRC
        //data:N字节，2字节METADATAFLAG+1字节OPTION+2字节SEARCHFLAGS+匹配过滤相关数据（由OPTION决定，同0X22指令）+盘存嵌入命令（由SEARCHFLAGS决定，同0X22指令）
        // 1.SubCrc：1字节，为SubCmdHighByte开始到data结束的所有数据相加结果的低8位值；
        byte[] senddata = new byte[512];
        byte[] moduletech = "Moduletech".getBytes();
        //************Moduletech*************
        System.arraycopy(moduletech, 0, senddata, 0, moduletech.length);
        //***********SubCmdHighByte+SubCmdLowByte************
        int index = moduletech.length;
        int subcrcIndex = index;
        senddata[index++] = (byte) 0xAA;
        senddata[index++] = (byte) 0x48;
        //************data*******************
        //2字节METADATAFLAG
        final int count = 0X0001;//Bit0置位即标签在盘存时间内被盘存到的次数将会返回
        final int rssi = 0x0002;//BIT1置位即标签的RSSI信号值将会被返回
        final int ant = 0X0004;//BIT2置位即标签 被盘存到时所用的天线 ID号将会被返回。（逻辑天线号）
        final int flag = count | rssi | ant;
        senddata[index++] = (flag >> 8) & 0xFF;
        senddata[index++] = flag & 0xFF;
        //1.字节OPTION
        senddata[index++] = (byte) (selectEntity.getOption());// 启用匹配过滤
        //2.字节SEARCHFLAGS,SEARCHFLAGS高字节的低4位表示不停止盘存过程中的停顿时间dd
        //0x10:每工作1秒中盘存时间950毫秒，停顿时间50毫秒
        //0x20:停顿时间100毫秒,0x30:停顿150，0x00:不停顿
        senddata[index++] = (0x00 | 0x10);
        senddata[index++] = 0x00;
        //3. 4字节AccessPassword
        senddata[index++] = 0x00;
        senddata[index++] = 0x00;
        senddata[index++] = 0x00;
        senddata[index++] = 0x00;
        //4. Select Address(bits)
        int address = selectEntity.getAddress();
        senddata[index++] = (byte) ((address >> 24) & 0xFF);
        senddata[index++] = (byte) ((address >> 16) & 0xFF);
        senddata[index++] = (byte) ((address >> 8) & 0xFF);
        senddata[index++] = (byte) (address & 0xFF);
        //Select data length(bits)
        senddata[index++] = (byte) selectEntity.getLength();
        //Select data
        byte[] byteData = DataConverter.hexToBytes(selectEntity.getData());
        int len = selectEntity.getLength() / 8;
        if (selectEntity.getLength() % 8 != 0) {
            len += 1;
        }
        for (int k = 0; k < len; k++) {
            senddata[index++] = byteData[k];
        }

        //****************data****************
        //****************SubCrc****************
        int subcrcTemp = 0;
        for (int k = subcrcIndex; k < index; k++) {
            subcrcTemp = subcrcTemp + (senddata[k] & 0xFF);
        }
        senddata[index++] = (byte) (subcrcTemp & 0xFF);
        //****************SubCrc****************
        senddata[index++] = (byte) 0xbb;
        return buildSendData(0XAA, Arrays.copyOf(senddata, index));

    }

    /**
     * 获取发送盘点的指令（快速模式）
     *
     * @return return
     */
    public byte[] makeStartFastModeInventorySendDataNeedTid512(SelectEntity selectEntity, boolean isTID) {
        LoggerUtils.d(TAG,"在这里组装数据 0000000000000000000000---------------" + selectEntity);
        if (!UHFReaderSLR.isR2000) {
            if (selectEntity != null) {
                if (isTID) {
                    return makeStartFastModeInventorySendDataNeedTid3(selectEntity, true);
                }
            }
        }

        if (selectEntity == null) {
            // 0xFF+DATALEN+0xAA+”Moduletech”+SubCmdHighByte+SubCmdLowByte+data+SubCrc+0xbb+CRC
            //FF+DATALEN+ 0XAA+”Moduletech”+AA+48+data+SubCrc+0xbb+CRC
            //data:N字节，2字节METADATAFLAG+1字节OPTION+2字节SEARCHFLAGS+匹配过滤相关数据（由OPTION决定，同0X22指令）+盘存嵌入命令（由SEARCHFLAGS决定，同0X22指令）
            // 1.SubCrc：1字节，为SubCmdHighByte开始到data结束的所有数据相加结果的低8位值；
            byte[] senddata = new byte[512];
            byte[] moduletech = "Moduletech".getBytes();
            //************Moduletech*************
            System.arraycopy(moduletech, 0, senddata, 0, moduletech.length);
            //***********SubCmdHighByte+SubCmdLowByte************
            int index = moduletech.length;
            int subcrcIndex = index;
            senddata[index++] = (byte) 0xAA;
            senddata[index++] = (byte) 0x48;

//            EPC+TID+USER+保留区   保留区0~3块+EPC区0~7块，TID区0~5块，用户区0~3块
//             0080 00 0004  04 1B 28 0000 00 00 00000000 04 01

//           0080 00 0004  02 0F 28 0000 00  02 00000000 06 03 00000000 43 D9 BB DB23

//            AA 48 00 80 00 00 04 02 0F 28 00 00 00 02 00 00 00 00 06 03 00 00 00 00 2B E5
//            AA 48 00 82 00 00 04 03 15 28 00 00 00 02 00 00 00 00 06 03 00 00 00 00 20 03 00 00 00 20 0B 11 BB 53 EA
            //AA 48 00 82 00 00 04 02 0F 28 00 00 00 02 00 00 00 00 06 03 00 00 00 00 20 DC BB 94 12
            //只盘存EPC+TID
            //FF1FAA4D6F64756C6574656368AA480082 000004 01 0928 00000002 0000000006 B2BBBE0B

            senddata[index++] = (byte) 0x00;
            senddata[index++] = (byte) 0x82;
            senddata[index++] = (byte) 0x00;
            senddata[index++] = (byte) 0x00;
            senddata[index++] = (byte) 0x04;
            senddata[index++] = (byte) 0x01;

            senddata[index++] = (byte) 0x09;
            senddata[index++] = (byte) 0x28;
            senddata[index++] = (byte) 0x00;
            senddata[index++] = (byte) 0x00;
            senddata[index++] = (byte) 0x00;
            senddata[index++] = (byte) 0x02;

            senddata[index++] = (byte) 0x00;
            senddata[index++] = (byte) 0x00;
            senddata[index++] = (byte) 0x00;
            senddata[index++] = (byte) 0x00;

            senddata[index++] = (byte) 0x06;










            /**
             * 备份保存
             * **************************************************************
             */
//
//            //************data*******************
//            //2字节METADATAFLAG
//            final int count = 0X0001;//Bit0置位即标签在盘存时间内被盘存到的次数将会返回
//            final int rssi = 0x0002;//BIT1置位即标签的RSSI信号值将会被返回
//            final int ant = 0X0004;//BIT2置位即标签 被盘存到时所用的天线 ID号将会被返回。（逻辑天线号）
//            final int tagData = 0x0080;//返回嵌入命令内存数据
//            final int flag = count | rssi | ant | tagData;
//
//            if (isTID) {
//                senddata[index++] = (flag >> 8) & 0xFF;
//                senddata[index++] = (byte) (flag & 0xFF);
//                senddata[index++] = 0x00;//不启用匹配过滤
//                //2字节SEARCHFLAGS,SEARCHFLAGS高字节的低4位表示不停止盘存过程中的停顿时间dd
//                //0x10:每工作1秒中盘存时间950毫秒，停顿时间50毫秒
//                //0x20:停顿时间100毫秒,0x30:停顿150，0x00:不停顿
//                senddata[index++] = (0x00 | 0x10);
//            } else {
//                senddata[index++] = 0x00;
//                senddata[index++] = 0x06;
//                //1字节OPTION
//                senddata[index++] = 0x00;//不启用匹配过滤
//                //2字节SEARCHFLAGS,SEARCHFLAGS高字节的低4位表示不停止盘存过程中的停顿时间dd
//                //0x10:每工作1秒中盘存时间950毫秒，停顿时间50毫秒
//                //0x20:停顿时间100毫秒,0x30:停顿150，0x00:不停顿
////            senddata[index++] = (0x00 | 0x20);
//                senddata[index++] = (byte) 0x90;//todo 0x00
//
//            }
//            if (!isTID) {
////                senddata[index++] = 0x00;
//                senddata[index++] = 0x03;
//            } else {
//                senddata[index++] = 0x04;
//                //**********************************************
//                //嵌入命令数量，目前该值只能为1.
//                senddata[index++] = (byte) 0x01;
//                //嵌入命令的数据域的字节长度。
//                senddata[index++] = (byte) 0x09;
//                //嵌入的命令码。目前只能嵌入（0X28命令）
//                senddata[index++] = (byte) 0x28;
//                //嵌入命令的数据域
//                //Emb Cmd Timeout
//                senddata[index++] = (byte) 0x00;
//                senddata[index++] = (byte) 0x00;
//                //Emb Cmd Option
//                senddata[index++] = (byte) 0x00;
//                //Read Membank
//                senddata[index++] = (byte) 0x02;
//                //Read Address
//                senddata[index++] = (byte) 0x00;
//                senddata[index++] = (byte) 0x00;
//                senddata[index++] = (byte) 0x00;
//                senddata[index++] = (byte) 0x00;
//                //Read Word Count
//                senddata[index++] = (byte) 0x06;
//            }


            /**
             * **************************************************************
             */



            //****************data****************
            //****************SubCrc****************
            int subcrcTemp = 0;
            for (int k = subcrcIndex; k < index; k++) {
                subcrcTemp = subcrcTemp + (senddata[k] & 0xFF);
            }
            senddata[index++] = (byte) (subcrcTemp & 0xFF);
            //****************SubCrc****************
            senddata[index++] = (byte) 0xbb;
            return buildSendData(0XAA, Arrays.copyOf(senddata, index));
        }

        // 0xFF+DATALEN+0xAA+”Moduletech”+SubCmdHighByte+SubCmdLowByte+data+SubCrc+0xbb+CRC
        //FF+DATALEN+ 0XAA+”Moduletech”+AA+48+data+SubCrc+0xbb+CRC
        //data:N字节，2字节METADATAFLAG+1字节OPTION+2字节SEARCHFLAGS+匹配过滤相关数据（由OPTION决定，同0X22指令）+盘存嵌入命令（由SEARCHFLAGS决定，同0X22指令）
        // 1.SubCrc：1字节，为SubCmdHighByte开始到data结束的所有数据相加结果的低8位值；
        byte[] senddata = new byte[512];
        byte[] moduletech = "Moduletech".getBytes();
        //************Moduletech*************
        System.arraycopy(moduletech, 0, senddata, 0, moduletech.length);
        //***********SubCmdHighByte+SubCmdLowByte************
        int index = moduletech.length;
        int subcrcIndex = index;
        senddata[index++] = (byte) 0xAA;
        senddata[index++] = (byte) 0x48;
        //************data*******************
        //2字节METADATAFLAG
        final int count = 0X0001;//Bit0置位即标签在盘存时间内被盘存到的次数将会返回
        final int rssi = 0x0002;//BIT1置位即标签的RSSI信号值将会被返回
        final int ant = 0X0004;//BIT2置位即标签 被盘存到时所用的天线 ID号将会被返回。（逻辑天线号）
        final int flag = count | rssi | ant;
        senddata[index++] = (flag >> 8) & 0xFF;
        senddata[index++] = flag & 0xFF;
        //1.字节OPTION
        senddata[index++] = (byte) (selectEntity.getOption());// 启用匹配过滤
        //2.字节SEARCHFLAGS,SEARCHFLAGS高字节的低4位表示不停止盘存过程中的停顿时间dd
        //0x10:每工作1秒中盘存时间950毫秒，停顿时间50毫秒
        //0x20:停顿时间100毫秒,0x30:停顿150，0x00:不停顿
        senddata[index++] = (0x00 | 0x10);
        senddata[index++] = 0x00;
        //3. 4字节AccessPassword
        senddata[index++] = 0x00;
        senddata[index++] = 0x00;
        senddata[index++] = 0x00;
        senddata[index++] = 0x00;
        //4. Select Address(bits)
        int address = selectEntity.getAddress();
        senddata[index++] = (byte) ((address >> 24) & 0xFF);
        senddata[index++] = (byte) ((address >> 16) & 0xFF);
        senddata[index++] = (byte) ((address >> 8) & 0xFF);
        senddata[index++] = (byte) (address & 0xFF);
        //Select data length(bits)
        senddata[index++] = (byte) selectEntity.getLength();
        //Select data
        byte[] byteData = DataConverter.hexToBytes(selectEntity.getData());
        int len = selectEntity.getLength() / 8;
        if (selectEntity.getLength() % 8 != 0) {
            len += 1;
        }
        for (int k = 0; k < len; k++) {
            senddata[index++] = byteData[k];
        }

        //****************data****************
        //****************SubCrc****************
        int subcrcTemp = 0;
        for (int k = subcrcIndex; k < index; k++) {
            subcrcTemp = subcrcTemp + (senddata[k] & 0xFF);
        }
        senddata[index++] = (byte) (subcrcTemp & 0xFF);
        //****************SubCrc****************
        senddata[index++] = (byte) 0xbb;
        return buildSendData(0XAA, Arrays.copyOf(senddata, index));

    }
}
