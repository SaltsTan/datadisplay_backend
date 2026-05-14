package org.jeecg.modules.basic.service;

import org.jeecg.modules.basic.vo.SurfaceMeshDTO;

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
     *
     * @param time1   时间点1 (基准时间)
     * @param time2   时间点2 (当前时间)
     * @param rowBody 排体号 (如 "19"~"25")
     * @return 曲面网格数据传输对象
     */
    SurfaceMeshDTO generateSegmentedSurface(String time1, String time2, String rowBody);

    /**
     * 生成全部排体(19~25)的分段反演曲面数据
     *
     * @param time1 时间点1 (基准时间)
     * @param time2 时间点2 (当前时间)
     * @return 按排体号分组的曲面数据 Map
     */
    Map<String, SurfaceMeshDTO> generateAllSegmentedSurfaces(String time1, String time2);
}
