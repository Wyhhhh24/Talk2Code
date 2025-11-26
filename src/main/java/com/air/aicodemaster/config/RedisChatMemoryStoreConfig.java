package com.air.aicodemaster.config;

import dev.langchain4j.community.store.memory.chat.redis.RedisChatMemoryStore;
import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * @author WyH524
 * @since 2025/11/20 21:07
 * Redis 持久化对话记忆
 * Redis 为 LanChain4j 提供存储能力的 Redis 的配置 Bean
 */
@Data // 为了让配置类可以为所定义的字段赋值
@Configuration
@ConfigurationProperties(prefix = "spring.data.redis")
public class RedisChatMemoryStoreConfig {

    private String host;

    private int port;

    private String password;

    private long ttl;  // 键的存活时间

    /**
     * LangChain4j 和 redis 的整合包中，我们必须要提供该配置类，定义 Redis 的存储
     */
    @Bean
    public RedisChatMemoryStore redisChatMemoryStore() {
        return RedisChatMemoryStore.builder()
                .host(host)
                .port(port)
                .user("default") // 如果密码不为空，这里的配置一定要加上 user 配置，默认就是 default 去写一下， LangChain4j 整合 Redis 需要这样配置否则报错
                .password(password)   // 但是如果密码为空，加了 user 配置反而会报错
                .ttl(ttl)  // 键的存活时间
                .build();
    }


}
