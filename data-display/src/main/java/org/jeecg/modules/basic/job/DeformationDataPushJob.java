package org.jeecg.modules.basic.job;

import cn.hutool.core.date.DateTime;
import cn.hutool.core.date.DateUtil;
import cn.hutool.core.util.ObjectUtil;
import cn.hutool.http.HttpRequest;
import cn.hutool.http.HttpResponse;
import cn.hutool.http.HttpUtil;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;
import org.jeecg.common.api.vo.Result;
import org.jeecg.modules.basic.dto.PointDTO;
import org.jeecg.modules.basic.entity.BasicDefaultValue;
import org.jeecg.modules.basic.service.IBasicDefaultValueService;
import org.jeecg.modules.basic.service.ISensorCalculationService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.io.BufferedWriter;
import java.io.File;
import java.io.FileWriter;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.Date;
import java.util.List;
import java.util.Map;

/**
 * 每天定时计算所有排体形变数据，生成 .xyz 文件并推送给外部系统
 */
@Component
@Slf4j
public class DeformationDataPushJob {
    private static final LocalDateTime DEADLINE = LocalDateTime.of(2026, 6, 1, 0, 0, 0);


    @Autowired
    private ISensorCalculationService sensorCalculationService;
    @Autowired
    private IBasicDefaultValueService basicDefaultValueService;

    // 目标系统的上传 API 接口地址 (可以在 application.yml 中配置，此处给定默认值)
    @Value("${push.api.url}")
    private String pushApiUrl;


    /**
     * 每天凌晨 1:00 执行一次推送任务
     * cron 表达式说明: 0(秒) 0(分) 1(时) *(日) *(月) ?(周)
     */
    @Scheduled(cron = "0 0 1 * * ?")
    public void executePushDataTask() {
        LocalDateTime now = LocalDateTime.now(ZoneId.systemDefault());
        // 检查是否超过截止时间
        if (now.isAfter(DEADLINE)) {
            return;
        }
        log.info("开始执行每日排体形变数据 .xyz 文件生成与推送任务...");
        // time1 使用配置的默认基准时间，time2 使用当前时间
        BasicDefaultValue byId = basicDefaultValueService.getById("9");
        String time1 = "2026-01-01 12:00";
        if(ObjectUtil.isNotNull(byId)){
            time1 = StringUtils.isEmpty(byId.getTimeStr()) ? "2026-01-01 12:00" : byId.getTimeStr();
        }
        DateTime dateTime = DateUtil.endOfDay(DateUtil.yesterday());
        String time2 = DateUtil.format(dateTime, "yyyy-MM-dd HH:mm:ss");
        String dateStr = DateUtil.format(new Date(), "yyyy-MM-dd");
        File xyzFile = null;
        try {
            // 1. 获取所有排体(19~25号)的形变数据
            Map<String, List<PointDTO>> allData = sensorCalculationService.calculateAllRowsDeformation(time1, time2);
            if (allData == null || allData.isEmpty()) {
                log.warn("未计算出任何排体数据，推送任务取消。");
                return;
            }

            // 2. 创建临时的 .xyz 文件
            xyzFile = File.createTempFile("fiber_optic_data_" + dateStr + "_", ".xyz");
            
            // 3. 将数据按规范要求写入文件 (经度 纬度 形变)
            try (BufferedWriter writer = new BufferedWriter(new FileWriter(xyzFile))) {
                for (List<PointDTO> rowData : allData.values()) {
                    for (PointDTO point : rowData) {
                        // 仅当该点存在经纬度时才写入文件
                        if (point.getLon() != null && point.getLat() != null) {
                            // 格式要求：以空格分隔 "113.105748 29.501930 7.28" (经度 纬度 形变)
                            String line = String.format("%.6f %.6f %.2f", point.getLon(), point.getLat(), point.getDeformation());
                            writer.write(line);
                            writer.newLine();
                        }
                    }
                }
            }
            log.info(".xyz 文件生成成功，文件大小: {} bytes", xyzFile.length());

            // 4. 调用外部 API 推送文件 (multipart/form-data)
            HttpResponse response = HttpRequest.post(pushApiUrl)
                    .form("file", xyzFile)
                    .form("dataDate", dateStr)
                    .timeout(30000) // 设置30秒超时
                    .execute();

            if (response.isOk()) {
                log.info("✅ 数据推送成功！外部系统返回: {}", response.body());
            } else {
                log.error("❌ 数据推送失败！HTTP状态码: {}, 错误信息: {}", response.getStatus(), response.body());
            }

        } catch (Exception e) {
            log.error("执行每日排体形变数据推送任务时发生异常: ", e);
        } finally {
            // 5. 无论成功失败，最后清理临时文件
            if (xyzFile != null && xyzFile.exists()) {
                xyzFile.delete();
            }
        }
    }
}