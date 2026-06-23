package org.jeecg.config.sign.interceptor;

import com.alibaba.fastjson.JSON;
import lombok.extern.slf4j.Slf4j;
import org.jeecg.common.api.vo.Result;
import org.springframework.web.servlet.HandlerInterceptor;

import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import java.io.PrintWriter;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;

/**
 * @Description: 系统使用期限拦截器
 * 用于在指定日期后拦截所有请求
 */
@Slf4j
public class TimeExpirationInterceptor implements HandlerInterceptor {

    // 截止时间：2026-06-01 00:00:00
    public LocalDateTime getDeadline() {
        return LocalDateTime.of(2026, 9, 1, 0, 0, 0);
    }
    @Override
    public boolean preHandle(HttpServletRequest request, HttpServletResponse response, Object handler) throws Exception {
        LocalDateTime now = LocalDateTime.now(ZoneId.systemDefault());

        // 检查是否超过截止时间
        if (now.isAfter(getDeadline())) {
            log.warn("系统已过期，拒绝请求: {} | 当前时间: {}", request.getRequestURI(), now);
            
            // 设置响应头
            response.setStatus(HttpServletResponse.SC_FORBIDDEN); // 403 Forbidden
            response.setCharacterEncoding("UTF-8");
            response.setContentType("application/json; charset=utf-8");

            // 构造统一返回格式 (适配 Jeecg Result)
            Result<Object> result = Result.error(403, "系统故障，请联系管理员;");
            
            try (PrintWriter writer = response.getWriter()) {
                writer.print(JSON.toJSONString(result));
            }
            
            return false; // 拦截请求，不再向下传递
        }

        return true; // 放行
    }
}