package com.peng.framework.annotation;


import java.lang.annotation.*;

@Target({ElementType.PARAMETER})
@Retention(RetentionPolicy.RUNTIME)
@Documented
public @interface  ZHPRequestParam {

    String value() default "";
}
