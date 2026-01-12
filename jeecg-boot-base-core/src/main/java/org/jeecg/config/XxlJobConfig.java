//package org.jeecg.config;
//
//import com.xxl.job.core.executor.impl.XxlJobSpringExecutor;
//import lombok.extern.slf4j.Slf4j;
//import org.springframework.beans.factory.annotation.Value;
//import org.springframework.context.annotation.Bean;
//import org.springframework.context.annotation.Configuration;
//import org.springframework.context.annotation.Profile;
//
//@Slf4j
//@Configuration
//@Profile("prod")
//public class XxlJobConfig {
//
//    @Value("${jeecg.xxl-job.admin.addresses}")
//    private String adminAddresses;
//    @Value("${jeecg.xxl-job.executor.appName}")
//    private String appName;
//    @Value("${jeecg.xxl-job.executor.ip}")
//    private String ip;
//    @Value("${jeecg.xxl-job.executor.port}")
//    private int port;
//    @Value("${jeecg.xxl-job.accessToken}")
//    private String accessToken;
//    @Value("${jeecg.xxl-job.executor.logPath}")
//    private String logPath;
//    @Value("${jeecg.xxl-job.executor.logRetentionDays}")
//    private int logRetentionDays;
//
//
//    @Bean
//    public XxlJobSpringExecutor xxlJobExecutor() {
//        log.info(">>>>>>>>>>> xxl-job config init.");
//        XxlJobSpringExecutor xxlJobSpringExecutor = new XxlJobSpringExecutor();
//        xxlJobSpringExecutor.setAdminAddresses(adminAddresses);
//        xxlJobSpringExecutor.setAppname(appName);
//        xxlJobSpringExecutor.setIp(ip);
//        xxlJobSpringExecutor.setPort(port);
//        xxlJobSpringExecutor.setAccessToken(accessToken);
//        xxlJobSpringExecutor.setLogPath(logPath);
//        xxlJobSpringExecutor.setLogRetentionDays(logRetentionDays);
//        return xxlJobSpringExecutor;
//    }
//}
