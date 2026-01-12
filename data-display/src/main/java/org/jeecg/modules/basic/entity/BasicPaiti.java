package org.jeecg.modules.basic.entity;

import java.io.Serializable;
import java.io.UnsupportedEncodingException;
import java.util.Date;
import java.math.BigDecimal;
import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import com.baomidou.mybatisplus.annotation.TableLogic;
import lombok.Data;
import com.fasterxml.jackson.annotation.JsonFormat;
import org.springframework.format.annotation.DateTimeFormat;
import org.jeecgframework.poi.excel.annotation.Excel;
import org.jeecg.common.aspect.annotation.Dict;
import io.swagger.annotations.ApiModel;
import io.swagger.annotations.ApiModelProperty;
import lombok.EqualsAndHashCode;
import lombok.experimental.Accessors;

/**
 * @Description: basic_paiti
 * @Author: jeecg-boot
 * @Date:   2025-10-04
 * @Version: V1.0
 */
@Data
@TableName("basic_paiti")
@Accessors(chain = true)
@EqualsAndHashCode(callSuper = false)
@ApiModel(value="basic_paiti对象", description="basic_paiti")
public class BasicPaiti implements Serializable {
    private static final long serialVersionUID = 1L;

	/**id*/
	@TableId(type = IdType.ASSIGN_ID)
    @ApiModelProperty(value = "id")
    private String id;
	/**排体名称*/
	@Excel(name = "排体名称", width = 15)
    @ApiModelProperty(value = "排体名称")
    private String name;
	/**组成通道*/
	@Excel(name = "组成通道", width = 15)
    @ApiModelProperty(value = "组成通道")
    private String channels;
	/**传感器排序*/
	@Excel(name = "传感器排序", width = 15)
    @ApiModelProperty(value = "传感器排序")
    private String chnSorts;
	/**传感器xy坐标*/
	@Excel(name = "传感器xy坐标", width = 15)
    @ApiModelProperty(value = "传感器xy坐标")
    private String localtions;
	/**创建时间*/
	@JsonFormat(timezone = "GMT+8",pattern = "yyyy-MM-dd HH:mm:ss")
    @DateTimeFormat(pattern="yyyy-MM-dd HH:mm:ss")
    @ApiModelProperty(value = "创建时间")
    private Date createTime;
}
