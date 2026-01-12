package org.jeecg.common.constant;

/**
 * 设备相关类型常量
 */
public interface DeviceConstant {


    /**
     * 设施
     */
    String DEVICE_STYLE_FACILITY = "2";

    /**
     * 设备
     */
    String DEVICE_STYLE_DEVICE = "1";

    /**
     * 设备在线
     */
    String DEVICE_ONLINE = "1";

    /**
     * 设备下线
     */
    String DEVICE_OUTLINE = "0";

    /**
     * 室内设备
     */
    Integer DEVICE_LOCATION_IN = 2;

    /**
     * 室外设备
     */
    Integer DEVICE_LOCATION_OUT = 1;

    /**
     * 设备类型-人行通道
     */
    String DEVICE_TYPE_PEDESTRIAN_PASSAGE = "004";

    /**
     * 设备类型-车行通道
     */
    String DEVICE_TYPE_VEHICLE_PASSAGE = "005";

    /**
     * 设备类型-电子巡更-nfc卡片
     */
    String DEVICE_TYPE_NFC_CARD = "018001";

    /**
     * 设备用途-车辆通行
     */
    String Device_USE_VEHICLE_PASS = "003";

    /**
     * 设备类型-车行道闸
     */
    String DEVICE_TYPE_BARRIER_GATE = "007001";

}
