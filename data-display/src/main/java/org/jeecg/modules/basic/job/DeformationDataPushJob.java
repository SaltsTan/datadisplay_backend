package org.jeecg.modules.basic.job;

import cn.hutool.core.date.DateField;
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
    public LocalDateTime getDeadline() {
        return LocalDateTime.of(2026, 9, 1, 0, 0, 0);
    }

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
        if (now.isAfter(getDeadline())) {
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
            log.info(".xyz 文件生成成功，文件大小: {} bytes;地址:{}", xyzFile.length(),xyzFile.getAbsolutePath());

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


    public void executePushDataTask(String dateStr) {
        LocalDateTime now = LocalDateTime.now(ZoneId.systemDefault());
        // 检查是否超过截止时间
        if (now.isAfter(getDeadline())) {
            return;
        }
        log.info("开始执行每日排体形变数据 .xyz 文件生成与推送任务...");
        // time1 使用配置的默认基准时间，time2 使用当前时间
        BasicDefaultValue byId = basicDefaultValueService.getById("9");
        String time1 = "2026-01-01 12:00";
        if(ObjectUtil.isNotNull(byId)){
            time1 = StringUtils.isEmpty(byId.getTimeStr()) ? "2026-01-01 12:00" : byId.getTimeStr();
        }
        DateTime dateTime = DateUtil.endOfDay(DateUtil.parseDate(dateStr));
        String time2 = DateUtil.format(dateTime, "yyyy-MM-dd HH:mm:ss");
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
            log.info(".xyz 文件生成成功，文件大小: {} bytes;地址:{}", xyzFile.length(),xyzFile.getAbsolutePath());

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

    /**
     * 手动调用：批量计算并推送指定日期区间内每一天的数据
     * * @param startDateStr 起始日期，格式：yyyy-MM-dd (例如 "2026-03-01")
     * @param endDateStr   结束日期，格式：yyyy-MM-dd (例如 "2026-03-10")
     */
    public void executePushDataTaskForRange(String startDateStr, String endDateStr) {
        log.info("🚀 开始执行历史排体形变数据批量生成与推送任务，区间: {} 到 {}", startDateStr, endDateStr);

        // 1. 获取基准时间 time1
        BasicDefaultValue byId = basicDefaultValueService.getById("9");
        String time1 = "2026-01-01 12:00";
        if (ObjectUtil.isNotNull(byId)) {
            time1 = StringUtils.isEmpty(byId.getTimeStr()) ? "2026-01-01 12:00" : byId.getTimeStr();
        }

        // 2. 解析日期区间并生成按天递增的日期列表
        DateTime startDate = DateUtil.parseDate(startDateStr);
        DateTime endDate = DateUtil.parseDate(endDateStr);
        List<DateTime> dateRange = DateUtil.rangeToList(startDate, endDate, DateField.DAY_OF_YEAR);

        // 3. 循环处理每一天的数据
        for (DateTime currentDate : dateRange) {
            String dateStr = DateUtil.format(currentDate, "yyyy-MM-dd");

            // time2 设置为当前遍历日期的 23:59:59
            DateTime endOfDay = DateUtil.endOfDay(currentDate);
            String time2 = DateUtil.format(endOfDay, "yyyy-MM-dd HH:mm:ss");

            log.info("--------------------------------------------------");
            log.info("⏳ 正在处理日期: {}, 计算区间: {} 到 {}", dateStr, time1, time2);

            File xyzFile = null;
            try {
                // 获取所有排体的形变数据
                Map<String, List<PointDTO>> allData = sensorCalculationService.calculateAllRowsDeformation(time1, time2);
                if (allData == null || allData.isEmpty()) {
                    log.warn("⚠️ 日期 {} 未计算出任何排体数据，跳过该日推送。", dateStr);
                    continue; // 没数据则继续下一天
                }

                // 创建临时的 .xyz 文件
                xyzFile = File.createTempFile("fiber_optic_data_" + dateStr + "_", ".xyz");

                // 将数据写入文件 (经度 纬度 形变)
                try (BufferedWriter writer = new BufferedWriter(new FileWriter(xyzFile))) {
                    for (List<PointDTO> rowData : allData.values()) {
                        for (PointDTO point : rowData) {
                            if (point.getLon() != null && point.getLat() != null) {
                                String line = String.format("%.6f %.6f %.2f", point.getLon(), point.getLat(), point.getDeformation());
                                writer.write(line);
                                writer.newLine();
                            }
                        }
                    }
                }
                log.info("📄 日期 {} 的 .xyz 文件生成成功，大小: {} bytes; 地址: {}", dateStr, xyzFile.length(), xyzFile.getAbsolutePath());

                // 调用外部 API 推送文件
                HttpResponse response = HttpRequest.post(pushApiUrl)
                        .form("file", xyzFile)
                        .form("dataDate", dateStr) // 传入当天日期
                        .timeout(30000)
                        .execute();

                if (response.isOk()) {
                    log.info("✅ 日期 {} 数据推送成功！外部系统返回: {}", dateStr, response.body());
                } else {
                    log.error("❌ 日期 {} 数据推送失败！HTTP状态码: {}, 错误信息: {}", dateStr, response.getStatus(), response.body());
                }

            } catch (Exception e) {
                log.error("❌ 执行日期 {} 形变数据推送时发生异常: ", dateStr, e);
            } finally {
                // 清理临时文件，防止占满磁盘
                if (xyzFile != null && xyzFile.exists()) {
                    xyzFile.delete();
                }
            }
        }

        log.info("🎉 批量推送任务执行完毕！");
    }

    public static void main(String[] args) {
        String startDateStr = "2025-12-09";
        String endDateStr = "2026-02-28";
        DateTime startDate = DateUtil.parseDate(startDateStr);
        DateTime endDate = DateUtil.parseDate(endDateStr);
        List<DateTime> dateRange = DateUtil.rangeToList(startDate, endDate, DateField.DAY_OF_YEAR);
        for (DateTime currentDate : dateRange) {
            String dateStr = DateUtil.format(currentDate, "yyyy-MM-dd");
            System.out.println(dateStr);
        }
    }
}