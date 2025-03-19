package com.remzbl.cpictureback.annotation;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

@Target(ElementType.METHOD)
@Retention(RetentionPolicy.RUNTIME)
public @interface AuthCheck {

    /**
     * 必须有某个角色
     * 语法 : 注解属性的定义方式就是以方法的方式来定义的
     * 为该注解属性赋值的方法 : @AuthCheck(mustRole = "admin")
     */
    String mustRole() default "";
}
