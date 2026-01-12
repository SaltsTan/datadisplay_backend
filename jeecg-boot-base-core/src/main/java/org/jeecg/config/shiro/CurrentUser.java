package org.jeecg.config.shiro;

import java.lang.annotation.*;

/**
 * Created by syl nerosyl@live.com on 2024/2/27
 *
 * 注入当前登录用户对象，支持注入整个用户对象或者用户对象的某个属性。
 *
 * sample:
 * <p>
 *     public Result<LoginUser> testController(@CurrentUser LoginUser loginUser )
 * <p>
 *     public Result<String> testController(@CurrentUser(expression = "username") String username )
 *
 *
 * @author syl
 */
@Target({ ElementType.PARAMETER, ElementType.ANNOTATION_TYPE })
@Retention(RetentionPolicy.RUNTIME)
@Documented
public @interface CurrentUser {


    /**
     * 当注入的类型不符合预期时，是否抛出异常。默认值为 false，即不抛出异常返回null。
     */
    boolean errorOnInvalidType() default false;

    /**
     * SpEL表达式，用于从当前用户对象中获取所需的属性。默认为空字符串，即直接注入整个用户对象。
     */
    String expression() default "";
}
