package com.air.aicodemaster.service.impl;

import cn.hutool.core.bean.BeanUtil;
import cn.hutool.core.collection.CollUtil;
import cn.hutool.core.io.FileUtil;
import cn.hutool.core.util.RandomUtil;
import cn.hutool.core.util.StrUtil;
import com.air.aicodemaster.ai.AiCodeGenTypeRoutingService;
import com.air.aicodemaster.constant.AppConstant;
import com.air.aicodemaster.core.AiCodeGeneratorFacade;
import com.air.aicodemaster.core.builder.VueProjectBuilder;
import com.air.aicodemaster.core.handler.StreamHandlerExecutor;
import com.air.aicodemaster.exception.BusinessException;
import com.air.aicodemaster.exception.ErrorCode;
import com.air.aicodemaster.exception.ThrowUtils;
import com.air.aicodemaster.mapper.AppMapper;
import com.air.aicodemaster.model.dto.app.AppAddRequest;
import com.air.aicodemaster.model.dto.app.AppQueryRequest;
import com.air.aicodemaster.model.entity.App;
import com.air.aicodemaster.model.entity.User;
import com.air.aicodemaster.model.enums.ChatHistoryMessageTypeEnum;
import com.air.aicodemaster.model.enums.CodeGenTypeEnum;
import com.air.aicodemaster.model.vo.AppVO;
import com.air.aicodemaster.model.vo.UserVO;
import com.air.aicodemaster.service.AppService;
import com.air.aicodemaster.service.ChatHistoryService;
import com.air.aicodemaster.service.ScreenshotService;
import com.air.aicodemaster.service.UserService;
import com.mybatisflex.core.query.QueryWrapper;
import com.mybatisflex.spring.service.impl.ServiceImpl;
import jakarta.annotation.Resource;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Flux;

import java.io.File;
import java.io.Serializable;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * 应用 服务层实现。
 *
 * @author Wyhhhh
 */
@Slf4j
@Service
public class AppServiceImpl extends ServiceImpl<AppMapper, App>  implements AppService{

    @Resource
    private UserService userService;

    @Resource
    private AiCodeGeneratorFacade aiCodeGeneratorFacade;

    @Resource
    private ChatHistoryService chatHistoryService;

    @Resource
    private StreamHandlerExecutor streamHandlerExecutor;

    @Resource
    private VueProjectBuilder vueProjectBuilder;

    @Resource
    private ScreenshotService screenshotService;

    @Resource
    private AiCodeGenTypeRoutingService aiCodeGenTypeRoutingService;

    /**
     * 通过对话生成代码
     * @param appId 应用 id
     * @param message 提示词
     * @param loginUser 登录用户
     */
    @Override
    public Flux<String> chatToGenCode(Long appId, String message, User loginUser) {
        // 1. 参数校验
        ThrowUtils.throwIf(appId == null || appId <= 0, ErrorCode.PARAMS_ERROR, "应用 ID 不能为空");
        ThrowUtils.throwIf(StrUtil.isBlank(message), ErrorCode.PARAMS_ERROR, "用户消息不能为空");

        // 2. 判断该应用是否存在
        App app = this.getById(appId);
        ThrowUtils.throwIf(app == null, ErrorCode.NOT_FOUND_ERROR, "应用不存在");

        // 3. 验证用户是否有权限访问该应用，本人仅可以对自己的应用访问
        if (!app.getUserId().equals(loginUser.getId())) {
            throw new BusinessException(ErrorCode.NO_AUTH_ERROR, "无权限访问该应用");
        }

        // 4. 获取该应用的代码生成类型，在创建应用的时候填充了
        String codeGenTypeStr = app.getCodeGenType();
        CodeGenTypeEnum codeGenTypeEnum = CodeGenTypeEnum.getEnumByValue(codeGenTypeStr);
        if (codeGenTypeEnum == null) {
            throw new BusinessException(ErrorCode.SYSTEM_ERROR, "不支持的代码生成类型");
        }

        // 5. 校验通过，先添加用户消息到对话历史
        chatHistoryService.addChatMessage(appId, message, ChatHistoryMessageTypeEnum.USER.getValue(), loginUser.getId());

        // 6. 调用 AI 生成代码，其实在门面类中有对流进行拼接的操作，那里已经可以将 AI 的响应内容保存到对话历史中了
        //    但是为了使业务隔离开来，门面类中拼接代码是将代码保存到文件中，这里拼接是为了保存 AI 响应历史，这两个业务隔离开来
        Flux<String> codeStream = aiCodeGeneratorFacade.generateAndSaveCodeStream(message, codeGenTypeEnum, appId);

        // 7. 调用流处理执行器，收集AI响应内容，并在解析完成拼接后，记录到对话历史
        //    生成的单/多文件的代码文件预览 和 VUE 项目的预览是不一样的，VUE项目得要 npm 一下的，分开处理
        return streamHandlerExecutor.doExecute(codeStream, chatHistoryService, appId, loginUser, codeGenTypeEnum);
    }


