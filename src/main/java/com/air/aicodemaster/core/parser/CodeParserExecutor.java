package com.air.aicodemaster.core.parser;
import com.air.aicodemaster.exception.BusinessException;
import com.air.aicodemaster.exception.ErrorCode;
import com.air.aicodemaster.model.enums.CodeGenTypeEnum;

/**
 * 代码解析执行器，专门负责执行代码解析的
 * 主要做的事情其实就是 if-else ，根据不同的代码生成类型，执行不同的解析逻辑
 * 根据代码生成类型执行相应的解析逻辑
 * 其实本质就是，具体的代码解析器实现了通用的代码解析器接口，然后代码执行器组合去调用不同的代码解析器
 * 接口的作用就是统一接口的请求参数，之后再有新的代码解析器也是要实现这个接口的
 * 这就是策略模式
 * 策略模式：
 * 策略模式定义؜了一系列算法，将每个算法封装起来，并让它们可‌以相互替换，使得算法的变化不会影响使用算法的‍代码，让项目更好维护和扩展。
 */
public class CodeParserExecutor {

    /**
     * 创建对应的代码解析器，先把解析器定义好，不用下面反复的 new
     * 这些代码解析器，本来就是工具类性质的东西，我们可以将其定义成常量
     */
    private static final HtmlCodeParser htmlCodeParser = new HtmlCodeParser();

    private static final MultiFileCodeParser multiFileCodeParser = new MultiFileCodeParser();

    /**
     * 执行代码解析
     *
     * @param codeContent 代码内容
     * @param codeGenType 代码生成类型
     * @return 解析结果（HtmlCodeResult 或 MultiFileCodeResult）
     */
    public static Object executeParser(String codeContent, CodeGenTypeEnum codeGenType) {
        return switch (codeGenType) {
            case HTML -> htmlCodeParser.parseCode(codeContent);
            case MULTI_FILE -> multiFileCodeParser.parseCode(codeContent);
            default -> throw new BusinessException(ErrorCode.SYSTEM_ERROR, "不支持的代码生成类型: " + codeGenType);
        };
    }
}
