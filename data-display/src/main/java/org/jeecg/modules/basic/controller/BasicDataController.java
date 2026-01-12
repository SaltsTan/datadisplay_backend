package org.jeecg.modules.basic.controller;

import java.util.Arrays;
import java.util.List;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

import org.apache.commons.lang3.StringUtils;
import org.jeecg.common.api.vo.Result;
import org.jeecg.common.system.query.QueryGenerator;
import org.jeecg.modules.basic.entity.BasicData;
import org.jeecg.modules.basic.service.IBasicDataService;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import lombok.extern.slf4j.Slf4j;

import org.jeecg.common.system.base.controller.JeecgController;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.ModelAndView;
import io.swagger.annotations.Api;
import io.swagger.annotations.ApiOperation;
import org.jeecg.common.aspect.annotation.AutoLog;

/**
 * @Description: basic_data
 * @Author: jeecg-boot
 * @Date:   2025-10-04
 * @Version: V1.0
 */
@Api(tags="basic_data")
@RestController
@RequestMapping("/basic/basicData")
@Slf4j
public class BasicDataController extends JeecgController<BasicData, IBasicDataService> {
	@Autowired
	private IBasicDataService basicDataService;

	/**
	 * 分页列表查询
	 *
	 * @param basicData
	 * @param pageNo
	 * @param pageSize
	 * @param req
	 * @return
	 */
	//@AutoLog(value = "basic_data-分页列表查询")
	@ApiOperation(value="basic_data-分页列表查询", notes="basic_data-分页列表查询")
	@GetMapping(value = "/list")
	public Result<IPage<BasicData>> queryPageList(BasicData basicData,
								   @RequestParam(name="pageNo", defaultValue="1") Integer pageNo,
								   @RequestParam(name="pageSize", defaultValue="10") Integer pageSize,
								   HttpServletRequest req) {
		QueryWrapper<BasicData> queryWrapper = QueryGenerator.initQueryWrapper(basicData, req.getParameterMap());
		Page<BasicData> page = new Page<BasicData>(pageNo, pageSize);
		IPage<BasicData> pageList = basicDataService.page(page, queryWrapper);
		return Result.OK(pageList);
	}


	 /**
	  *
	  * @return
	  */
	 //@AutoLog(value = "basic_data-分页列表查询")
	 @ApiOperation(value="根据时间节点和排体查询", notes="根据时间节点和排体查询")
	 @GetMapping(value = "/dataQuery")
	 public Result<List<BasicData>> dataQuery(@RequestParam(name="channels", required=true) String channels,
												   @RequestParam(name="time") String time,
												   HttpServletRequest req) {
		 if(StringUtils.isEmpty( channels)){
			 return Result.error("请选择排体！");
		 }
		 List<String> list = Arrays.asList(channels.split(","));
		 List<BasicData> pageList = basicDataService.channelDataList(list,time);
		 return Result.OK(pageList);
	 }

	 @ApiOperation(value="查询时间点列表")
	 @GetMapping(value = "/timeQuery")
	 public Result<IPage<String>> timeQuery(@RequestParam(name="channels", required=true) String channels,
												   @RequestParam(name="pageNo", defaultValue="1") Integer pageNo,
												   @RequestParam(name="pageSize", defaultValue="20") Integer pageSize,
												   HttpServletRequest req) {
		 if(StringUtils.isEmpty( channels)){
			 return Result.error("请选择排体！");
		 }
		 Page<String> page = new Page<>(pageNo, pageSize);
		 List<String> list = Arrays.asList(channels.split(","));
		 IPage<String> pageList = basicDataService.timeQuery(page, list);
		 return Result.OK(pageList);
	 }

	/**
	 *   添加
	 *
	 * @param basicData
	 * @return
	 */
	@AutoLog(value = "basic_data-添加")
	@ApiOperation(value="basic_data-添加", notes="basic_data-添加")
	@PostMapping(value = "/add")
	public Result<String> add(@RequestBody BasicData basicData) {
		basicDataService.save(basicData);
		return Result.OK("添加成功！");
	}

	/**
	 *  编辑
	 *
	 * @param basicData
	 * @return
	 */
	@AutoLog(value = "basic_data-编辑")
	@ApiOperation(value="basic_data-编辑", notes="basic_data-编辑")
	@RequestMapping(value = "/edit", method = {RequestMethod.PUT,RequestMethod.POST})
	public Result<String> edit(@RequestBody BasicData basicData) {
		basicDataService.updateById(basicData);
		return Result.OK("编辑成功!");
	}

	/**
	 *   通过id删除
	 *
	 * @param id
	 * @return
	 */
	@AutoLog(value = "basic_data-通过id删除")
	@ApiOperation(value="basic_data-通过id删除", notes="basic_data-通过id删除")
	@DeleteMapping(value = "/delete")
	public Result<String> delete(@RequestParam(name="id",required=true) String id) {
		basicDataService.removeById(id);
		return Result.OK("删除成功!");
	}

	/**
	 *  批量删除
	 *
	 * @param ids
	 * @return
	 */
	@AutoLog(value = "basic_data-批量删除")
	@ApiOperation(value="basic_data-批量删除", notes="basic_data-批量删除")
	@DeleteMapping(value = "/deleteBatch")
	public Result<String> deleteBatch(@RequestParam(name="ids",required=true) String ids) {
		this.basicDataService.removeByIds(Arrays.asList(ids.split(",")));
		return Result.OK("批量删除成功!");
	}

	/**
	 * 通过id查询
	 *
	 * @param id
	 * @return
	 */
	//@AutoLog(value = "basic_data-通过id查询")
	@ApiOperation(value="basic_data-通过id查询", notes="basic_data-通过id查询")
	@GetMapping(value = "/queryById")
	public Result<BasicData> queryById(@RequestParam(name="id",required=true) String id) {
		BasicData basicData = basicDataService.getById(id);
		if(basicData==null) {
			return Result.error("未找到对应数据");
		}
		return Result.OK(basicData);
	}

    /**
    * 导出excel
    *
    * @param request
    * @param basicData
    */
    @RequestMapping(value = "/exportXls")
    public ModelAndView exportXls(HttpServletRequest request, BasicData basicData) {
        return super.exportXls(request, basicData, BasicData.class, "basic_data");
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
        return super.importExcel(request, response, BasicData.class);
    }

}
