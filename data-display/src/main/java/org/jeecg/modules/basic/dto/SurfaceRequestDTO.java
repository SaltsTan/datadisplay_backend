package org.jeecg.modules.basic.dto;

import io.swagger.annotations.ApiModel;
import io.swagger.annotations.ApiModelProperty;
import lombok.Data;

/**
 * 曲面生成请求参数
 * @author Senior Developer
 * @date 2026-05-12
 */
@Data
@ApiModel(description = "曲面生成请求参数")
public class SurfaceRequestDTO {

    /**
     * 时间点1 (基准时间)
     * 格式: yyyy-MM-dd HH:mm:ss
     */
    @ApiModelProperty(value = "基准时间点1", required = true)
    private String time1;

    /**
     * 时间点2 (当前时间)
     * 格式: yyyy-MM-dd HH:mm:ss
     */
    @ApiModelProperty(value = "当前时间点2", required = true)
    private String time2;

    /**
     * 排体号，如 "19"~"25"
     * 单排体查询时必传
     */
    @ApiModelProperty(value = "排体号")
    private String rowBody;
}
