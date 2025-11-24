package com.air.aicodemaster.ai.model.message;

import dev.langchain4j.service.tool.ToolExecution;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.NoArgsConstructor;

/**
 * 工具执行结果消息
 */
@Data
@EqualsAndHashCode(callSuper = true)
@NoArgsConstructor
public class ToolExecutedMessage extends StreamMessage {

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
     * 工具执行结果
     */
    private String result;

    public ToolExecutedMessage(ToolExecution toolExecution) {
        super(StreamMessageTypeEnum.TOOL_EXECUTED.getValue());
        this.id = toolExecution.request().id();
        this.name = toolExecution.request().name();
        this.arguments = toolExecution.request().arguments();
        this.result = toolExecution.result();
    }
}
