package org.jeecg.common.aspect.annotation;

import java.lang.annotation.*;

/**
 * 时间戳token校验注解
 */
@Target(ElementType.METHOD)
@Retention(RetentionPolicy.RUNTIME)
@Documented
public @interface TimestampToken {
}
