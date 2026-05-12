package org.jeecg.modules.basic.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import io.swagger.annotations.ApiModel;
import io.swagger.annotations.ApiModelProperty;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.experimental.Accessors;

import java.io.Serializable;
import java.util.List;

/**
 * 排体传感器坐标实体
 * @author jeecg-boot
 * @date 2025-03-09
 */
@Data
@TableName("sensor_position")
@Accessors(chain = true)
@EqualsAndHashCode(callSuper = false)
@ApiModel(value = "SensorPosition", description = "排体传感器坐标")
public class SensorPosition implements Serializable {

    private static final long serialVersionUID = 1L;

    @TableId(type = IdType.ASSIGN_ID)
    @ApiModelProperty(value = "主键 ID")
    private String id;

    @ApiModelProperty(value = "排体号", required = true)
    private String rowBodyNo;

    @ApiModelProperty(value = "通道号", required = true)
    private String channelNo;

    @ApiModelProperty(value = "通道描述")
    private String description;

    @ApiModelProperty(value = "X 坐标 (垂直河道方向)")
    private Double x;

    @ApiModelProperty(value = "Y 坐标 (河道方向)")
    private Double y;

    @ApiModelProperty(value = "点位索引")
    private Integer pointIndex;

    @ApiModelProperty(value = "是否备用点", example = "false")
    private Boolean isBackup;
}
