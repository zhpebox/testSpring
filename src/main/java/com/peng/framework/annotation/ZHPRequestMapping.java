package com.peng.framework.annotation;

import java.lang.annotation.*;


@Target({ElementType.TYPE,ElementType.METHOD})
@Retention(RetentionPolicy.RUNTIME)
@Documented
public @interface ZHPRequestMapping {

    String value() default "";

}