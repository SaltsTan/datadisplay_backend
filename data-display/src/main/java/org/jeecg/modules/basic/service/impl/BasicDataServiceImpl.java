package org.jeecg.modules.basic.service.impl;

import cn.hutool.core.collection.CollectionUtil;
import cn.hutool.core.date.DateTime;
import cn.hutool.core.date.DateUtil;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import org.jeecg.modules.basic.entity.BasicData;
import org.jeecg.modules.basic.entity.BasicSetting;
import org.jeecg.modules.basic.mapper.BasicDataMapper;
import org.jeecg.modules.basic.mapper.BasicSettingMapper;
import org.jeecg.modules.basic.service.IBasicDataService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.stream.Collectors;

/**
 * @Description: basic_data
 * @Author: jeecg-boot
 * @Date:   2025-10-04
 * @Version: V1.0
 */
@Service
public class BasicDataServiceImpl extends ServiceImpl<BasicDataMapper, BasicData> implements IBasicDataService {

    @Autowired
    private BasicSettingMapper basicSettingMapper;

    @Override
    public IPage<String> timeQuery(Page<String> page, List<String> channels) {
        return baseMapper.timeQuery(page,channels);
    }

    @Override
    public List<BasicData> channelDataList(List<String> list, String time) {
        BasicSetting basicSetting = basicSettingMapper.selectById(1);
        Integer count;
        List<String> strings = new ArrayList<>();
        strings.add( time);
        if(basicSetting!=null&&basicSetting.getAvgCount()!=null){
            count = basicSetting.getAvgCount();
            if(basicSetting.getAvgCount()<=0){
                count = 3;
            }
        } else {
            count = 3;
        }
        DateTime parse = DateUtil.parse(time, "yyyy-MM-dd HH:mm");
        String time1 = DateUtil.format(parse, "yyyy-MM-dd HH:mm:ss");
        Integer finalCount = count;
        List<BasicData> resList = new ArrayList<>();
        list.forEach(i->{
            List<BasicData> basicData = baseMapper.batchSelect(i, time1, finalCount);
            if(basicData.size()<=0){
                return;
            }
            List<List<Double>> collect = basicData.stream().map(j -> {
                String wavelength = j.getWavelength();
                String[] split = wavelength.split("/");
                List<String> list1 = Arrays.asList(split);
                List<Double> doubleList = list1.stream()
                        .map(Double::parseDouble)  // 将每个 String 转换为 Double
                        .collect(Collectors.toList());
                return doubleList;
            }).collect(Collectors.toList());
            BasicData data = basicData.get(0);
            data.setWavelength(CollectionUtil.join(collect, "/"));
            resList.add(data);
        });
        return resList;
    }
}
