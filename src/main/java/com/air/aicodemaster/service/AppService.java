package com.air.aicodemaster.service;

import com.air.aicodemaster.model.dto.app.AppQueryRequest;
import com.air.aicodemaster.model.entity.App;
import com.air.aicodemaster.model.entity.User;
import com.air.aicodemaster.model.vo.AppVO;
import com.mybatisflex.core.query.QueryWrapper;
import com.mybatisflex.core.service.IService;
import reactor.core.publisher.Flux;

import java.util.List;

/**
 * 应用 服务层。
 *
 * @author Wyhhhh
 */
public interface AppService extends IService<App> {

    /**
     * App 对象转换为 AppVO
     * 查询 App 关联信息
     */
    AppVO getAppVO(App app);


    /**
     * 基于请求参数，构造 QueryWrapper 查询对象
     */
    QueryWrapper getQueryWrapper(AppQueryRequest appQueryRequest);


    /**
     * 分页查询应用؜时，也需要额外获取创建应用的用户信息，这会涉‌及到关联查询多个用户信息，我们需要优化查询性‍能。优化查询逻辑如下：
     * 先收集所有 userId 到集合中
     * 根据 userId 集合批量查询所有用户信息
     * 构建 Map 映射关系 userId => UserVO
     * 一次性组装所有 AppVO，根据 userId 从 Map 中取到需要的用户信息
     */
    List<AppVO> getAppVOList(List<App> appList);

    /**
     * 通过对话生成代码
     * @param appId 应用 id
     * @param message 提示词
     * @param loginUser 登录用户
     */
    Flux<String> chatToGenCode(Long appId, String message, User loginUser);
}
