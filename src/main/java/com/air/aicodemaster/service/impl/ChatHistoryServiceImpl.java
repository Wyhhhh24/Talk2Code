package com.air.aicodemaster.service.impl;

import cn.hutool.core.collection.CollUtil;
import cn.hutool.core.util.StrUtil;
import com.air.aicodemaster.constant.UserConstant;
import com.air.aicodemaster.exception.ErrorCode;
import com.air.aicodemaster.exception.ThrowUtils;
import com.air.aicodemaster.mapper.ChatHistoryMapper;
import com.air.aicodemaster.model.dto.chatHistory.ChatHistoryQueryRequest;
import com.air.aicodemaster.model.entity.App;
import com.air.aicodemaster.model.entity.ChatHistory;
import com.air.aicodemaster.model.entity.User;
import com.air.aicodemaster.model.enums.ChatHistoryMessageTypeEnum;
import com.air.aicodemaster.service.AppService;
import com.air.aicodemaster.service.ChatHistoryService;
import com.mybatisflex.core.paginate.Page;
import com.mybatisflex.core.query.QueryWrapper;
import com.mybatisflex.spring.service.impl.ServiceImpl;
import dev.langchain4j.data.message.AiMessage;
import dev.langchain4j.data.message.UserMessage;
import dev.langchain4j.memory.chat.MessageWindowChatMemory;
import jakarta.annotation.Resource;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.annotation.Lazy;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.List;

/**
 * 对话历史 服务层实现。
 *
 * @author Wyhhhh
 */
@Slf4j
@Service
public class ChatHistoryServiceImpl extends ServiceImpl<ChatHistoryMapper, ChatHistory>  implements ChatHistoryService{

    @Lazy
    @Resource
    private AppService appService;

    /**
     * 添加对话消息
     *
     * @param appId       应用ID
     * @param message     消息内容
     * @param messageType 消息类型
     * @param userId      用户ID
     * @return 是否添加成功
     */
    @Override
    public boolean addChatMessage(Long appId, String message, String messageType, Long userId) {
        // 1.参数校验
        ThrowUtils.throwIf(appId == null || appId <= 0, ErrorCode.PARAMS_ERROR, "应用ID不能为空");
        ThrowUtils.throwIf(StrUtil.isBlank(message), ErrorCode.PARAMS_ERROR, "消息内容不能为空");
        ThrowUtils.throwIf(StrUtil.isBlank(messageType), ErrorCode.PARAMS_ERROR, "消息类型不能为空");
        ThrowUtils.throwIf(userId == null || userId <= 0, ErrorCode.PARAMS_ERROR, "用户ID不能为空");

        // 2.验证消息类型是否有效
        ChatHistoryMessageTypeEnum messageTypeEnum = ChatHistoryMessageTypeEnum.getEnumByValue(messageType);
        ThrowUtils.throwIf(messageTypeEnum == null, ErrorCode.PARAMS_ERROR, "不支持的消息类型: " + messageType);

        // 3.操作持久层添加对话消息
        ChatHistory chatHistory = ChatHistory.builder()
                .appId(appId)
                .message(message)
                .messageType(messageType)
                .userId(userId)
                .build();
        return this.save(chatHistory);
    }

    /**
     * 当应用被删除时，需要同步清理该应用下的对话历史数据
     */
    @Override
    public boolean deleteByAppId(Long appId) {
        ThrowUtils.throwIf(appId == null || appId <= 0, ErrorCode.PARAMS_ERROR, "应用ID不能为空");
        QueryWrapper queryWrapper = QueryWrapper.create()
                .eq("appId", appId);
        return this.remove(queryWrapper);
    }


    /**
     * 分页获取应用下的对话历史
     *
     * @param appId       应用ID
     * @param pageSize    页面大小
     * @param lastCreateTime 最后创建时间
     * @param loginUser   登录用户
     * @return 对话历史列表
     */
    @Override
    public Page<ChatHistory> listAppChatHistoryByPage(Long appId, int pageSize,
                                                      LocalDateTime lastCreateTime,
                                                      User loginUser) {
        // 参数校验
        ThrowUtils.throwIf(appId == null || appId <= 0, ErrorCode.PARAMS_ERROR, "应用ID不能为空");
        ThrowUtils.throwIf(pageSize <= 0 || pageSize > 50, ErrorCode.PARAMS_ERROR, "页面大小必须在1-50之间");
        ThrowUtils.throwIf(loginUser == null, ErrorCode.NOT_LOGIN_ERROR);

        // 验证权限：只有应用创建者和管理员可以查看
        App app = appService.getById(appId);
        ThrowUtils.throwIf(app == null, ErrorCode.NOT_FOUND_ERROR, "应用不存在");
        boolean isAdmin = UserConstant.ADMIN_ROLE.equals(loginUser.getUserRole());
        boolean isCreator = app.getUserId().equals(loginUser.getId());
        // 既不是管理员也不是创建者不能查看
        ThrowUtils.throwIf(!isAdmin && !isCreator, ErrorCode.NO_AUTH_ERROR, "无权查看该应用的对话历史");

        // 构建查询条件
        ChatHistoryQueryRequest queryRequest = new ChatHistoryQueryRequest();
        queryRequest.setAppId(appId);
        queryRequest.setLastCreateTime(lastCreateTime);
        QueryWrapper queryWrapper = this.getQueryWrapper(queryRequest);

        // 使用游标查询，每一次只查询数据对应索引后面的第一页，所以这里的 pageNum = 1
        return this.page(Page.of(1, pageSize), queryWrapper);
    }


