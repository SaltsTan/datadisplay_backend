package org.jeecg.modules.basic.dto;

import io.swagger.annotations.ApiModel;
import io.swagger.annotations.ApiModelProperty;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

/**
 * 传感器形变计算响应 DTO
 * @author jeecg-boot
 * @date 2025-03-09
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
@ApiModel(value = "SensorCalculationResponse", description = "传感器形变计算响应")
public class SensorCalculationResponse {

    @ApiModelProperty(value = "数据点列表", position = 1)
    private List<SensorDataPoint> dataPoints;

    @ApiModelProperty(value = "传感器总数", position = 2)
    private Integer totalSensorCount;
}
