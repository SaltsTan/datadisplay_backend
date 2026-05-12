package org.jeecg.modules.basic.entity;

import io.swagger.annotations.ApiModel;
import io.swagger.annotations.ApiModelProperty;
import lombok.Data;

import java.util.List;
import java.util.Map;

/**
 * 传感器通道坐标信息（用于 JSON 解析）
 * @author jeecg-boot
 * @date 2025-03-09
 */
@Data
@ApiModel(value = "SensorChannel", description = "传感器通道坐标信息")
public class SensorChannel {

    @ApiModelProperty(value = "通道描述")
    private String description;

    @ApiModelProperty(value = "坐标数组 [[x1,y1], [x2,y2], ...]")
    private List<List<Integer>> coordinates;

    @ApiModelProperty(value = "备用坐标")
    private List<List<Integer>> beiyong;
}
