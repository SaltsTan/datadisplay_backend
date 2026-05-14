package org.jeecg.modules.basic.controller;

import io.swagger.annotations.Api;
import io.swagger.annotations.ApiOperation;
import org.jeecg.common.api.vo.Result;
import org.jeecg.modules.basic.dto.PointDTO;
import org.jeecg.modules.basic.dto.SensorCalculationRequest;
import org.jeecg.modules.basic.job.DeformationDataPushJob;
import org.jeecg.modules.basic.service.ISensorCalculationService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

/**
 * 专门用于提供形变与应变计算数据的接口
 */
@RestController
@RequestMapping("/deformation")
@Api(tags="DeformationController")
public class DeformationController {

    @Autowired
    private ISensorCalculationService sensorCalculationService;

    @Autowired
    private DeformationDataPushJob deformationDataPushJob;

    /**
     * 1. 获取指定单个排体的形变数据
     * 返回结构: [ {x:0, y:0, strain:..., deformation:...}, ... ]
     * 
     * @deprecated 该接口已被 /surface/single 替代，建议使用新的曲面可视化接口
     * @see org.jeecg.modules.basic.controller.SurfaceGenerationController#single
     */
    @Deprecated
    @PostMapping("/calculateSingle")
    @ApiOperation(value="单个排体数据(已废弃)", notes="该接口已废弃，请使用 /surface/single 获取曲面数据")
    public Result<List<PointDTO>> calculateSingle(@RequestBody SensorCalculationRequest request) {
        if (request.getTime1() == null || request.getTime2() == null || request.getRowBody() == null) {
            return Result.error("参数不完整，必须传入 time1, time2, rowBody");
        }

        try {
            List<PointDTO> data = sensorCalculationService.calculateSingleRowDeformation(
                    request.getTime1(), request.getTime2(), request.getRowBody());
            return Result.OK("计算成功", data);
        } catch (Exception e) {
            e.printStackTrace();
            return Result.error("计算异常: " + e.getMessage());
        }
    }

    /**
     * 2. 获取全部(19~25号)排体的形变数据 (新增需求)
     * 返回结构: { "19": [{x:0...}...], "20": [{x:0...}...] }
     * 
     * @deprecated 该接口已被 /surface/all 替代，建议使用新的曲面可视化接口
     * @see org.jeecg.modules.basic.controller.SurfaceGenerationController#all
     */
    @Deprecated
    @PostMapping("/calculateAll")
    @ApiOperation(value="排体数据(已废弃)", notes="该接口已废弃，请使用 /surface/all 获取曲面数据")
    public Result<Map<String, List<PointDTO>>> calculateAll(@RequestBody SensorCalculationRequest request) {
        if (request.getTime1() == null || request.getTime2() == null) {
            return Result.error("参数不完整，必须传入 time1, time2");
        }

        try {
            Map<String, List<PointDTO>> mapData = sensorCalculationService.calculateAllRowsDeformation(
                    request.getTime1(), request.getTime2());
            return Result.OK("计算成功", mapData);
        } catch (Exception e) {
            e.printStackTrace();
            return Result.error("全部排体计算异常: " + e.getMessage());
        }
    }

    @PostMapping("/send")
    @ApiOperation(value="主动推送", notes="主动推送")
    public Result<String> send(String dateStr) {
        deformationDataPushJob.executePushDataTask(dateStr);
        return Result.OK("执行推送成功!");
    }

    @PostMapping("/batchSend")
    @ApiOperation(value="主动批量推送", notes="主动批量推送")
    public Result<String> batchSend(String startDateStr, String endDateStr) {
        deformationDataPushJob.executePushDataTaskForRange(startDateStr, endDateStr);
        return Result.OK("执行推送成功!");
    }
}