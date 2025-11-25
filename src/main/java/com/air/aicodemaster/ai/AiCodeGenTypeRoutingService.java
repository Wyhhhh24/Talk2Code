package com.air.aicodemaster.ai;

import com.air.aicodemaster.model.enums.CodeGenTypeEnum;
import dev.langchain4j.service.SystemMessage;

/**
 * AI代码生成类型智能路由服务
 * 方法的返回值是枚举类型，框架帮我们实现结构化输出
 */
public interface AiCodeGenTypeRoutingService {

    /**
     * 根据用户需求智能选择代码生成类型
     *
     * @param userPrompt 用户输入的需求描述
     * @return 推荐的代码生成类型
     */
    @SystemMessage(fromResource = "prompt/codegen-routing-system-prompt.txt")
    CodeGenTypeEnum routeCodeGenType(String userPrompt);
}
