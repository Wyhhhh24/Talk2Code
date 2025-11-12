package com.air.aicodemaster.ai;


import com.air.aicodemaster.ai.model.HtmlCodeResult;
import com.air.aicodemaster.ai.model.MultiFileCodeResult;
import dev.langchain4j.service.SystemMessage;

/**
 * @author WyH524
 * @since 2025/11/12 11:23
 * 和 AI 对话的方法
 */
public interface AiCodeGeneratorService {

    /**
     * 生成 HTML 代码
     *
     * @param userMessage 用户提示词
     * @return AI 的输出结果
     * 给 AI 提供一个系统提示词，我们使用的是 LanChain4j 的 AiService 开发模式，我们使用系统提示词的方式很简单，只需要加一个注解
     * 我们将提示词放到文件中统一管理
     * LanChain4j 的 SystemMessage 注解是支持直接从文件中去读取提示词
     */
    @SystemMessage(fromResource = "prompt/codegen-html-system-prompt.txt")
    HtmlCodeResult generateHtmlCode(String userMessage);


    /**
     * 生成多文件代码
     *
     * @param userMessage 用户消息
     * @return 生成的代码结果
     */
    @SystemMessage(fromResource = "prompt/codegen-multi-file-system-prompt.txt")
    MultiFileCodeResult generateMultiFileCode(String userMessage);
}
