package org.jeecg.modules.basic.controller;

import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
import java.io.IOException;
import java.io.UnsupportedEncodingException;
import java.net.URLDecoder;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

import cn.hutool.core.util.ObjectUtil;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import org.apache.commons.lang3.StringUtils;
import org.jeecg.common.api.vo.Result;
import org.jeecg.common.system.query.QueryGenerator;
import org.jeecg.common.util.oConvertUtils;
import org.jeecg.modules.basic.entity.BasicDefaultValue;
import org.jeecg.modules.basic.service.IBasicDefaultValueService;

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
import org.apache.shiro.authz.annotation.RequiresPermissions;

 /**
 * @Description: 排体默认值参考表
 * @Author: jeecg-boot
 * @Date:   2025-10-22
 * @Version: V1.0
 */
@Api(tags="排体默认值参考表")
@RestController
@RequestMapping("/basic/basicDefaultValue")
@Slf4j
public class BasicDefaultValueController extends JeecgController<BasicDefaultValue, IBasicDefaultValueService> {
	@Autowired
	private IBasicDefaultValueService basicDefaultValueService;

	/**
	 * 分页列表查询
	 *
	 * @param basicDefaultValue
	 * @param pageNo
	 * @param pageSize
	 * @param req
	 * @return
	 */
	//@AutoLog(value = "排体默认值参考表-分页列表查询")
	@ApiOperation(value="排体默认值参考表-分页列表查询", notes="排体默认值参考表-分页列表查询")
	@GetMapping(value = "/list")
	public Result<IPage<BasicDefaultValue>> queryPageList(BasicDefaultValue basicDefaultValue,
								   @RequestParam(name="pageNo", defaultValue="1") Integer pageNo,
								   @RequestParam(name="pageSize", defaultValue="10") Integer pageSize,
								   HttpServletRequest req) {
		QueryWrapper<BasicDefaultValue> queryWrapper = QueryGenerator.initQueryWrapper(basicDefaultValue, req.getParameterMap());
		Page<BasicDefaultValue> page = new Page<BasicDefaultValue>(pageNo, pageSize);
		IPage<BasicDefaultValue> pageList = basicDefaultValueService.page(page, queryWrapper);
		return Result.OK(pageList);
	}

	 @GetMapping(value = "/getDefaultTime")
	 public Result<String> queryPageList(String  paitiNo) {
		 LambdaQueryWrapper<BasicDefaultValue> wrapper = new LambdaQueryWrapper<>();
		 wrapper.eq(BasicDefaultValue::getPaitiNo,paitiNo);
		 List<BasicDefaultValue> pageList = basicDefaultValueService.list(wrapper);
		 if(pageList.size()>0){
			 BasicDefaultValue value = pageList.get(0);
			 if(ObjectUtil.isNull(value)){
				 return Result.OK("未找到对应数据");
			 }
			 String time = StringUtils.isEmpty(value.getTimeStr()) ? "2025-09-29 12:00" : value.getTimeStr();
			 return Result.OK(time);
		 }
		 return Result.OK(null);
	 }

	/**
	 *   添加
	 *
	 * @param basicDefaultValue
	 * @return
	 */
	@AutoLog(value = "排体默认值参考表-添加")
	@ApiOperation(value="排体默认值参考表-添加", notes="排体默认值参考表-添加")
	@PostMapping(value = "/add")
	public Result<String> add(@RequestBody BasicDefaultValue basicDefaultValue) {
		basicDefaultValueService.save(basicDefaultValue);
		return Result.OK("添加成功！");
	}

	/**
	 *  编辑
	 *
	 * @param basicDefaultValue
	 * @return
	 */
	@AutoLog(value = "排体默认值参考表-编辑")
	@ApiOperation(value="排体默认值参考表-编辑", notes="排体默认值参考表-编辑")
	@RequestMapping(value = "/edit", method = {RequestMethod.PUT,RequestMethod.POST})
	public Result<String> edit(@RequestBody BasicDefaultValue basicDefaultValue) {
		basicDefaultValueService.updateById(basicDefaultValue);
		return Result.OK("编辑成功!");
	}

	 @RequestMapping(value = "/updateDefaultTime", method = {RequestMethod.PUT,RequestMethod.POST})
	 public Result<String> updateDefaultTime(@RequestBody BasicDefaultValue basicDefaultValue) {
		basicDefaultValueService.update(basicDefaultValue,new LambdaQueryWrapper<BasicDefaultValue>().eq(BasicDefaultValue::getPaitiNo,basicDefaultValue.getPaitiNo()));
		 return Result.OK("编辑修改默认值成功!");
	 }


	 /**
	 *   通过id删除
	 *
	 * @param id
	 * @return
	 */
	@AutoLog(value = "排体默认值参考表-通过id删除")
	@ApiOperation(value="排体默认值参考表-通过id删除", notes="排体默认值参考表-通过id删除")
	@DeleteMapping(value = "/delete")
	public Result<String> delete(@RequestParam(name="id",required=true) String id) {
		basicDefaultValueService.removeById(id);
		return Result.OK("删除成功!");
	}

	/**
	 *  批量删除
	 *
	 * @param ids
	 * @return
	 */
	@AutoLog(value = "排体默认值参考表-批量删除")
	@ApiOperation(value="排体默认值参考表-批量删除", notes="排体默认值参考表-批量删除")
	@DeleteMapping(value = "/deleteBatch")
	public Result<String> deleteBatch(@RequestParam(name="ids",required=true) String ids) {
		this.basicDefaultValueService.removeByIds(Arrays.asList(ids.split(",")));
		return Result.OK("批量删除成功!");
	}

	/**
	 * 通过id查询
	 *
	 * @param id
	 * @return
	 */
	//@AutoLog(value = "排体默认值参考表-通过id查询")
	@ApiOperation(value="排体默认值参考表-通过id查询", notes="排体默认值参考表-通过id查询")
	@GetMapping(value = "/queryById")
	public Result<BasicDefaultValue> queryById(@RequestParam(name="id",required=true) String id) {
		BasicDefaultValue basicDefaultValue = basicDefaultValueService.getById(id);
		if(basicDefaultValue==null) {
			return Result.error("未找到对应数据");
		}
		return Result.OK(basicDefaultValue);
	}

    /**
    * 导出excel
    *
    * @param request
    * @param basicDefaultValue
    */
    @RequestMapping(value = "/exportXls")
    public ModelAndView exportXls(HttpServletRequest request, BasicDefaultValue basicDefaultValue) {
        return super.exportXls(request, basicDefaultValue, BasicDefaultValue.class, "排体默认值参考表");
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
        return super.importExcel(request, response, BasicDefaultValue.class);
    }

}
