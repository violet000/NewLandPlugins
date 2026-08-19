package com.xlzn.hcpda.uhf.entity;

import androidx.annotation.Nullable;

import java.math.BigInteger;
import java.util.Objects;

/**
 *
 * UHF 标签实体类
 */
public class UHFTagEntity implements Comparable<UHFTagEntity>{
//public class UHFTagEntity  {
    private String tidHex;
    private String ecpHex;
    private String pcHex;
    private String userHex;
    private int rssi;
    private int ant;
    private int count;


    public String getUserHex() {
        return userHex;
    }

    public void setUserHex(String userHex) {
        this.userHex = userHex;
    }

    /**
     * 获取EPC数据
     * @return 十六字节EPC数据
     */
    public String getEcpHex() {
        return ecpHex;
    }

    public void setEcpHex(String ecpHex) {
        this.ecpHex = ecpHex;
    }
    /**
     * 获取PC数据
     * @return 十六字节PC数据
     */
    public String getPcHex() {
        return pcHex;
    }

    public void setPcHex(String pcHex) {
        this.pcHex = pcHex;
    }
    /**
     * 获取RSSI数据
     * @return RSSI数据
     */
    public int getRssi() {
        return rssi;
    }

    public void setRssi(int rssi) {
        this.rssi = rssi;
    }
    /**
     * 获取ANT数据
     * @return ANT数据
     */
    public int getAnt() {
        return ant;
    }

    public void setAnt(int ant) {
        this.ant = ant;
    }
    /**
     * 获取标签次数数据
     * @return 标签次数数据
     */
    public int getCount() {
        return count;
    }

    public void setCount(int count) {
        this.count = count;
    }
    /**
     * 获取标签TID数据
     * @return 十六字节TID数据
     */
    public String getTidHex() {
        return tidHex;
    }

    public void setTidHex(String tidHex) {
        this.tidHex = tidHex;
    }

    @Override
    public boolean equals(@Nullable Object o) {
        if (this == o) return true;
        if (!(o instanceof UHFTagEntity)) return false;
        UHFTagEntity person = (UHFTagEntity) o;
        return Objects.equals(ecpHex, person.ecpHex);
    }



    @Override
    public int hashCode() {
        return Objects.hash(ecpHex);
    }

    @Override
    public int compareTo(UHFTagEntity o) {

        String ecpHex = getEcpHex();
        String ecpHex2 = o.getEcpHex();
        BigInteger one = new BigInteger(ecpHex,16);
        BigInteger two = new BigInteger(ecpHex2,16);

//        one.compareTo(two);
        return one.compareTo(two);
    }
}
