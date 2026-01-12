package org.jeecg.modules.basic.mapper;

import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import org.apache.ibatis.annotations.Param;
import org.jeecg.modules.basic.entity.BasicData;
import com.baomidou.mybatisplus.core.mapper.BaseMapper;

import java.util.List;

/**
 * @Description: basic_data
 * @Author: jeecg-boot
 * @Date:   2025-10-04
 * @Version: V1.0
 */
public interface BasicDataMapper extends BaseMapper<BasicData> {

    IPage<String> timeQuery(Page<String> page, @Param("channels") List<String> channels);

    List<BasicData> list( @Param("list")List<String> list,@Param("time") String time);

    List<BasicData> batchSelect(@Param("channelId") String channelId, @Param("time") String time,@Param("count") Integer count);
}
