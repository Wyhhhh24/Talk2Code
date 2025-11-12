package com.air.aicodemaster.ai;

import dev.langchain4j.model.chat.ChatModel;
import dev.langchain4j.service.AiServices;
import jakarta.annotation.Resource;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * AI 服务创建工厂
 */
@Configuration
public class AiCodeGeneratorServiceFactory {

    @Resource
    private ChatModel chatModel;

    /**
     * 创建 AI 代码生成器服务
     * 项目初始化的时候，springboot会扫描到 Configuration 注解，创建这么个 Bean 对象
     * 我们就可以直接使用这个 AiCodeGeneratorService 来与 AI 进行交互对话了，甚至不用写什么 Http 请求，具体怎么和 AI 对话
     * 这就是 LangChain4j 的 AiService 开发模式，只需要写接口，AiService 的做法，当在创建的时候，根据所写接口的类型运用 反向代理机制
     * 根据传入的接口类型参数，自动生成一个实现类，这个实现类里就是调用 AI ，并且处理 AI 的返回值，转化成一个 String ，AI 对话返回值就是 String ，实现 AI 的交互
     * 代理模式的作用是，相当于找房子你要租房，自己一个一个跑比较的麻烦，就直接找一个中介，找一个代理，把需求告诉它，中介会帮看好房子，给你返回一些信息
     * 所以代理就是为了，给调用方简化代码，更轻松的实现一些功能
     */
    @Bean
    public AiCodeGeneratorService aiCodeGeneratorService() {
        return AiServices.create(AiCodeGeneratorService.class, chatModel);
    }
}
