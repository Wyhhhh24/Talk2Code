package com.air.aicodemaster.ai.model.message;

import dev.langchain4j.agent.tool.ToolExecutionRequest;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.NoArgsConstructor;

/**
 * 工具调用消息
 */
@Data
@EqualsAndHashCode(callSuper = true)
@NoArgsConstructor
public class ToolRequestMessage extends StreamMessage {

    /**
     * 每个工具有唯一的工具 Id
     */
    private String id;

    /**
     * 工具名称
     */
    private String name;

    /**
     * 调用工具时传的参数
     */
    private String arguments;

    /**
     * 这里我们接收的是等会调用 AI 得到的 TokenStream 中能给我们提供的工具调用请求这样一个对象
     * 这里为什么不直接用 JSON 去赋值呢？为什么要一个一取出来参数再去赋值给我们自己的参数呢？
     * 这个对象不支持 JSON 解析，可能会报错
     */
    public ToolRequestMessage(ToolExecutionRequest toolExecutionRequest) {
        super(StreamMessageTypeEnum.TOOL_REQUEST.getValue());
        this.id = toolExecutionRequest.id();
        this.name = toolExecutionRequest.name();
        this.arguments = toolExecutionRequest.arguments();
    }
}
