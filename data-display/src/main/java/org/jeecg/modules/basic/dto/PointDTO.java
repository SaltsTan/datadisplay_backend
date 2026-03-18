package org.jeecg.modules.basic.dto;

import io.swagger.annotations.ApiModel;
import io.swagger.annotations.ApiModelProperty;
import lombok.Data;
import java.io.Serializable;

@Data
@ApiModel(value = "传感器虚拟对象")
public class PointDTO implements Serializable {
    private static final long serialVersionUID = 1L;

    @ApiModelProperty(name ="排体号" )
    private String rowBody;      // 排体号 (如 "23")
    @ApiModelProperty(name ="通道号" )
    private String channel;      // 通道号 (如 "CH15", 拆分后可能为 "CH15_S1")
    @ApiModelProperty(name ="点位索引" )
    private int pointIndex;      // 重置后的点位索引，用于前端连线

    private double x;            // 经过空间滤波、平移后的X坐标
    private double y;            // Y坐标
    private double z;            // 暂存原始波长差，方便前端兼容
    @ApiModelProperty(name ="微应变" )
    private double strain;       // 去噪并扣除基准后的微应变 (με)
    @ApiModelProperty(name ="形变(mm)" )
    private double deformation;  // 反演计算得到的相对形变 (mm)

    // === 新增：真实的经纬度坐标 ===
    private Double lon;          // 经度 (Longitude)
    private Double lat;          // 纬度 (Latitude)
    // 辅助字段：原始坐标，用于和基准排体进行空间匹配（不会序列化返回给前端）
    private transient double originalX;
    private transient double originalY;
}