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
import org.jeecg.common.api.vo.Result;
import org.jeecg.common.system.query.QueryGenerator;
import org.jeecg.common.util.oConvertUtils;
import org.jeecg.modules.basic.entity.BasicSetting;
import org.jeecg.modules.basic.service.IBasicSettingService;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import lombok.extern.slf4j.Slf4j;

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
 * @Description: basic_setting
 * @Author: jeecg-boot
 * @Date:   2025-10-22
 * @Version: V1.0
 */
@Api(tags="basic_setting")
@RestController
@RequestMapping("/basic/basicSetting")
@Slf4j
public class BasicSettingController extends JeecgController<BasicSetting, IBasicSettingService> {
	@Autowired
	private IBasicSettingService basicSettingService;

	/**
	 * 分页列表查询
	 *
	 * @param basicSetting
	 * @param pageNo
	 * @param pageSize
	 * @param req
	 * @return
	 */
	//@AutoLog(value = "basic_setting-分页列表查询")
	@ApiOperation(value="basic_setting-分页列表查询", notes="basic_setting-分页列表查询")
	@GetMapping(value = "/list")
	public Result<IPage<BasicSetting>> queryPageList(BasicSetting basicSetting,
								   @RequestParam(name="pageNo", defaultValue="1") Integer pageNo,
								   @RequestParam(name="pageSize", defaultValue="10") Integer pageSize,
								   HttpServletRequest req) {
		QueryWrapper<BasicSetting> queryWrapper = QueryGenerator.initQueryWrapper(basicSetting, req.getParameterMap());
		Page<BasicSetting> page = new Page<BasicSetting>(pageNo, pageSize);
		IPage<BasicSetting> pageList = basicSettingService.page(page, queryWrapper);
		return Result.OK(pageList);
	}

	@GetMapping(value = "/count")
	public Result<Integer> count() {
		List<BasicSetting> list = basicSettingService.list();
		if(list.size()>0){
			BasicSetting basicSetting = list.get(0);
			Integer count = basicSetting.getAvgCount();
			return Result.OK(count);
		}
		return Result.OK(3);
	}




	/**
	 *   添加
	 *
	 * @param basicSetting
	 * @return
	 */
	@AutoLog(value = "basic_setting-添加")
	@ApiOperation(value="basic_setting-添加", notes="basic_setting-添加")
	@PostMapping(value = "/add")
	public Result<String> add(@RequestBody BasicSetting basicSetting) {
		basicSettingService.save(basicSetting);
		return Result.OK("添加成功！");
	}

	/**
	 *  编辑
	 *
	 * @param basicSetting
	 * @return
	 */
	@AutoLog(value = "basic_setting-编辑")
	@ApiOperation(value="basic_setting-编辑", notes="basic_setting-编辑")
	@RequestMapping(value = "/edit", method = {RequestMethod.PUT,RequestMethod.POST})
	public Result<String> edit(@RequestBody BasicSetting basicSetting) {
		basicSettingService.updateById(basicSetting);
		return Result.OK("编辑成功!");
	}


	 @RequestMapping(value = "/setCount", method = {RequestMethod.PUT,RequestMethod.POST})
	 public Result<String> setCount(Integer avgCount) {
		 BasicSetting basicSetting = new BasicSetting();
		 basicSetting.setId("1");
		 basicSetting.setAvgCount(avgCount);
		 basicSettingService.updateById(basicSetting);
		 return Result.OK("修改平均个数成功!");
	 }

	/**
	 *   通过id删除
	 *
	 * @param id
	 * @return
	 */
	@AutoLog(value = "basic_setting-通过id删除")
	@ApiOperation(value="basic_setting-通过id删除", notes="basic_setting-通过id删除")
	@DeleteMapping(value = "/delete")
	public Result<String> delete(@RequestParam(name="id",required=true) String id) {
		basicSettingService.removeById(id);
		return Result.OK("删除成功!");
	}

	/**
	 *  批量删除
	 *
	 * @param ids
	 * @return
	 */
	@AutoLog(value = "basic_setting-批量删除")
	@ApiOperation(value="basic_setting-批量删除", notes="basic_setting-批量删除")
	@DeleteMapping(value = "/deleteBatch")
	public Result<String> deleteBatch(@RequestParam(name="ids",required=true) String ids) {
		this.basicSettingService.removeByIds(Arrays.asList(ids.split(",")));
		return Result.OK("批量删除成功!");
	}

	/**
	 * 通过id查询
	 *
	 * @param id
	 * @return
	 */
	//@AutoLog(value = "basic_setting-通过id查询")
	@ApiOperation(value="basic_setting-通过id查询", notes="basic_setting-通过id查询")
	@GetMapping(value = "/queryById")
	public Result<BasicSetting> queryById(@RequestParam(name="id",required=true) String id) {
		BasicSetting basicSetting = basicSettingService.getById(id);
		if(basicSetting==null) {
			return Result.error("未找到对应数据");
		}
		return Result.OK(basicSetting);
	}

    /**
    * 导出excel
    *
    * @param request
    * @param basicSetting
    */
    @RequestMapping(value = "/exportXls")
    public ModelAndView exportXls(HttpServletRequest request, BasicSetting basicSetting) {
        return super.exportXls(request, basicSetting, BasicSetting.class, "basic_setting");
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
        return super.importExcel(request, response, BasicSetting.class);
    }

}
