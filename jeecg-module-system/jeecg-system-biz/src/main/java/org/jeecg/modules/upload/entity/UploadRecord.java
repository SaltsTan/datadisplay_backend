package org.jeecg.modules.upload.entity;

import java.io.Serializable;
import java.io.UnsupportedEncodingException;
import java.util.Date;
import java.math.BigDecimal;
import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
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
 * @Description: 文件上传记录表
 * @Author: jeecg-boot
 * @Date:   2022-01-19
 * @Version: V1.0
 */
@Data
@TableName("sys_upload_record")
@Accessors(chain = true)
@EqualsAndHashCode(callSuper = false)
@ApiModel(value="upload_record对象", description="文件上传记录表")
public class UploadRecord implements Serializable {
    private static final long serialVersionUID = 1L;

	/**主键*/
	@TableId(type = IdType.ASSIGN_ID)
    @ApiModelProperty(value = "主键")
    private String id;
	/**创建人*/
    @ApiModelProperty(value = "创建人")
    private String createBy;
	/**创建日期*/
	@JsonFormat(timezone = "GMT+8",pattern = "yyyy-MM-dd HH:mm:ss")
    @DateTimeFormat(pattern="yyyy-MM-dd HH:mm:ss")
    @ApiModelProperty(value = "创建日期")
    private Date createTime;
	/**更新人*/
    @ApiModelProperty(value = "更新人")
    private String updateBy;
	/**更新日期*/
	@JsonFormat(timezone = "GMT+8",pattern = "yyyy-MM-dd HH:mm:ss")
    @DateTimeFormat(pattern="yyyy-MM-dd HH:mm:ss")
    @ApiModelProperty(value = "更新日期")
    private Date updateTime;
	/**文件名*/
	@Excel(name = "文件名", width = 15)
    @ApiModelProperty(value = "文件名")
    private String fileName;
	/**文件后缀*/
	@Excel(name = "文件后缀", width = 15)
    @ApiModelProperty(value = "文件后缀")
    private String suffix;
	/**文件大小*/
	@Excel(name = "文件大小", width = 15)
    @ApiModelProperty(value = "文件大小")
    private String size;
	/**文件用途*/
	@Excel(name = "文件用途", width = 15)
    @ApiModelProperty(value = "文件用途")
    private String fileUsage;
	/**关联id*/
	@Excel(name = "关联id", width = 15)
    @ApiModelProperty(value = "关联id")
    private String foreignId;
	/**保存路径*/
	@Excel(name = "保存路径", width = 15)
    @ApiModelProperty(value = "保存路径")
    private String savePath;
	@Excel(name = "md5", width = 15)
    @ApiModelProperty(value = "md5")
    private String md5;
	@Excel(name = "文件上传状态", width = 15)
    @ApiModelProperty(value = "文件上传状态(0:未开始，1:正在上传,2:上传完成)")
    private String status;
}
