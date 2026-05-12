package org.jeecg.modules.basic.service;

import org.jeecg.modules.basic.dto.PointDTO;
import java.util.List;
import java.util.Map;

public interface ISensorCalculationService {
    
    /**
     * 计算单条排体的形变数据 (返回列表形式)
     */
    List<PointDTO> calculateSingleRowDeformation(String time1, String time2, String rowBody);

    /**
     * 计算所有排体(19~25)的形变数据 (返回按排体号分组的Map形式)
     */
    Map<String, List<PointDTO>> calculateAllRowsDeformation(String time1, String time2);
}