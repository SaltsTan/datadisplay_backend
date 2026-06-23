package org.jeecg.modules.basic.controller;

import io.swagger.annotations.Api;
import io.swagger.annotations.ApiOperation;
import org.jeecg.common.api.vo.Result;
import org.jeecg.modules.basic.dto.DeformationSeriesDTO;
import org.jeecg.modules.basic.dto.SensorRawDataDTO;
import org.jeecg.modules.basic.dto.SurfaceRequestDTO;
import org.jeecg.modules.basic.service.ISegmentedSurfaceService;
import org.jeecg.modules.basic.service.ISurfaceGenerationService;
import org.jeecg.modules.basic.vo.SurfaceMeshDTO;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.*;

import java.util.Arrays;
import java.util.List;
import java.util.Map;

/**
 * 曲面生成接口
 * 提供单排体和全部排体的三维空间曲面网格数据
 *
 * @author Senior Developer
 * @date 2026-05-12
 */
@RestController
@RequestMapping("/surface")
@Api(tags = "SurfaceGenerationController")
public class SurfaceGenerationController {

    @Autowired
    private ISurfaceGenerationService surfaceGenerationService;

    @Autowired
    private ISegmentedSurfaceService segmentedSurfaceService;


    /**
     * 分段反演单排体曲面数据
     */
    @PostMapping("/segmented/single")
    @ApiOperation(value = "分段反演单排体曲面数据", notes = "使用分段管道获取指定排体的三维曲面网格数据")
    public Result<SurfaceMeshDTO> segmentedSingle(@RequestBody SurfaceRequestDTO request) {
        if (request.getTime1() == null || request.getTime2() == null || request.getRowBody() == null) {
            return Result.error("参数不完整，必须传入 time1, time2, rowBody");
        }

        String rowBody = request.getRowBody();
        if (!Arrays.asList("19", "20", "21", "22", "23", "24", "25").contains(rowBody)) {
            return Result.error("排体号无效，必须在 19~25 范围内");
        }

        try {
            SurfaceMeshDTO data = segmentedSurfaceService.generateSegmentedSurface(
                    request.getTime1(), request.getTime2(), rowBody);
            if (data == null) {
                return Result.error("未获取到排体 " + rowBody + " 的数据");
            }
            return Result.OK("计算成功", data);
        } catch (Exception e) {
            e.printStackTrace();
            return Result.error("分段曲面生成异常: " + e.getMessage());
        }
    }

    /**
     * 分段反演全部排体曲面数据
     */
    @PostMapping("/segmented/all")
    @ApiOperation(value = "分段反演全部排体曲面数据", notes = "使用分段管道获取全部排体(19~25)的三维曲面网格数据")
    public Result<Map<String, SurfaceMeshDTO>> segmentedAll(@RequestBody SurfaceRequestDTO request) {
        if (request.getTime1() == null || request.getTime2() == null) {
            return Result.error("参数不完整，必须传入 time1, time2");
        }

        try {
            Map<String, SurfaceMeshDTO> data = segmentedSurfaceService.generateAllSegmentedSurfaces(
                    request.getTime1(), request.getTime2());
            return Result.OK("计算成功", data);
        } catch (Exception e) {
            e.printStackTrace();
            return Result.error("全部分段曲面生成异常: " + e.getMessage());
        }
    }

    /**
     * 导出全部排体(19~25)的原始波长和应变数据
     */
    @PostMapping("/export/strain")
    @ApiOperation(value = "导出原始波长和应变数据", notes = "导出全部排体截取后的原始波长、波长差值和应变值")
    public Result<Map<String, List<SensorRawDataDTO>>> exportStrain(@RequestBody SurfaceRequestDTO request) {
        if (request.getTime1() == null || request.getTime2() == null) {
            return Result.error("参数不完整，必须传入 time1, time2");
        }

        try {
            Map<String, List<SensorRawDataDTO>> data = segmentedSurfaceService.exportStrainData(
                    request.getTime1(), request.getTime2());
            if (data == null || data.isEmpty()) {
                return Result.error("未获取到数据");
            }
            return Result.OK("查询成功", data);
        } catch (Exception e) {
            e.printStackTrace();
            return Result.error("导出数据异常: " + e.getMessage());
        }
    }

    /**
     * 导出形变时间序列数据
     */
    @PostMapping("/export/deformation-series")
    @ApiOperation(value = "导出形变时间序列", notes = "从time1次日12:00到time2当天12:00，每天12:00与time1做形变计算")
    public Result<DeformationSeriesDTO> exportDeformationSeries(@RequestBody SurfaceRequestDTO request) {
        if (request.getTime1() == null || request.getTime2() == null) {
            return Result.error("参数不完整，必须传入 time1, time2");
        }

        try {
            DeformationSeriesDTO data = segmentedSurfaceService.exportDeformationSeries(
                    request.getTime1(), request.getTime2());
            if (data == null || data.getData() == null || data.getData().isEmpty()) {
                return Result.error("未获取到形变数据");
            }
            return Result.OK("计算成功", data);
        } catch (Exception e) {
            e.printStackTrace();
            return Result.error("形变序列计算异常: " + e.getMessage());
        }
    }
}
