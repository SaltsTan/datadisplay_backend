package org.jeecg.common.aspect;

import cn.hutool.core.date.DateUtil;
import cn.hutool.core.util.StrUtil;
import org.aspectj.lang.ProceedingJoinPoint;
import org.aspectj.lang.annotation.Around;
import org.aspectj.lang.annotation.Aspect;
import org.aspectj.lang.annotation.Pointcut;
import org.jeecg.common.exception.JeecgBoot401Exception;
import org.jeecg.common.util.SpringContextUtils;
import org.jeecg.common.util.encryption.SM2EncryptUtil;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import javax.servlet.http.HttpServletRequest;
import java.time.Duration;
import java.time.LocalDateTime;
import java.util.Date;

/**
 * 时间戳token校验拦截器
 */
@Aspect
@Component
public class TimestampTokenAspect {

    private static final Logger log = LoggerFactory.getLogger(TimestampTokenAspect.class);

    /**
     * 定义切点Pointcut
     */
    @Pointcut("execution(public * org.jeecg.modules..*.*Controller.*(..)) && @annotation(org.jeecg.common.aspect.annotation.TimestampToken)")
    public void excudeService() {
    }

    @Around("excudeService()")
    public Object around(ProceedingJoinPoint joinPoint) throws Throwable{
        HttpServletRequest request = SpringContextUtils.getHttpServletRequest();
        //校验时间戳token
        boolean verifyPass = verifyTimestampToken(request);
        if (!verifyPass){
            throw new JeecgBoot401Exception("无权限");
        }
        return joinPoint.proceed();
    }

    /**
     * 验证时间戳token
     * @param request
     * @return
     */
    private boolean verifyTimestampToken(HttpServletRequest request){
        boolean verifyPass = false;
        String token = request.getHeader("x-token");
        String timestamp = request.getHeader("x-timestamp");
        if (StrUtil.isBlank(token) || StrUtil.isBlank(timestamp)){
            return verifyPass;
        }
        try {
            String desEncryptToken = SM2EncryptUtil.decrypt(token);
            //String desEncryptToken = AesEncryptUtil.desEncrypt(token);
            verifyPass = timestamp.equals(desEncryptToken);
            if(verifyPass){
                Date xTimestamp = new Date(Long.parseLong(timestamp) * 1000);
                LocalDateTime xLocalDateTime = DateUtil.toLocalDateTime(xTimestamp);
                LocalDateTime nowLocalDateTime = LocalDateTime.now();
                Duration duration = Duration.between(xLocalDateTime, nowLocalDateTime);
                return duration.toMinutes() <= 30;
            }
        } catch (Exception e) {
            return verifyPass;
        }
        return verifyPass;
    }

}
