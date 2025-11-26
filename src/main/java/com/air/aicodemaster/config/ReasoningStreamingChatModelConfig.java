package com.air.aicodemaster.config;
import dev.langchain4j.model.chat.StreamingChatModel;
import dev.langchain4j.model.openai.OpenAiStreamingChatModel;
import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * @author WyH524
 * @since 2025/11/23 18:49
 * 推理流式模型配置类
 */
@Configuration
@ConfigurationProperties(prefix = "langchain4j.open-ai.chat-model")
@Data
public class ReasoningStreamingChatModelConfig {

    private String baseUrl;

    private String apiKey;

    /**
     * 推理流式模型（用于 Vue 项目生成，带工具调用）
     * 自定义一个 Bean
     */
    @Bean
    public StreamingChatModel reasoningStreamingChatModel() {
        // 为了测试方便临时修改
//        final String modelName = "deepseek-chat";
//        final int maxTokens = 8192;
        // 流式推理模型会比较慢，会输出一些思考内容，所以生产环境使用正常的，但是开发调试的时候，还是使用对话模型
        final String modelName = "deepseek-reasoner";
        final int maxTokens = 32768;
        return OpenAiStreamingChatModel.builder()
                .apiKey(apiKey)
                .baseUrl(baseUrl)
                .modelName(modelName)
                .maxTokens(maxTokens)
                .logRequests(true)  // 输出请求日志
                .logResponses(true)  // 输出响应日志
                .build();
    }
}

