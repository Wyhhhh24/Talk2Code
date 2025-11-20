package com.air.aicodemaster;

import dev.langchain4j.community.store.embedding.redis.spring.RedisEmbeddingStoreAutoConfiguration;
import org.mybatis.spring.annotation.MapperScan;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.context.annotation.EnableAspectJAutoProxy;
// 我们只要引入了 LanChain4j 整合 Redis 依赖包，会默认初始化一个 Redis 的向量存储，也就是为了实现 RAG 检索增强功能用到的
// 我们现在主要是为了配置 Redis 的会话记忆存储，所以排除掉项目启动时自动去初始化这个向量存储，所以排除掉这个类，不然启动报错
@SpringBootApplication(exclude = {RedisEmbeddingStoreAutoConfiguration.class})
@MapperScan("com.air.aicodemaster.mapper")
@EnableAspectJAutoProxy(exposeProxy = true) // 开启 aop 切面编程
// 这个注解作用：通过 Spring AOP 提供对当前代理对象的访问，使得可以在业务逻辑中访问到当前的代理对象。
// 你可以在方法执行时通过 AopContext.currentProxy() 获取当前的代理对象。
public class AiCodeMasterApplication {
    public static void main(String[] args) {
        SpringApplication.run(AiCodeMasterApplication.class, args);
    }
}