    /**
     * 创建应用
     */
    @Override
    public Long createApp(AppAddRequest appAddRequest, User loginUser) {
        // 参数校验
        String initPrompt = appAddRequest.getInitPrompt();
        ThrowUtils.throwIf(StrUtil.isBlank(initPrompt), ErrorCode.PARAMS_ERROR, "初始化 prompt 不能为空");

        // 构造入库对象
        App app = new App();
        BeanUtil.copyProperties(appAddRequest, app);
        app.setUserId(loginUser.getId());

        // 应用名称暂时为 initPrompt 前 12 位
        app.setAppName(initPrompt.substring(0, Math.min(initPrompt.length(), 12)));

        // 使用 AI 智能选择代码生成类型
        CodeGenTypeEnum selectedCodeGenType = aiCodeGenTypeRoutingService.routeCodeGenType(initPrompt);
        app.setCodeGenType(selectedCodeGenType.getValue());

        // 插入数据库
        boolean result = this.save(app);
        ThrowUtils.throwIf(!result, ErrorCode.OPERATION_ERROR);
        log.info("应用创建成功，ID: {}, 类型: {}", app.getId(), selectedCodeGenType.getValue());
        return app.getId();
    }



    /**
     * 应用部署
     * 这个实现的优点在于支؜持重复部署。如果应用已经有 deployKey，就直接使用现有的；如果没有，‌就生成一个新的。这样既保证了 URL 的稳定性，
     * 又支持了代码的更新。缺点是不‍支持区分同一个应用多次部署的版本。
     */
    @Override
    public String deployApp(Long appId, User loginUser) {
        // 1. 参数校验
        ThrowUtils.throwIf(appId == null || appId <= 0, ErrorCode.PARAMS_ERROR, "应用 ID 不能为空");
        ThrowUtils.throwIf(loginUser == null, ErrorCode.NOT_LOGIN_ERROR, "用户未登录");

        // 2. 查询应用信息
        App app = this.getById(appId);
        ThrowUtils.throwIf(app == null, ErrorCode.NOT_FOUND_ERROR, "应用不存在");

        // 3. 验证用户是否有权限部署该应用，仅本人可以部署
        if (!app.getUserId().equals(loginUser.getId())) {
            throw new BusinessException(ErrorCode.NO_AUTH_ERROR, "无权限部署该应用");
        }

        // 4. 检查是否已有 deployKey
        String deployKey = app.getDeployKey();
        // 没有则生成 6 位 deployKey（大小写字母 + 数字）
        if (StrUtil.isBlank(deployKey)) {
            deployKey = RandomUtil.randomString(6);
        }

        // 5. 获取代码生成类型，构建源目录路径
        String codeGenType = app.getCodeGenType();
        String sourceDirName = codeGenType + "_" + appId;
        // 这里是应用浏览的路径，代码所生成的路径就是这里
        String sourceDirPath = AppConstant.CODE_OUTPUT_ROOT_DIR + File.separator + sourceDirName;

        // 6. 检查源目录是否存在
        File sourceDir = new File(sourceDirPath);
        if (!sourceDir.exists() || !sourceDir.isDirectory()) {
            throw new BusinessException(ErrorCode.SYSTEM_ERROR, "应用代码不存在，请先生成代码");
        }

        // 7.VUE 项目特殊处理，执行构建，然后将构建好的 dist 目录复制到部署目录即可
        CodeGenTypeEnum codeGenTypeEnum = CodeGenTypeEnum.getEnumByValue(codeGenType);
        // 只有 vue 项目才做特殊处理
        if (codeGenTypeEnum == CodeGenTypeEnum.VUE_PROJECT) {
            // 这个时候就不需要异步了，因为用户已经点了部署了，用户肯定是希望看到部署的结果的，和实时浏览还是不一样的
            boolean buildSuccess = vueProjectBuilder.buildProject(sourceDirPath);
            ThrowUtils.throwIf(!buildSuccess, ErrorCode.SYSTEM_ERROR, "Vue 项目构建失败，请重试");
            // 检查 dist 目录是否存在
            File distDir = new File(sourceDirPath, "dist");
            ThrowUtils.throwIf(!distDir.exists(), ErrorCode.SYSTEM_ERROR, "Vue 项目构建完成但未生成 dist 目录");
            // 将 dist 目录作为部署源
            sourceDir = distDir;
            log.info("Vue 项目构建成功，将部署 dist 目录: {}", distDir.getAbsolutePath());
        }

        // 8. 复制文件到部署目录
        String deployDirPath = AppConstant.CODE_DEPLOY_ROOT_DIR + File.separator + deployKey;
        try {
            // 进行复制，并且还支持覆盖旧的文件
            FileUtil.copyContent(sourceDir, new File(deployDirPath), true);
        } catch (Exception e) {
            throw new BusinessException(ErrorCode.SYSTEM_ERROR, "部署失败：" + e.getMessage());
        }

        // 9. 更新应用的 deployKey 和部署时间
        App updateApp = new App();
        updateApp.setId(appId);
        updateApp.setDeployKey(deployKey);
        updateApp.setDeployedTime(LocalDateTime.now());
        boolean updateResult = this.updateById(updateApp);
        ThrowUtils.throwIf(!updateResult, ErrorCode.OPERATION_ERROR, "更新应用部署信息失败");

        // 10. 生成可访问的 URL
        String appDeployUrl = String.format("%s/%s/", AppConstant.CODE_DEPLOY_HOST, deployKey);

        // 11. 异步生成截图并更新应用封面
        generateAppScreenshotAsync(appId, appDeployUrl);

        return appDeployUrl;
    }


