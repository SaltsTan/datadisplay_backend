package org.jeecg.modules.basic.service;

import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import org.jeecg.modules.basic.entity.BasicData;
import com.baomidou.mybatisplus.extension.service.IService;

import java.util.List;

/**
 * @Description: basic_data
 * @Author: jeecg-boot
 * @Date:   2025-10-04
 * @Version: V1.0
 */
public interface IBasicDataService extends IService<BasicData> {

    IPage<String> timeQuery(Page<String> page, List<String> channels);

    List<BasicData> channelDataList(List<String> list, String time);
}
