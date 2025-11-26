package com.air.aicodemaster.controller;

import cn.hutool.core.bean.BeanUtil;
import cn.hutool.core.util.StrUtil;
import cn.hutool.json.JSONUtil;
import com.air.aicodemaster.annotation.AuthCheck;
import com.air.aicodemaster.common.BaseResponse;
import com.air.aicodemaster.common.DeleteRequest;
import com.air.aicodemaster.common.ResultUtils;
import com.air.aicodemaster.constant.AppConstant;
import com.air.aicodemaster.constant.UserConstant;
import com.air.aicodemaster.exception.BusinessException;
import com.air.aicodemaster.exception.ErrorCode;
import com.air.aicodemaster.exception.ThrowUtils;
import com.air.aicodemaster.model.dto.app.*;
import com.air.aicodemaster.model.entity.App;
import com.air.aicodemaster.model.entity.User;
import com.air.aicodemaster.model.vo.AppVO;
import com.air.aicodemaster.service.AppService;
import com.air.aicodemaster.service.ProjectDownloadService;
import com.air.aicodemaster.service.UserService;
import com.mybatisflex.core.paginate.Page;
import com.mybatisflex.core.query.QueryWrapper;
import jakarta.annotation.Resource;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.http.MediaType;
import org.springframework.http.codec.ServerSentEvent;
import org.springframework.web.bind.annotation.*;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.io.File;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

/**
 * 应用 控制层。
 *
 * @author Wyhhhh
 */
@RestController
@RequestMapping("/app")
public class AppController {

    @Resource
    private UserService userService;

    @Resource
    private AppService appService;

    @Resource
    private ProjectDownloadService projectDownloadService;


