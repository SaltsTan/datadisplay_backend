package org.jeecg.modules.upload.controller;

import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
import java.io.IOException;
import java.io.UnsupportedEncodingException;
import java.net.URLDecoder;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import org.jeecg.common.api.vo.Result;
import org.jeecg.common.system.query.QueryGenerator;
import org.jeecg.common.util.oConvertUtils;
import org.jeecg.modules.upload.entity.UploadRecord;
import org.jeecg.modules.upload.service.IUploadRecordService;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import lombok.extern.slf4j.Slf4j;

import org.jeecgframework.poi.excel.ExcelImportUtil;
import org.jeecgframework.poi.excel.def.NormalExcelConstants;
import org.jeecgframework.poi.excel.entity.ExportParams;
import org.jeecgframework.poi.excel.entity.ImportParams;
import org.jeecgframework.poi.excel.view.JeecgEntityExcelView;
import org.jeecg.common.system.base.controller.JeecgController;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.multipart.MultipartHttpServletRequest;
import org.springframework.web.servlet.ModelAndView;
import com.alibaba.fastjson.JSON;
import io.swagger.annotations.Api;
import io.swagger.annotations.ApiOperation;
import org.jeecg.common.aspect.annotation.AutoLog;

 /**
 * @Description: 文件上传记录表
 * @Author: jeecg-boot
 * @Date:   2022-01-19
 * @Version: V1.0
 */
@Api(tags="文件上传记录表")
@RestController
@RequestMapping("/uploadrecord/uploadRecord")
@Slf4j
public class UploadRecordController extends JeecgController<UploadRecord, IUploadRecordService> {
	@Autowired
	private IUploadRecordService uploadRecordService;

	/**
	 * 分页列表查询
	 *
	 * @param uploadRecord
	 * @param pageNo
	 * @param pageSize
	 * @param req
	 * @return
	 */
	@AutoLog(value = "文件上传记录表-分页列表查询")
	@ApiOperation(value="文件上传记录表-分页列表查询", notes="文件上传记录表-分页列表查询")
	@GetMapping(value = "/list")
	public Result<?> queryPageList(UploadRecord uploadRecord,
								   @RequestParam(name="pageNo", defaultValue="1") Integer pageNo,
								   @RequestParam(name="pageSize", defaultValue="10") Integer pageSize,
								   HttpServletRequest req) {
		QueryWrapper<UploadRecord> queryWrapper = QueryGenerator.initQueryWrapper(uploadRecord, req.getParameterMap());
		Page<UploadRecord> page = new Page<UploadRecord>(pageNo, pageSize);
		IPage<UploadRecord> pageList = uploadRecordService.page(page, queryWrapper);
		return Result.OK(pageList);
	}

	/**
	 *   添加
	 *
	 * @param uploadRecord
	 * @return
	 */
	@AutoLog(value = "文件上传记录表-添加")
	@ApiOperation(value="文件上传记录表-添加", notes="文件上传记录表-添加")
	@PostMapping(value = "/add")
	public Result<?> add(@RequestBody UploadRecord uploadRecord) {
		uploadRecordService.save(uploadRecord);
		return Result.OK("添加成功！");
	}

	/**
	 *  编辑
	 *
	 * @param uploadRecord
	 * @return
	 */
	@AutoLog(value = "文件上传记录表-编辑")
	@ApiOperation(value="文件上传记录表-编辑", notes="文件上传记录表-编辑")
	@PutMapping(value = "/edit")
	public Result<?> edit(@RequestBody UploadRecord uploadRecord) {
		uploadRecordService.updateById(uploadRecord);
		return Result.OK("编辑成功!");
	}

	/**
	 *   通过id删除
	 *
	 * @param id
	 * @return
	 */
	@AutoLog(value = "文件上传记录表-通过id删除")
	@ApiOperation(value="文件上传记录表-通过id删除", notes="文件上传记录表-通过id删除")
	@DeleteMapping(value = "/delete")
	public Result<?> delete(@RequestParam(name="id",required=true) String id) {
		uploadRecordService.removeById(id);
		return Result.OK("删除成功!");
	}

	/**
	 *  批量删除
	 *
	 * @param ids
	 * @return
	 */
	@AutoLog(value = "文件上传记录表-批量删除")
	@ApiOperation(value="文件上传记录表-批量删除", notes="文件上传记录表-批量删除")
	@DeleteMapping(value = "/deleteBatch")
	public Result<?> deleteBatch(@RequestParam(name="ids",required=true) String ids) {
		this.uploadRecordService.removeByIds(Arrays.asList(ids.split(",")));
		return Result.OK("批量删除成功!");
	}

	/**
	 * 通过id查询
	 *
	 * @param id
	 * @return
	 */
	@AutoLog(value = "文件上传记录表-通过id查询")
	@ApiOperation(value="文件上传记录表-通过id查询", notes="文件上传记录表-通过id查询")
	@GetMapping(value = "/queryById")
	public Result<?> queryById(@RequestParam(name="id",required=true) String id) {
		UploadRecord uploadRecord = uploadRecordService.getById(id);
		if(uploadRecord==null) {
			return Result.error("未找到对应数据");
		}
		return Result.OK(uploadRecord);
	}

    /**
    * 导出excel
    *
    * @param request
    * @param uploadRecord
    */
    @RequestMapping(value = "/exportXls")
    public ModelAndView exportXls(HttpServletRequest request, UploadRecord uploadRecord) {
        return super.exportXls(request, uploadRecord, UploadRecord.class, "文件上传记录表");
    }

    /**
      * 通过excel导入数据
    *
    * @param request
    * @param response
    * @return
    */
    @RequestMapping(value = "/importExcel", method = RequestMethod.POST)
    public Result<?> importExcel(HttpServletRequest request, HttpServletResponse response) {
        return super.importExcel(request, response, UploadRecord.class);
    }

}
