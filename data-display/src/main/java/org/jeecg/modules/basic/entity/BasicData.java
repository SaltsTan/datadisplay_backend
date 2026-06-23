package org.jeecg.modules.basic.entity;

import java.io.Serializable;
import java.math.BigDecimal;
import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;
import org.jeecgframework.poi.excel.annotation.Excel;
import io.swagger.annotations.ApiModel;
import io.swagger.annotations.ApiModelProperty;
import lombok.EqualsAndHashCode;
import lombok.experimental.Accessors;

/**
 * @Description: basic_data
 * @Author: jeecg-boot
 * @Date:   2025-10-04
 * @Version: V1.0
 */
@Data
@TableName(value = "wavedata")
@Accessors(chain = true)
@EqualsAndHashCode(callSuper = false)
public class BasicData implements Serializable {
    private static final long serialVersionUID = 1L;

	/**index*/
	@TableId(value = "`Index`", type = IdType.AUTO)
	@Excel(name = "index", width = 15)
    @ApiModelProperty(value = "index")
    private String index;
	/**time*/
	@Excel(name = "time", width = 20)
    @ApiModelProperty(value = "time")
    private String time;
	/**deviceid*/
	@Excel(name = "deviceid", width = 15)
    @ApiModelProperty(value = "deviceid")
    private String deviceid;
	/**channel*/
	@Excel(name = "channel", width = 15)
    @ApiModelProperty(value = "channel")
    private Integer channel;
	/**number*/
	@Excel(name = "number", width = 15)
    @ApiModelProperty(value = "number")
    private Integer number;
	/**distance*/
	@Excel(name = "distance", width = 15)
    @ApiModelProperty(value = "distance")
    private String distance;
	/**wavelength*/
	@Excel(name = "wavelength", width = 15)
    @ApiModelProperty(value = "wavelength")
    private String wavelength;
	/**temperature*/
	@Excel(name = "temperature", width = 15)
    @ApiModelProperty(value = "temperature")
    private String temperature;
	/**strain*/
	@Excel(name = "strain", width = 15)
    @ApiModelProperty(value = "strain")
    private String strain;
	/**temp*/
	@Excel(name = "temp", width = 15)
    @ApiModelProperty(value = "temp")
    private String temp;
}
