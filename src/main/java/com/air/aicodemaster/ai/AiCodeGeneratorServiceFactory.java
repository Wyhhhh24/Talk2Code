package com.air.aicodemaster.ai;

import com.air.aicodemaster.service.ChatHistoryService;
import com.github.benmanes.caffeine.cache.Cache;
import com.github.benmanes.caffeine.cache.Caffeine;
import dev.langchain4j.community.store.memory.chat.redis.RedisChatMemoryStore;
import dev.langchain4j.memory.chat.MessageWindowChatMemory;
import dev.langchain4j.model.chat.ChatModel;
import dev.langchain4j.model.chat.StreamingChatModel;
import dev.langchain4j.service.AiServices;
import jakarta.annotation.Resource;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.time.Duration;

/**
 * AI 服务创建工厂类
 */
@Slf4j
@Configuration
public class AiCodeGeneratorServiceFactory {

    /**
     * 普通的 ChatModel
     */
    @Resource
    private ChatModel chatModel;

    /**
     * 流式的 ChatModel
     */
    @Resource
    private StreamingChatModel streamingChatModel;

    /**
     * Redis 配置类
     */
    @Resource
    private RedisChatMemoryStore redisChatMemoryStore;

    /**
     * 对话历史
     */
    private ChatHistoryService chatHistoryService;

    /**
     * 通过 AI Service 工厂为每一个 appId 单独构建会话记忆，并且单独提供 AI 服务，这样每个 AI 服务只为 appId 提供特定功能，
     * 这样 AI 服务之间也会更加的隔离，便于我们调试，利用本地缓存防止重复初始化 AI 服务
     *
     * 难道每一次同一个 appId 和 AI 进行对话，我都要给它创建一个新的 AI Service 吗？
     * 我们现在为了隔离对话记忆，我们给每一个 appId 生成一个 AI Service ，但对于相同的 appId 我每次都要重新获取吗？
     * 是不是对于同一个 appId 只需要创建一次 AI Service 就好了
     * 同一个 appId 对话的时候，我直接拿到之前已经生成好的 AI Service 就行了
     * 所以这里我们可以使用缓存
     *
     * AI 服务实例缓存，初始化本地缓存
     * 缓存策略：
     * - 最大缓存 1000 个实例
     * - 写入后 30 分钟过期
     * - 访问后 10 分钟过期
     */
    private final Cache<Long, AiCodeGeneratorService> serviceCache = Caffeine.newBuilder()
            .maximumSize(1000)  // 最多加 1000 个 Key
            .expireAfterWrite(Duration.ofMinutes(30)) // 设置 30 分钟过期，正常情况来说，一个用户和同一个 AI 应用对话的时间应该不会超过 30 分钟
            .expireAfterAccess(Duration.ofMinutes(10)) // 超过半小时之后，内存中的缓存也该淘汰了，在需要使用，重新生成即可
            .removalListener((key, value, cause) -> {
                log.debug("AI 服务实例被移除，appId: {}, 原因: {}", key, cause);
            }) // 当某一个 Key 被强行删除掉，比如说容量超过被淘汰的时候，我们输出一个日志
            .build();


    /**
     * 根据 appId 获取服务（带缓存）
     */
    public AiCodeGeneratorService getAiCodeGeneratorService(long appId) {
        return serviceCache.get(appId, this::createAiCodeGeneratorService);
                          // 如果缓存中没有，就调用指定的方法，生成一个 AI Service 再返回
    }

    /**
     * 创建新的 AI 服务实例
     * 这样如果我们对同一个 appId 进行对话，那我们始终获取的是同一个 Ai Service ，也不用多次执行创建这个 AI 示例的方法
     * 所以这里的加载对话记忆，正常情况来说也不会多次执行，除非过了一个小时用户再来对话
     * 这个时候缓存中没有值了，Redis 中的 Key 也过期了，才需要重新初始化
     */
    private AiCodeGeneratorService createAiCodeGeneratorService(long appId) {
        log.info("为 appId: {} 创建新的 AI 服务实例", appId);
        // 根据 appId 构建独立的对话记忆
        MessageWindowChatMemory chatMemory = MessageWindowChatMemory
                .builder()
                .id(appId)
                .chatMemoryStore(redisChatMemoryStore)
                .maxMessages(20)
                .build();
        // 从数据库中加载对话历史到记忆中
        // 现在如果没有 AI 服务实例的隔离，可能就需要自己去区分什么时候清理对话记忆，我要把对话记忆加载到哪一个 ChatMemory 里面
        chatHistoryService.loadChatHistoryToMemory(appId, chatMemory, 20);
        return AiServices.builder(AiCodeGeneratorService.class)
                .chatModel(chatModel)
                .streamingChatModel(streamingChatModel)
                .chatMemory(chatMemory)
                .build();
    }


