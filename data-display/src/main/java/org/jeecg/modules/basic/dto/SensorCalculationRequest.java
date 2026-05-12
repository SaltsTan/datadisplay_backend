package org.jeecg.modules.basic.dto;

import io.swagger.annotations.ApiModel;
import io.swagger.annotations.ApiModelProperty;
import lombok.Data;

@Data
@ApiModel(description = "查询参数")
public class SensorCalculationRequest {
    /**
     * 时间点1 (基准时间)
     * 格式: yyyy-MM-dd HH:mm:ss
     */
    @ApiModelProperty(name = "时间点1")
    private String time1;

    /**
     * 时间点2 (当前时间)
     * 格式: yyyy-MM-dd HH:mm:ss
     */
    @ApiModelProperty(name = "时间点2")
    private String time2;

    /**
     * 排体号，如 "23"
     * 在单排体查询接口中必须传入
     */
    @ApiModelProperty(name = "排体号")
    private String rowBody;
}