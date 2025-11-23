package com.air.aicodemaster.core.parser;

/**
 * @author WyH524
 * @since 2025/11/17 22:49
 * parser 包就是定义所有的代码解析器
 *
 * 代码解析器策略接口
 * 这里需要用到泛型，因为代码解析器返回值是不固定的，有可能是 HtmlCodeResult ，有可能是 MultiFileCodeResult
 * 也就是定义了一套标准，每一个代码解析器都需要实现这个接口
 */
public interface CodeParser<T> {

    /**
     * 解析代码内容
     *
     * @param codeContent 原始代码内容
     * @return 解析后的结果对象
     * 这里返回值不固定的情况下，我们就把返回值设定为一个泛型，动态交给子类去实现，来定义返回值类型
     */
    T parseCode (String codeContent);
}
