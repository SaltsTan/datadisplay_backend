package org.jeecg.modules.basic.controller;

import java.util.Arrays;
import java.util.List;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import org.jeecg.common.api.vo.Result;
import org.jeecg.common.system.query.QueryGenerator;
import org.jeecg.modules.basic.entity.BasicPaiti;
import org.jeecg.modules.basic.service.IBasicPaitiService;

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
import org.apache.shiro.authz.annotation.RequiresPermissions;

 /**
 * @Description: basic_paiti
 * @Author: jeecg-boot
 * @Date:   2025-10-04
 * @Version: V1.0
 */
@Api(tags="basic_paiti")
@RestController
@RequestMapping("/basic/basicPaiti")
@Slf4j
public class BasicPaitiController extends JeecgController<BasicPaiti, IBasicPaitiService> {
	@Autowired
	private IBasicPaitiService basicPaitiService;

	/**
	 * 分页列表查询
	 *
	 * @param basicPaiti
	 * @param pageNo
	 * @param pageSize
	 * @param req
	 * @return
	 */
	//@AutoLog(value = "basic_paiti-分页列表查询")
	@ApiOperation(value="basic_paiti-分页列表查询", notes="basic_paiti-分页列表查询")
	@GetMapping(value = "/page")
	public Result<IPage<BasicPaiti>> queryPageList(BasicPaiti basicPaiti,
								   @RequestParam(name="pageNo", defaultValue="1") Integer pageNo,
								   @RequestParam(name="pageSize", defaultValue="10") Integer pageSize,
								   HttpServletRequest req) {
		QueryWrapper<BasicPaiti> queryWrapper = QueryGenerator.initQueryWrapper(basicPaiti, req.getParameterMap());
		Page<BasicPaiti> page = new Page<BasicPaiti>(pageNo, pageSize);
		IPage<BasicPaiti> pageList = basicPaitiService.page(page, queryWrapper);
		return Result.OK(pageList);
	}

	 @ApiOperation(value="列表查询", notes="列表查询")
	 @GetMapping(value = "/list")
	 public Result<List<BasicPaiti>> list(BasicPaiti basicPaiti,
										  HttpServletRequest req) {
		 QueryWrapper<BasicPaiti> queryWrapper = QueryGenerator.initQueryWrapper(basicPaiti, req.getParameterMap());
		 List<BasicPaiti> pageList = basicPaitiService.list( queryWrapper);
		 return Result.OK(pageList);
	 }

	/**
	 *   添加
	 *
	 * @param basicPaiti
	 * @return
	 */
	@AutoLog(value = "basic_paiti-添加")
	@ApiOperation(value="basic_paiti-添加", notes="basic_paiti-添加")
	@PostMapping(value = "/add")
	public Result<String> add(@RequestBody BasicPaiti basicPaiti) {
		basicPaitiService.save(basicPaiti);
		return Result.OK("添加成功！");
	}

	/**
	 *  编辑
	 *
	 * @param basicPaiti
	 * @return
	 */
	@AutoLog(value = "basic_paiti-编辑")
	@ApiOperation(value="basic_paiti-编辑", notes="basic_paiti-编辑")
	public Result<String> edit(@RequestBody BasicPaiti basicPaiti) {
		basicPaitiService.updateById(basicPaiti);
		return Result.OK("编辑成功!");
	}

	/**
	 *   通过id删除
	 *
	 * @param id
	 * @return
	 */
	@AutoLog(value = "basic_paiti-通过id删除")
	@ApiOperation(value="basic_paiti-通过id删除", notes="basic_paiti-通过id删除")
	@DeleteMapping(value = "/delete")
	public Result<String> delete(@RequestParam(name="id",required=true) String id) {
		basicPaitiService.removeById(id);
		return Result.OK("删除成功!");
	}

	/**
	 *  批量删除
	 *
	 * @param ids
	 * @return
	 */
	@AutoLog(value = "basic_paiti-批量删除")
	@ApiOperation(value="basic_paiti-批量删除", notes="basic_paiti-批量删除")
	@DeleteMapping(value = "/deleteBatch")
	public Result<String> deleteBatch(@RequestParam(name="ids",required=true) String ids) {
		this.basicPaitiService.removeByIds(Arrays.asList(ids.split(",")));
		return Result.OK("批量删除成功!");
	}

	/**
	 * 通过id查询
	 *
	 * @param id
	 * @return
	 */
	//@AutoLog(value = "basic_paiti-通过id查询")
	@ApiOperation(value="basic_paiti-通过id查询", notes="basic_paiti-通过id查询")
	@GetMapping(value = "/queryById")
	public Result<BasicPaiti> queryById(@RequestParam(name="id",required=true) String id) {
		BasicPaiti basicPaiti = basicPaitiService.getById(id);
		if(basicPaiti==null) {
			return Result.error("未找到对应数据");
		}
		return Result.OK(basicPaiti);
	}

    /**
    * 导出excel
    *
    * @param request
    * @param basicPaiti
    */
    @RequestMapping(value = "/exportXls")
    public ModelAndView exportXls(HttpServletRequest request, BasicPaiti basicPaiti) {
        return super.exportXls(request, basicPaiti, BasicPaiti.class, "basic_paiti");
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
        return super.importExcel(request, response, BasicPaiti.class);
    }

}