    /**
     * 异步生成应用截图并更新封面
     *
     * @param appId  应用ID
     * @param appUrl 应用访问URL
     */
    @Override
    public void generateAppScreenshotAsync(Long appId, String appUrl) {
        // 使用虚拟线程异步执行
        // Java 21 的虚؜拟线程（Virtual Thread）特性，这是由 JVM 管理的轻量级线程。它的创建成本极低（几乎无内存开销），且在执行 I/O 操作时会自动‌让出 CPU 给其他虚拟线程
        // 从而在同样的系统资源下支持百万级并发而不是传统平台线程的几千级并发。而且它的使用和传统 Java 线程几乎没有区别‍，非常适合处理这种 I/O 密集型的异步任务
        // 比如说，以前需要去排队买票，那假如这票是交给票务平台去调度和获取的，每次买票都得要去找票务平台去管理，那是不是其它用户找你要票你都得要去找票务平台，那就涉及到了更多的交互操作
        // 那我们怎么样去更好的更快的去给用户提供票务服务呢？我们可以先从票务平台批发 1w 张票，所有的票都在我自己手上，也就是线程都在 JVM 的手里，然后我们就可以直接自己去管理这些票
        // 相当于把资源放到了 JVM 来管理，不需要根操作系统再去做交互了，这样的话应用的开销消耗更小一些
        Thread.startVirtualThread(() -> {
            // 调用截图服务生成截图并上传
            String screenshotUrl = screenshotService.generateAndUploadScreenshot(appUrl);
            // 更新数据库中的应用封面字段
            App updateApp = new App();
            updateApp.setId(appId);
            updateApp.setCover(screenshotUrl);
            boolean updated = this.updateById(updateApp);
            ThrowUtils.throwIf(!updated, ErrorCode.OPERATION_ERROR, "更新应用封面字段失败");
        });
    }


