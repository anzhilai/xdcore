package com.anzhilai.core.base;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;
/**
 * 用于注解属性和方法的XColumn注解
 *
 */
@Target({ElementType.METHOD, ElementType.FIELD})
@Retention(RetentionPolicy.RUNTIME)
public @interface XColumn {
    /**
     * 列名，默认为空字符串
     */
    String name() default "";
    /**
     * 是否唯一，默认为false
     */
    boolean unique() default false;
    /**
     * 是否为Blob类型，默认为false
     */
    boolean blob() default false;
    /**
     * 是否为大文本字符串类型，默认为false
     */
    boolean text() default false;
    /**
     * 是否为中等长度字符串，默认为false
     */
    boolean mediumtext() default false;
    /**
     * 是否可为空，默认为true
     */
    boolean nullable() default true;

    /**
     * 列定义，默认为空字符串
     */
    String columnDefinition() default "";
    /**
     * 所属表名，默认为空字符串
     */
    String table() default "";
    /**
     * 外键表名，默认为空字符串
     */
    String foreignTable() default "";
    /**
     * 外键表字段名，默认为空字符串
     */
    String foreignTableField() default "";
    /**
     * 关联字段名，默认为空字符串
     */
    String relationField() default "";
    /**
     * 字符串长度，默认为255
     */
    int length() default 255;
    /**
     * 数字长度，默认为0
     */
    int precision() default 0;
    /**
     * 数字精度，默认为0
     */
    int scale() default 0;
    /**
     * 描述，默认为空字符串
     */
    String description() default "";
    /**
     * 是否为文件路径，默认为false
     */
    boolean filepath() default false;
}
