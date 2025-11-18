package com.air.aicodemaster.constant;

/**
 * 应用优先级的数值常量类
 * 数据库表字段中的优先级是通过数值设置的
 * 但是数值的话可能会出现设置错误的情况，这里定义一个常量来避免错误
 * 同时数据库中的优先级功能，用数字来表示的话，也便于添加其它的优先级
 */
public interface AppConstant {

    /**
     * 精选应用的优先级
     */
    Integer GOOD_APP_PRIORITY = 99;

    /**
     * 默认应用优先级
     */
    Integer DEFAULT_APP_PRIORITY = 0;
}
