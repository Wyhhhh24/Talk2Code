package com.air.aicodemaster.ai;

import com.air.aicodemaster.ai.model.HtmlCodeResult;
import com.air.aicodemaster.ai.model.MultiFileCodeResult;
import dev.langchain4j.service.MemoryId;
import dev.langchain4j.service.SystemMessage;
import dev.langchain4j.service.TokenStream;
import dev.langchain4j.service.UserMessage;
import reactor.core.publisher.Flux;

/**
 * @author WyH524
 * @since 2025/11/12 11:23
 * 和 AI 对话的方法，我们只需要定义接口，框架帮我们通过动态代理实现这些接口
 */
public interface AiCodeGeneratorService {

    /**
     * 生成 HTML 代码
     *
     * @param userMessage 用户提示词
     * @return AI 的输出结果
     * 给 AI 提供一个系统提示词，我们使用的是 LanChain4j 的 AiService 开发模式，我们使用系统提示词的方式很简单，只需要加一个注解
     * 我们将提示词放到文件中统一管理，注解指定提示词文件 url 即可
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


    /**
     * 生成 HTML 代码（流式输出）
     *
     * @param userMessage 用户消息
     * @return 生成的代码结果
     */
    @SystemMessage(fromResource = "prompt/codegen-html-system-prompt.txt")
    Flux<String> generateHtmlCodeStream(String userMessage);
    // Flux 就是一个数据流，它会不断的产生新的数据，不是一个静态不变的对象


    /**
     * 生成多文件代码（流式）
     *
     * @param userMessage 用户消息
     * @return 生成的代码结果
     */
    @SystemMessage(fromResource = "prompt/codegen-multi-file-system-prompt.txt")
    Flux<String> generateMultiFileCodeStream(String userMessage);


    /**
     * 生成 Vue 项目代码（流式）
     *
     * @param userMessage 用户消息
     * @return 生成过程的流式响应
     * 参数中必须包含 @MemoryId，支持工具调用时获取到 appId
     * 一旦用上了 @MemoryId 注解，就得要用 @UserMessage 注解
     */
    @SystemMessage(fromResource = "prompt/codegen-vue-project-system-prompt.txt")
    TokenStream generateVueProjectCodeStream(@MemoryId long appId, @UserMessage String userMessage);
}
