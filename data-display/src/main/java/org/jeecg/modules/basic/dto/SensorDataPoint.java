package org.jeecg.modules.basic.dto;

import io.swagger.annotations.ApiModel;
import io.swagger.annotations.ApiModelProperty;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * 传感器数据点 DTO
 * @author jeecg-boot
 * @date 2025-03-09
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
@ApiModel(value = "SensorDataPoint", description = "传感器数据点")
public class SensorDataPoint {

    @ApiModelProperty(value = "X 坐标 (垂直河道方向)", position = 1)
    private Double x;

    @ApiModelProperty(value = "Y 坐标 (河道方向)", position = 2)
    private Double y;

    @ApiModelProperty(value = "微应变 (με)", position = 3)
    private Double strain;

    @ApiModelProperty(value = "形变 (mm)", position = 4)
    private Double deformation;

    @ApiModelProperty(value = "通道名称", position = 5)
    private String channel;

    @ApiModelProperty(value = "点位索引", position = 6)
    private Integer pointIndex;
}
