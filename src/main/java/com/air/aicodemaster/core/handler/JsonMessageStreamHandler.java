package com.air.aicodemaster.core.handler;

import cn.hutool.core.util.StrUtil;
import cn.hutool.json.JSONObject;
import cn.hutool.json.JSONUtil;
import com.air.aicodemaster.ai.model.message.*;
import com.air.aicodemaster.ai.tools.BaseTool;
import com.air.aicodemaster.ai.tools.ToolManager;
import com.air.aicodemaster.constant.AppConstant;
import com.air.aicodemaster.core.builder.VueProjectBuilder;
import com.air.aicodemaster.model.entity.User;
import com.air.aicodemaster.model.enums.ChatHistoryMessageTypeEnum;
import com.air.aicodemaster.service.ChatHistoryService;
import jakarta.annotation.Resource;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import reactor.core.publisher.Flux;

import java.util.HashSet;
import java.util.Set;

/**
 * JSON 消息流处理器
 * 处理 VUE_PROJECT 类型的复杂流式响应，包含工具调用信息
 */
@Slf4j
@Component
public class JsonMessageStreamHandler {

    @Resource
    private VueProjectBuilder vueProjectBuilder;

    @Resource
    private ToolManager toolManager;

    /**
     * 处理 TokenStream（VUE_PROJECT）
     * 解析 JSON 消息并重组为完整的响应格式
     *
     * @param originFlux         原始流
     * @param chatHistoryService 聊天历史服务
     * @param appId              应用ID
     * @param loginUser          登录用户
     * @return 处理后的流
     */
    public Flux<String> handle(Flux<String> originFlux,
                               ChatHistoryService chatHistoryService,
                               long appId, User loginUser) {
        // 收集数据用于生成后端记忆格式
        StringBuilder chatHistoryStringBuilder = new StringBuilder();
        // 用于跟踪已经见过的工具ID，判断是否是第一次调用，有时候在调用工具的时候，它的参数是流式输出的，这种消息会有多份，我们只记录一份
        Set<String> seenToolIds = new HashSet<>();
        return originFlux
                .map(chunk -> {
                    // 解析每个 JSON 消息块{ type: , data: }，解析消息 取出块中的 data 进行拼接，最终返回
                    return handleJsonMessageChunk(chunk, chatHistoryStringBuilder, seenToolIds);
                })
                .filter(StrUtil::isNotEmpty) // 过滤空字符串，防止一些无意义的信息输出，有意义的都转换为 JSON 了，不会为空字符串
                .doOnComplete(() -> {
                    // 流式响应完成后，添加 AI 消息到对话历史
                    String aiResponse = chatHistoryStringBuilder.toString();
                    chatHistoryService.addChatMessage(appId, aiResponse, ChatHistoryMessageTypeEnum.AI.getValue(), loginUser.getId());

                    // 最后将生成的代码，打包构建成 VUE 项目，可以实现浏览
                    String projectPath = AppConstant.CODE_OUTPUT_ROOT_DIR + "/" + "vue_project_" + appId;
                    // 打包构建异步执行不阻塞主线程，假如有非常多的线程阻塞在这里打包了，假如刚好网络有问题，依赖安装不了，那不就是所有的请求都会卡在这个异步构造了，更安全稳定一些
                    // 缺点是：不知道什么时候异步执行完成了，前端可能没有办法做到实时的更新最新网站的浏览，后续做一些调整
                    vueProjectBuilder.buildProjectAsync(projectPath);
                })
                .doOnError(error -> {
                    // 如果AI回复失败，也要记录错误消息
                    String errorMessage = "AI回复失败: " + error.getMessage();
                    chatHistoryService.addChatMessage(appId, errorMessage, ChatHistoryMessageTypeEnum.AI.getValue(), loginUser.getId());
                });
    }

    /**
     * 解析并收集 TokenStream 数据
     * 主要做两件事
     * 1.解析消息，把原本的 JSON 转换成对应类型的对象
     * 2.对输出的工具的信息进行处理，如果完全实时解析工具调用的参数，可能前端后端都要有很复杂的逻辑，所以我们现在只输出 AI 要调用的这个工具的信息
     * 实际调用工具时，流式输出工具请求：
     * {"id": "123","name": "writeFile","arguments": {"relativePath": "src/App.vue","content": "网页代码"}}
     * 其实这个 arguments 是部分的碎片化的
     * 但实际上我们不需要把每一个流都输出吧，其实我们只需要收到一个调用工具请求，知道要调用什么工具之后，就直接返回前端需要执行调用工具
     * 参数的流式输出就不需要流式展示了，在后面工具执行结果可以得到这个参数的
     */
    private String handleJsonMessageChunk(String chunk, StringBuilder chatHistoryStringBuilder, Set<String> seenToolIds) {
        // 解析 JSON
        // 把 JSON 反序列化回对应的对象，这里先反序列化回父类，得到消息类型 type
        StreamMessage streamMessage = JSONUtil.toBean(chunk, StreamMessage.class);
        // 根据消息类型获取对应的类型枚举
        StreamMessageTypeEnum typeEnum = StreamMessageTypeEnum.getEnumByValue(streamMessage.getType());

        // 基于不同的枚举有不同的解析逻辑
        switch (typeEnum) {
            case AI_RESPONSE -> {
                // AI 响应消息
                AiResponseMessage aiMessage = JSONUtil.toBean(chunk, AiResponseMessage.class);
                String data = aiMessage.getData();
                // 直接拼接响应
                chatHistoryStringBuilder.append(data);
                return data;
            }
            case TOOL_REQUEST -> {
                // 工具请求消息
                ToolRequestMessage toolRequestMessage = JSONUtil.toBean(chunk, ToolRequestMessage.class);
                String toolId = toolRequestMessage.getId();
                String toolName = toolRequestMessage.getName();
                // 检查是否是第一次看到这个工具 ID
                if (toolId != null && !seenToolIds.contains(toolId)) {
                    // 第一次调用这个工具，记录 ID 并返回工具信息
                    seenToolIds.add(toolId);
                    // 我们注册了多个工具，所以基于这个工具名称，通过工具管理类获取对应的工具实例
                    // 根据工具名称获取工具实例
                    BaseTool tool = toolManager.getTool(toolName);
                    // 返回格式化的工具调用信息，也就是拼接好工具名称的字符串  String.format("\n\n[选择工具] %s\n\n", getDisplayName());
                    return tool.generateToolRequestResponse();
                } else {
                    // 不是第一次调用这个工具，直接返回空
                    return "";
                }
            }
            case TOOL_EXECUTED -> {
                ToolExecutedMessage toolExecutedMessage = JSONUtil.toBean(chunk, ToolExecutedMessage.class);
                String toolName = toolExecutedMessage.getName();
                // 获取工具调用时传的完整参数
                JSONObject jsonObject = JSONUtil.parseObj(toolExecutedMessage.getArguments());
                // 我们注册了多个工具，所以基于这个工具名称，通过工具管理类获取对应的工具实例
                // 根据工具名称获取工具实例
                BaseTool tool = toolManager.getTool(toolName);
                // 基于工具调用时传的完整参数，拼接返回的字符串结果
                String result = tool.generateToolExecutedResult(jsonObject);
                // String.format("[工具调用] %s %s", getDisplayName(), relativeFilePath) ，将工具名称和操作的路径拼接，进行输出前端和持久化的内容
                String output = String.format("\n\n%s\n\n", result);
                chatHistoryStringBuilder.append(output);
                return output;
            }
            default -> {
                log.error("不支持的消息类型: {}", typeEnum);
                return "";
            }
        }
    }
}
