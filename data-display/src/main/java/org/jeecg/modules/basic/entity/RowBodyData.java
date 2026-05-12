package org.jeecg.modules.basic.entity;

import io.swagger.annotations.ApiModel;
import io.swagger.annotations.ApiModelProperty;
import lombok.Data;

import java.util.Map;

/**
 * 排体数据结构（用于 JSON 解析）
 * @author jeecg-boot
 * @date 2025-03-09
 */
@Data
@ApiModel(value = "RowBodyData", description = "排体数据结构")
public class RowBodyData {

    @ApiModelProperty(value = "排体描述")
    private String description;

    @ApiModelProperty(value = "通道数据 Map<通道名，通道坐标>")
    private Map<String, SensorChannel> channels;

    @ApiModelProperty(value = "统计信息")
    private Map<String, Object> 总计;
}