    /**
     * 根据 appId 获取服务
     * 之前所有应用共用؜同一个 AI Service 实例，在单独的一个 AI Service 利用它的机制通过 appId 实现对话记忆隔离
     * 还有另外一种方式
     * 现在我在外部每个应用给它生成一个单独的 AI Service ，每个 AI Service 可以给它指定它自己的 ChatMemory
     * 现在我们如果想实现隔离会话记忆的话，可以给每‌个应用分配一个专属的 AI Service，每个 AI Service 绑定独立的对话记忆
     * 根据 appId 获取服务
     */
//    public AiCodeGeneratorService getAiCodeGeneratorService(long appId) {
//        // 根据 appId 构建独立的对话记忆
//        MessageWindowChatMemory chatMemory = MessageWindowChatMemory.builder()
//                .id(appId)
//                .chatMemoryStore(redisChatMemoryStore)
//                .maxMessages(20) // 每个应用最大保存 20 条
//                .build();
//        return AiServices.builder(AiCodeGeneratorService.class)
//                .chatModel(chatModel)
//                .streamingChatModel(streamingChatModel)
//                .chatMemory(chatMemory)
//                .build();
//    }

    /**
     * 之前共用一个 AI Service 的时候，是不需要传 appId 的话，这里为了不改变原来的逻辑，定义一个方法
     * 默认提供一个 Bean ，原有调用的代码逻辑不变
     */
    @Bean
    public AiCodeGeneratorService aiCodeGeneratorService() {
        return getAiCodeGeneratorService(0L);
    }


    // 第一种实现对话记忆隔离，同一个 AI Service 利用它自己的机制，通过 appId 进行隔离
//    @Bean
//    public AiCodeGeneratorService aiCodeGeneratorService() {
//        return AiServices.builder(AiCodeGeneratorService.class)
//                .chatModel(chatModel)
//                .streamingChatModel(streamingChatModel)
//                // 根据 id 构建独立的对话记忆
//                .chatMemoryProvider(memoryId -> MessageWindowChatMemory
//                        .builder()
//                        .id(memoryId)
//                        .chatMemoryStore(redisChatMemoryStore)
//                        .maxMessages(20)
//                        .build())
//                .build();



    /**
     * 创建 AI 代码生成器服务
     * 项目初始化的时候，springboot会扫描到 Configuration 注解，创建这么个 Bean 对象
     * 我们就可以直接使用这个 AiCodeGeneratorService 来与 AI 进行交互对话了，甚至不用写什么 Http 请求，具体怎么和 AI 对话
     * 这就是 LangChain4j 的 AiService 开发模式，只需要写接口，AiService 的做法，当在创建的时候，根据所写接口的类型运用 反向代理机制
     * 根据传入的接口类型参数，自动生成一个实现类，这个实现类里就是调用 AI ，并且处理 AI 的返回值，转化成一个 String ，AI 对话返回值就是 String ，实现 AI 的交互
     * 代理模式的作用是，相当于找房子你要租房，自己一个一个跑比较的麻烦，就直接找一个中介，找一个代理，把需求告诉它，中介会帮看好房子，给你返回一些信息
     * 所以代理就是为了，给调用方简化代码，更轻松的实现一些功能
     */
    // 流式的模式给 AI 服务去使用
//    @Bean
//    public AiCodeGeneratorService aiCodeGeneratorService() {
//        return AiServices.builder(AiCodeGeneratorService.class) // 为哪个接口生成 AI 服务的代理类
//                .chatModel(chatModel)
//                .streamingChatModel(streamingChatModel)
//                .build();
//    }


}