    /**
     * 对؜话记忆初始化时，需要从数据库中加载对话历史到记‌忆中
     * 需要注意加载的顺序
     * 加载对话历史到内存
     */
    @Override
    public int loadChatHistoryToMemory(Long appId, MessageWindowChatMemory chatMemory, int maxCount) {
        try {
            // 直接构造查询条件，起始点为 1 而不是 0，用于排除最新的用户消息
            // 从第一条开始，我们的业务流程是这样的：
            // 先保存的最新的消息到数据库中，当前的这次提问已经入库了，入库之后才调的 AI
            // 调 AI 的时候，先获取对话记忆，这里生成的 AI 服务已经创建出对话记忆了
            // 这个时候对话记忆就已经会把用户的第一条消息交给框架去管理了，所以我们再从数据库中把最新的消息也加载过来的话
            // 那就会出现两条重复的消息，所以这里我们忽略掉最新的那条信息，避免重复加载
            QueryWrapper queryWrapper = QueryWrapper.create()
                    .eq(ChatHistory::getAppId, appId)
                    .orderBy(ChatHistory::getCreateTime, false) // 降序获取最新的
                    .limit(1, maxCount);

            // 查询数据库（新的在前，老的在后）
            List<ChatHistory> historyList = this.list(queryWrapper);
            //List<ChatHistory> historyList = [
            //    {id:7, create_time: "10:30:00"}, // 最新的一条
            //    {id:6, create_time: "10:25:00"},
            //    {id:5, create_time: "10:20:00"},
            //    {id:4, create_time: "10:15:00"},
            //    {id:3, create_time: "10:10:00"}   // 最老的一条
            //];
            if (CollUtil.isEmpty(historyList)) {
                return 0;
            }

            // 反转列表，确保按时间正序（老的在前，新的在后），查出来的时候是新的在前老的在后
            historyList = historyList.reversed();
            //historyList = historyList.reversed(); // 变为：
            //[
            //    {id:3, create_time: "10:10:00"}, // 最老的一条
            //    {id:4, create_time: "10:15:00"},
            //    {id:5, create_time: "10:20:00"},
            //    {id:6, create_time: "10:25:00"},
            //    {id:7, create_time: "10:30:00"}  // 最新的一条
            //]

            // 按时间顺序添加到 chatMemory 中
            int loadedCount = 0;

            // 先清理历史缓存，防止重复加载，也就是将 redis 中该 chatMemory 对应的 appId 对应的 value 清空
            // 如果我们每一次获取 AI Service 的时候，都要进行对话记忆的加载
            // 如果存到 Redis 中的对话记忆没过期，那如果再重新加载一遍是不是消息就两遍了，所以为了防止，先对其进行清空
            // 防止 AI Service 过期了，但是该 AI Service 对应的对话记忆在 Redis 中没有过期 TODO 这里有疑问
            chatMemory.clear();
            for (ChatHistory history : historyList) {
                // 这里遍历这 List 的话， 最老的一条消息先被添加缓存，也就是上面的3、4、5、6、7，满足时间线，对话记忆更合理
                if (ChatHistoryMessageTypeEnum.USER.getValue().equals(history.getMessageType())) {
                    chatMemory.add(UserMessage.from(history.getMessage()));
                    loadedCount++;
                } else if (ChatHistoryMessageTypeEnum.AI.getValue().equals(history.getMessageType())) {
                    chatMemory.add(AiMessage.from(history.getMessage()));
                    loadedCount++;
                }
            }
            log.info("成功为 appId: {} 加载了 {} 条历史对话", appId, loadedCount);
            return loadedCount;
        } catch (Exception e) {
            log.error("加载历史对话失败，appId: {}, error: {}", appId, e.getMessage(), e);
            // 加载失败不影响系统运行，只是没有历史上下文
            return 0;
        }
    }


    /**
     * 根据查询条件，获取对应的 QueryWrapper 包装类
     *
     * @param chatHistoryQueryRequest
     * @return
     */
    @Override
    public QueryWrapper getQueryWrapper(ChatHistoryQueryRequest chatHistoryQueryRequest) {
        QueryWrapper queryWrapper = QueryWrapper.create();
        if (chatHistoryQueryRequest == null) {
            return queryWrapper;
        }
        Long id = chatHistoryQueryRequest.getId();
        String message = chatHistoryQueryRequest.getMessage();
        String messageType = chatHistoryQueryRequest.getMessageType();
        Long appId = chatHistoryQueryRequest.getAppId();
        Long userId = chatHistoryQueryRequest.getUserId();
        LocalDateTime lastCreateTime = chatHistoryQueryRequest.getLastCreateTime();
        String sortField = chatHistoryQueryRequest.getSortField();
        String sortOrder = chatHistoryQueryRequest.getSortOrder();
        // 拼接查询条件
        queryWrapper.eq("id", id)
                .like("message", message)
                .eq("messageType", messageType)
                .eq("appId", appId)
                .eq("userId", userId);
        // 游标查询逻辑 - 只使用 createTime 作为游标
        if (lastCreateTime != null) {
            queryWrapper.lt("createTime", lastCreateTime);
        }
        // 排序
        if (StrUtil.isNotBlank(sortField)) {
            queryWrapper.orderBy(sortField, "ascend".equals(sortOrder));
        } else {
            // 默认按创建时间降序排列
            queryWrapper.orderBy("createTime", false);
        }
        return queryWrapper;
    }
}