    /**
     * App 对象转换为 AppVO
     * 查询 App 关联信息
     */
    @Override
    public AppVO getAppVO(App app) {
        if (app == null) {
            return null;
        }
        AppVO appVO = new AppVO();
        BeanUtil.copyProperties(app, appVO);
        // 关联查询用户信息
        Long userId = app.getUserId();
        if (userId != null) {
            User user = userService.getById(userId);
            UserVO userVO = userService.getUserVO(user);
            appVO.setUser(userVO);
        }
        return appVO;
    }


    /**
     * 基于请求参数，构造 QueryWrapper 查询对象
     */
    @Override
    public QueryWrapper getQueryWrapper(AppQueryRequest appQueryRequest) {
        if (appQueryRequest == null) {
            throw new BusinessException(ErrorCode.PARAMS_ERROR, "请求参数为空");
        }
        Long id = appQueryRequest.getId();
        String appName = appQueryRequest.getAppName();
        String cover = appQueryRequest.getCover();
        String initPrompt = appQueryRequest.getInitPrompt();
        String codeGenType = appQueryRequest.getCodeGenType();
        String deployKey = appQueryRequest.getDeployKey();
        Integer priority = appQueryRequest.getPriority();
        Long userId = appQueryRequest.getUserId();
        String sortField = appQueryRequest.getSortField();
        String sortOrder = appQueryRequest.getSortOrder();
        return QueryWrapper.create()
                .eq("id", id)
                .like("appName", appName)
                .like("cover", cover)
                .like("initPrompt", initPrompt)
                .eq("codeGenType", codeGenType)
                .eq("deployKey", deployKey)
                .eq("priority", priority)
                .eq("userId", userId)
                .orderBy(sortField, "ascend".equals(sortOrder));
    }


    /**
     * 批处理
     * 分页查询应用؜时，也需要额外获取创建应用的用户信息，这会涉‌及到关联查询多个用户信息，我们需要优化查询性‍能。优化查询逻辑如下：
     * 先收集所有 userId 到集合中
     * 根据 userId 集合批量查询所有用户信息
     * 构建 Map 映射关系 userId => UserVO
     * 一次性组装所有 AppVO，根据 userId 从 Map 中取到需要的用户信息
     */
    @Override
    public List<AppVO> getAppVOList(List<App> appList) {
        if (CollUtil.isEmpty(appList)) {
            return new ArrayList<>();
        }
        // 批量获取用户信息，避免 N+1 查询问题，批处理
        // 1.先获取所有的 userId ，收集为集合
        Set<Long> userIds = appList.stream()
                .map(App::getUserId)
                .collect(Collectors.toSet());

        // 2.根据 userId 集合，批量查询用户，转换成 VO 对象，再基于 userId 构建 Map 映射
        Map<Long, UserVO> userVOMap = userService.listByIds(userIds).stream()
                .collect(Collectors.toMap(User::getId, userService::getUserVO)); // 这里是一个优化操作

        return appList.stream().map(app -> {
            AppVO appVO = getAppVO(app);
            UserVO userVO = userVOMap.get(app.getUserId());
            appVO.setUser(userVO);
            return appVO;
        }).collect(Collectors.toList());
    }


    /**
     * 重写 Mybatis-flex 的 removeById 方法，加一些逻辑
     * 删除应用的时候，删除对话历史的绑定一起的
     * 删除应用时关联删除对话历史
     *
     * @param id 应用ID
     * @return 是否成功
     */
    @Override
    public boolean removeById(Serializable id) {
        if (id == null) {
            return false;
        }
        // 转换为 Long 类型
        Long appId = Long.valueOf(id.toString());
        if (appId <= 0) {
            return false;
        }
        // 先删除关联的对话历史
        try {
            chatHistoryService.deleteByAppId(appId);
        } catch (Exception e) {
            // 记录日志但不阻止应用删除
            log.error("删除应用关联对话历史失败: {}", e.getMessage());
        }
        // 其实即使对话历史删除失败，应用也还是要删除的，后期可以做一些兜底，主要是删除应用，无论对话历史是否删除成功
        // 容错设计，即使对话历史删除失败，也不‌会阻止应用的删除操作，只是记录错误日‍志，确保核心业务的稳定性
        // 调用父类的删除方法
        return super.removeById(id);
    }
}
