package org.jeecg.modules.basic.service;

import org.jeecg.modules.basic.dto.DeformationSeriesDTO;
import org.jeecg.modules.basic.dto.SensorRawDataDTO;
import org.jeecg.modules.basic.vo.SurfaceMeshDTO;

import java.util.List;
import java.util.Map;

/**
 * 分段曲面生成服务接口
 *
 * @author Senior Developer
 * @date 2026-05-12
 */
public interface ISegmentedSurfaceService {

    /**
     * 生成单个排体的分段反演曲面数据
     */
    SurfaceMeshDTO generateSegmentedSurface(String time1, String time2, String rowBody);

    /**
     * 生成全部排体(19~25)的分段反演曲面数据
     */
    Map<String, SurfaceMeshDTO> generateAllSegmentedSurfaces(String time1, String time2);

    /**
     * 导出全部排体(19~25)截取后的原始波长和应变数据
     */
    Map<String, List<SensorRawDataDTO>> exportStrainData(String time1, String time2);

    /**
     * 导出形变时间序列数据
     * 从 time1 次日12:00 到 time2 当天12:00，每天12:00与 time1 做形变计算
     *
     * @param time1 基准时间
     * @param time2 结束时间
     * @return 形变时间序列数据
     */
    DeformationSeriesDTO exportDeformationSeries(String time1, String time2);
}
