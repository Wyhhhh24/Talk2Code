package com.air.aicodemaster.service;

import com.air.aicodemaster.model.dto.chatHistory.ChatHistoryQueryRequest;
import com.air.aicodemaster.model.entity.ChatHistory;
import com.air.aicodemaster.model.entity.User;
import com.mybatisflex.core.paginate.Page;
import com.mybatisflex.core.query.QueryWrapper;
import com.mybatisflex.core.service.IService;
import dev.langchain4j.memory.chat.MessageWindowChatMemory;

import java.time.LocalDateTime;

/**
 * 对话历史 服务层。
 *
 * @author Wyhhhh
 */
public interface ChatHistoryService extends IService<ChatHistory> {

    /**
     * 添加对话消息
     */
    boolean addChatMessage(Long appId, String message, String messageType, Long userId);


    /**
     * 当应用被删除时，需要同步清理对话历史数据
     */
    boolean deleteByAppId(Long appId);


    /**
     * 分页获取应用下的对话历史（游标查询）
     */
    Page<ChatHistory> listAppChatHistoryByPage(Long appId, int pageSize,
                                               LocalDateTime lastCreateTime,
                                               User loginUser);

    /**
     * 对؜话记忆初始化时，需要从数据库中加载对话历史到记‌忆中
     * 加载对话历史到内存
     */
    int loadChatHistoryToMemory(Long appId, MessageWindowChatMemory chatMemory, int maxCount);


    /**
     * 通过查询条件获取 QueryWrapper
     */
    QueryWrapper getQueryWrapper(ChatHistoryQueryRequest chatHistoryQueryRequest);
}
