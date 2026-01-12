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
 * @Description: 排体默认值参考表
 * @Author: jeecg-boot
 * @Date:   2025-10-22
 * @Version: V1.0
 */
@Data
@TableName("basic_default_value")
@Accessors(chain = true)
@EqualsAndHashCode(callSuper = false)
@ApiModel(value="basic_default_value对象", description="排体默认值参考表")
public class BasicDefaultValue implements Serializable {
    private static final long serialVersionUID = 1L;

	/**主键*/
	@TableId(type = IdType.ASSIGN_ID)
    @ApiModelProperty(value = "主键")
    private String id;
	/**时间节点*/
	@Excel(name = "时间节点", width = 15)
    @ApiModelProperty(value = "时间节点")
    private String timeStr;
	/**排体号*/
	@Excel(name = "排体号", width = 15)
    @ApiModelProperty(value = "排体号")
    private String paitiNo;
}