    /**
     * 应用聊天生成代码（流式 SSE）
     * 需要通过 SSE 这种协议返回给前端，让前端能够接收我们后端处理的流式内容，而不是阻塞返回
     * 如果要使用 SSE 这种流式对话方法，要给这个接口的响应做一个特殊的声明
     *
     * 这个接口建议用 GET 类型，如果说接口接收的参数不复杂且没有超过 GET 请求限制那就用 GET
     * 前端在用 EventSource 去对接后端 SSE 接口的时候，GET 请求会更便于对接，测试起来更方便
     * @param appId   应用 ID
     * @param message 用户消息
     * @param request 请求对象
     *                加一个返回的声明 MediaType.TEXT_EVENT_STREAM_VALUE
     * @return 生成结果流
     */
    @GetMapping(value = "/chat/gen/code", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    public Flux<ServerSentEvent<String>> chatToGenCode(@RequestParam Long appId,
                                      @RequestParam String message,
                                      HttpServletRequest request) {
        // 参数校验
        // 如果有一个攻击者连续用 appId 小于 0 的值请求这个接口，是不是就刷数据库了
        // 这里添加一个这个值的校验，其实查询数据库的话，很多字段都需要这样校验
        ThrowUtils.throwIf(appId == null || appId <= 0, ErrorCode.PARAMS_ERROR, "应用ID无效");
        ThrowUtils.throwIf(StrUtil.isBlank(message), ErrorCode.PARAMS_ERROR, "用户消息不能为空");
        // 获取当前登录用户
        User loginUser = userService.getLoginUser(request);
        // 调用服务生成代码（流式）
        Flux<String> contentFlux = appService.chatToGenCode(appId, message, loginUser);

        // 为了解决流式输出时空格丢失问题，即将返回的这个流对象我们可以进行处理
        // 解决方案是对这个流封装一层 JSON 格式，处理这个流，每一个流进行包装
        // 比如说现在这个流返回的文本块，可以把每一个文本块封装成一个 JSON
        return contentFlux
                .map(chunk ->{
                    // 构造一个对象，构造了有一个键值对的对象，
                    // 把数据封装到了一个 Key 为 "d" 的 Map 里
                    // 这里用 d 而不是用 data ，字符越多传输消耗的流量越大，这也是直接影响性能的
                    // 有时候这个块就一个空格，如果用 data 的话，光一个键就已经比要返回的数值要长，损耗得不偿失
                    // 尽量用短一点的字符
                    Map<String, String> wrapper = Map.of("d", chunk);
                    String jsonData = JSONUtil.toJsonStr(wrapper);
                    // 转换成 JSON 之后，我们可以再封装一层，封装成一个 ServerSentEvent
                    // 前端处理起来更方便一些
                    return ServerSentEvent.<String>builder() // Spring 包下的 ServerSentEvent ，AI 输出的是 String，最后处理得到的流也是 String 类型的
                            .data(jsonData)
                            .build();
                })
                // 我们的前端有的时候没有办法判断什么时候 AI 生成完成了
                // 在 SSE 中，当服务器关闭连接时，会触发客户端的 onclose 事件，这是前端判断流结束的一种方式
                // 但是，onclose 事件会在连接正常结束，后端把 AI 内容都输出完了（服务器主动关闭）和异常中断（如网络问题）时都触发
                // 前端就很难区分到底后端是正常响应了所有数据、还是异常中断了
                // 因此，我们最好在后端所有事件返回完成后，添加一个明确的 done 事件
                // 这样前端只要发现有 done 事件是不是就正常结束了，否则如果调用 onClose 是不是就异常中断了
                // 这样可以更清晰地区分流的正常结束和异常中断
                .concatWith(Mono.just(
                        // 流式输出结束之后，发送结束事件
                        ServerSentEvent.<String>builder()
                                .event("done")
                                .data("")
                                .build()
                ));
                // 这个流依次交给下游去处理，现在这个流到我这了，是不是可以给它加点东西，不仅能转换还可以加个事件
                // 最后所有事件发送完了，我们再额外送它一个事件，这样前端就能通过这个 done 去判断了
        // 解决了空格丢失问题，纯空格的 String 也能拿到了，data 多了一层封装，不会出现丢失内容的情况
        // 也不需要专门在后端进行转义了，其实如果想把后端空格也正常输出还有一种方式是：后端先对空格进行转义，前端再转回来
        // 比如把空格转成大括号+小括号+中括号，然后前端再转回来，这种方式还不如再粉装一层
    }


    /**
     * 应用部署
     *
     * @param appDeployRequest 部署请求
     * @param request          请求
     * @return 部署 URL
     */
    @PostMapping("/deploy")
    public BaseResponse<String> deployApp(@RequestBody AppDeployRequest appDeployRequest, HttpServletRequest request) {
        // 参数校验
        ThrowUtils.throwIf(appDeployRequest == null, ErrorCode.PARAMS_ERROR);
        Long appId = appDeployRequest.getAppId();
        ThrowUtils.throwIf(appId == null || appId <= 0, ErrorCode.PARAMS_ERROR, "应用 ID 不能为空");

        // 获取当前登录用户
        User loginUser = userService.getLoginUser(request);

        // 对该应用进行部署，返回可访问的 URL
        String deployUrl = appService.deployApp(appId, loginUser);
        return ResultUtils.success(deployUrl);
    }


    /**
     * 创建应用
     * 用户创建应؜用时，只需要填写初始化提示词。系统会‌自动生成应用名称（取提示词前 12 ‍位）和默认的代码生成类型。
     * @param appAddRequest 创建应用请求
     * @param request       请求
     * @return 应用 id
     */
    @PostMapping("/add")
    public BaseResponse<Long> addApp(@RequestBody AppAddRequest appAddRequest, HttpServletRequest request) {
        // 参数校验
        ThrowUtils.throwIf(appAddRequest == null, ErrorCode.PARAMS_ERROR);
        String initPrompt = appAddRequest.getInitPrompt();
        ThrowUtils.throwIf(StrUtil.isBlank(initPrompt), ErrorCode.PARAMS_ERROR, "初始化 prompt 不能为空");

        // 获取当前登录用户
        User loginUser = userService.getLoginUser(request);

        Long appId = appService.createApp(appAddRequest, loginUser);

        // 返回 appId
        return ResultUtils.success(appId);
    }


    /**
     * 下载应用代码
     * 下载文件名最好是英文的，否则前端可能不会获取到
     * GetMapping 下载方便一些
     *
     * @param appId    应用ID
     * @param request  请求
     * @param response 响应
     */
    @GetMapping("/download/{appId}")
    public void downloadAppCode(@PathVariable Long appId, HttpServletRequest request, HttpServletResponse response) {
        // 1. 基础校验
        ThrowUtils.throwIf(appId == null || appId <= 0, ErrorCode.PARAMS_ERROR, "应用ID无效");

        // 2. 查询应用信息
        App app = appService.getById(appId);
        ThrowUtils.throwIf(app == null, ErrorCode.NOT_FOUND_ERROR, "应用不存在");

        // 3. 权限校验：只有应用创建者可以下载代码
        User loginUser = userService.getLoginUser(request);
        if (!app.getUserId().equals(loginUser.getId())) {
            throw new BusinessException(ErrorCode.NO_AUTH_ERROR, "无权限下载该应用代码");
        }

        // 4. 构建应用代码目录路径（生成目录，非部署目录，生成目录包含所生产的代码文件）
        String codeGenType = app.getCodeGenType();
        String sourceDirName = codeGenType + "_" + appId;
        String sourceDirPath = AppConstant.CODE_OUTPUT_ROOT_DIR + File.separator + sourceDirName;

        // 5. 检查代码目录是否存在
        File sourceDir = new File(sourceDirPath);
        ThrowUtils.throwIf(!sourceDir.exists() || !sourceDir.isDirectory(),
                ErrorCode.NOT_FOUND_ERROR, "应用代码不存在，请先生成代码");

        // 6. 生成下载文件名（不建议添加中文内容，所以这里直接使用 appId 作为文件名）
        String downloadFileName = String.valueOf(appId);

        // 7. 调用通用下载服务  参数中包含 HttpServletResponse 最终我们是要返回给前端的，给前端设置一个特殊的 HTTP 响应头
        projectDownloadService.downloadProjectAsZip(sourceDirPath, downloadFileName, response);
    }


    /**
     * 更新应用（用户只能更新自己的应用名称）
     * 用户更新应用时，需要进行权限校验，确保只能修改自己的应用
     *
     * @param appUpdateRequest 更新请求
     * @param request          请求
     * @return 更新结果
     */
    @PostMapping("/update")
    public BaseResponse<Boolean> updateApp(@RequestBody AppUpdateRequest appUpdateRequest, HttpServletRequest request) {
        // 参数校验
        if (appUpdateRequest == null || appUpdateRequest.getId() == null) {
            throw new BusinessException(ErrorCode.PARAMS_ERROR);
        }

        // 获取当前登录用户
        User loginUser = userService.getLoginUser(request);

        // 判断该应用是否存在
        long id = appUpdateRequest.getId();
        App oldApp = appService.getById(id);
        ThrowUtils.throwIf(oldApp == null, ErrorCode.NOT_FOUND_ERROR);

        // 本人仅可以更新自己的应用
        if (!oldApp.getUserId().equals(loginUser.getId())) {
            throw new BusinessException(ErrorCode.NO_AUTH_ERROR);
        }

        // 操作持久层
        App app = new App();
        app.setId(id);
        app.setAppName(appUpdateRequest.getAppName());
        // 手动设置了 editTime，该字段是为了区分用户主动编辑和系统自动更新的时间
        app.setEditTime(LocalDateTime.now());
        boolean result = appService.updateById(app);
        ThrowUtils.throwIf(!result, ErrorCode.OPERATION_ERROR);
        return ResultUtils.success(true);
    }


    /**
     * 删除应用（用户只能删除自己的应用）
     * 需要进行权限校验，确保只能修改自己的应用
     * @param deleteRequest 删除请求
     * @param request       请求
     * @return 删除结果
     */
    @PostMapping("/delete")
    public BaseResponse<Boolean> deleteApp(@RequestBody DeleteRequest deleteRequest, HttpServletRequest request) {
        // 参数校验
        if (deleteRequest == null || deleteRequest.getId() <= 0) {
            throw new BusinessException(ErrorCode.PARAMS_ERROR);
        }
        // 获取当前用户
        User loginUser = userService.getLoginUser(request);

        // 判断该 App 是否存在
        long id = deleteRequest.getId();
        App oldApp = appService.getById(id);
        ThrowUtils.throwIf(oldApp == null, ErrorCode.NOT_FOUND_ERROR);

        // 仅本人或管理员可删除应用
        if (!oldApp.getUserId().equals(loginUser.getId()) && !UserConstant.ADMIN_ROLE.equals(loginUser.getUserRole())) {
            throw new BusinessException(ErrorCode.NO_AUTH_ERROR);
        }

        // 删除应用的同时，也删除该应用关联的对话历史
        boolean result = appService.removeById(id);
        return ResultUtils.success(result);
    }


    /**
     * 根据 id 获取应用详情（关联该应用的用户信息）
     * 先查询 App，再查询封装类
     * @param id      应用 id
     * @return 应用详情
     */
    @GetMapping("/get/vo")
    public BaseResponse<AppVO> getAppVOById(long id) {
        // 参数校验
        ThrowUtils.throwIf(id <= 0, ErrorCode.PARAMS_ERROR);

        // 查询数据库
        App app = appService.getById(id);
        ThrowUtils.throwIf(app == null, ErrorCode.NOT_FOUND_ERROR);

        // 获取封装类（包含用户信息）
        return ResultUtils.success(appService.getAppVO(app));
    }


    /**
     * 分页获取当前用户创建的应用列表
     *
     * @param appQueryRequest 查询请求
     * @param request         请求
     * @return 应用列表
     */
    @PostMapping("/my/list/page/vo")
    public BaseResponse<Page<AppVO>> listMyAppVOByPage(@RequestBody AppQueryRequest appQueryRequest, HttpServletRequest request) {
        // 1.参数校验
        ThrowUtils.throwIf(appQueryRequest == null, ErrorCode.PARAMS_ERROR);
        // 2.登录校验，获取登录用户
        User loginUser = userService.getLoginUser(request);
        // 3.防止爬虫，限制单次请求只可以查 20 个应用
        long pageSize = appQueryRequest.getPageSize();
        ThrowUtils.throwIf(pageSize > 20, ErrorCode.PARAMS_ERROR, "每页最多查询 20 个应用");
        long pageNum = appQueryRequest.getPageNum();

        // 只查询当前用户的应用
        appQueryRequest.setUserId(loginUser.getId());
        // 基于请求参数，构造查询条件
        QueryWrapper queryWrapper = appService.getQueryWrapper(appQueryRequest);
        Page<App> appPage = appService.page(Page.of(pageNum, pageSize), queryWrapper);
        // 数据封装，转成 AppVO 的 Page 对象
        Page<AppVO> appVOPage = new Page<>(pageNum, pageSize, appPage.getTotalRow());
        List<AppVO> appVOList = appService.getAppVOList(appPage.getRecords());
        appVOPage.setRecords(appVOList);
        // 返回分页查询结果
        return ResultUtils.success(appVOPage);
    }


    /**
     * 分页获取精选应用列表
     * 用户只能在主页查询精选应用列表以及自己的应用列表，这样主页会更干净；同时避免了爬虫，相当于起到‍了一个管理员审核的作用。
     * 加了精选功能，也就相当于添加了一个管理员审核功能，因为管理员只要我们前端开发的时候限定，只允许在主页展示精选应用
     * 那非精选的应用以及一些其它应用没有被管理员设置为精选的应用自然就看不到了
     *
     * @param appQueryRequest 查询请求
     * @return 精选应用列表
     */
    @PostMapping("/good/list/page/vo")
    public BaseResponse<Page<AppVO>> listGoodAppVOByPage(@RequestBody AppQueryRequest appQueryRequest) {
        // 1.参数判空
        ThrowUtils.throwIf(appQueryRequest == null, ErrorCode.PARAMS_ERROR);

        // 2.限制每页最多 20 个
        long pageSize = appQueryRequest.getPageSize();
        ThrowUtils.throwIf(pageSize > 20, ErrorCode.PARAMS_ERROR, "每页最多查询 20 个应用");
        long pageNum = appQueryRequest.getPageNum();

        // 只查询精选的应用
        appQueryRequest.setPriority(AppConstant.GOOD_APP_PRIORITY);
        QueryWrapper queryWrapper = appService.getQueryWrapper(appQueryRequest);

        // 分页查询
        Page<App> appPage = appService.page(Page.of(pageNum, pageSize), queryWrapper);

        // 数据封装
        Page<AppVO> appVOPage = new Page<>(pageNum, pageSize, appPage.getTotalRow());
        List<AppVO> appVOList = appService.getAppVOList(appPage.getRecords());
        appVOPage.setRecords(appVOList);
        return ResultUtils.success(appVOPage);
    }


    /**
     * 管理员删除应用
     * 跟用户删除应用؜接口类似，但是管理员可以删除任意应用
     * @param deleteRequest 删除请求
     * @return 删除结果
     */
    @PostMapping("/admin/delete")
    @AuthCheck(mustRole = UserConstant.ADMIN_ROLE)
    public BaseResponse<Boolean> deleteAppByAdmin(@RequestBody DeleteRequest deleteRequest) {
        if (deleteRequest == null || deleteRequest.getId() <= 0) {
            throw new BusinessException(ErrorCode.PARAMS_ERROR);
        }
        long id = deleteRequest.getId();
        // 判断是否存在
        App oldApp = appService.getById(id);
        ThrowUtils.throwIf(oldApp == null, ErrorCode.NOT_FOUND_ERROR);
        boolean result = appService.removeById(id);
        return ResultUtils.success(result);
    }


    /**
     * 管理员更新应用
     *
     * @param appAdminUpdateRequest 更新请求
     * @return 更新结果
     */
    @PostMapping("/admin/update")
    @AuthCheck(mustRole = UserConstant.ADMIN_ROLE)
    public BaseResponse<Boolean> updateAppByAdmin(@RequestBody AppAdminUpdateRequest appAdminUpdateRequest) {
        if (appAdminUpdateRequest == null || appAdminUpdateRequest.getId() == null) {
            throw new BusinessException(ErrorCode.PARAMS_ERROR);
        }
        long id = appAdminUpdateRequest.getId();
        // 判断是否存在
        App oldApp = appService.getById(id);
        ThrowUtils.throwIf(oldApp == null, ErrorCode.NOT_FOUND_ERROR);
        App app = new App();
        BeanUtil.copyProperties(appAdminUpdateRequest, app);
        // 设置编辑时间
        app.setEditTime(LocalDateTime.now());
        boolean result = appService.updateById(app);
        ThrowUtils.throwIf(!result, ErrorCode.OPERATION_ERROR);
        return ResultUtils.success(true);
    }


    /**
     * 管理员分页获取应用列表
     * 管理员比普؜通用户拥有更大的查询范围，支持根据除‌时间外的任何字段查询，并且每页数量不‍限
     *
     * @param appQueryRequest 查询请求
     * @return 应用列表
     */
    @PostMapping("/admin/list/page/vo")
    @AuthCheck(mustRole = UserConstant.ADMIN_ROLE)
    public BaseResponse<Page<AppVO>> listAppVOByPageByAdmin(@RequestBody AppQueryRequest appQueryRequest) {
        ThrowUtils.throwIf(appQueryRequest == null, ErrorCode.PARAMS_ERROR);
        long pageNum = appQueryRequest.getPageNum();
        long pageSize = appQueryRequest.getPageSize();
        QueryWrapper queryWrapper = appService.getQueryWrapper(appQueryRequest);
        Page<App> appPage = appService.page(Page.of(pageNum, pageSize), queryWrapper);
        // 数据封装
        Page<AppVO> appVOPage = new Page<>(pageNum, pageSize, appPage.getTotalRow());
        List<AppVO> appVOList = appService.getAppVOList(appPage.getRecords());
        appVOPage.setRecords(appVOList);
        return ResultUtils.success(appVOPage);
    }


    /**
     * 管理员根据 id 获取应用详情
     * 这个接口除了权限之外，目前跟用户查看应用详情接口没有区别
     *
     * @param id 应用 id
     * @return 应用详情
     */
    @GetMapping("/admin/get/vo")
    @AuthCheck(mustRole = UserConstant.ADMIN_ROLE)
    public BaseResponse<AppVO> getAppVOByIdByAdmin(long id) {
        ThrowUtils.throwIf(id <= 0, ErrorCode.PARAMS_ERROR);
        // 查询数据库
        App app = appService.getById(id);
        ThrowUtils.throwIf(app == null, ErrorCode.NOT_FOUND_ERROR);
        // 获取封装类
        return ResultUtils.success(appService.getAppVO(app));
    }
}