package com.air.aicodemaster.service.impl;

import cn.hutool.core.util.StrUtil;
import cn.hutool.core.util.ZipUtil;
import com.air.aicodemaster.exception.BusinessException;
import com.air.aicodemaster.exception.ErrorCode;
import com.air.aicodemaster.exception.ThrowUtils;
import com.air.aicodemaster.service.ProjectDownloadService;
import jakarta.servlet.http.HttpServletResponse;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import java.io.File;
import java.io.FileFilter;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.util.Set;

@Service
@Slf4j
public class ProjectDownloadServiceImpl implements ProjectDownloadService {

    /**
     * 这两部其实可以读取 .ignore 文件，进行过滤一些不需要的文件
     * 压缩时，需要过滤的文件和目录名称，这些是不需要压缩到压缩包的
     */
    private static final Set<String> IGNORED_NAMES = Set.of(
            "node_modules",
            ".git",
            "dist",
            "build",
            ".DS_Store",
            ".env",
            "target",
            ".mvn",
            ".idea",
            ".vscode"
    );

    /**
     * 压缩时，需要过滤的文件扩展名，这些是不需要压缩到压缩包的
     */
    private static final Set<String> IGNORED_EXTENSIONS = Set.of(
            ".log",
            ".tmp",
            ".cache"
    );


    /**
     * 下载项目文件压缩包
     * projectPath 要打包的文件路径
     * downloadFileName 要下载的文件名称
     * response 最终我们是要返回给前端的，给前端设置一个特殊的 HTTP 响应头
     */
    @Override
    public void downloadProjectAsZip(String projectPath, String downloadFileName, HttpServletResponse response) {
        // 目录、文件名的基础校验
        ThrowUtils.throwIf(StrUtil.isBlank(projectPath), ErrorCode.PARAMS_ERROR, "项目路径不能为空");
        ThrowUtils.throwIf(StrUtil.isBlank(downloadFileName), ErrorCode.PARAMS_ERROR, "下载文件名不能为空");
        File projectDir = new File(projectPath);
        ThrowUtils.throwIf(!projectDir.exists(), ErrorCode.NOT_FOUND_ERROR, "项目目录不存在");
        ThrowUtils.throwIf(!projectDir.isDirectory(), ErrorCode.PARAMS_ERROR, "指定路径不是目录");
        log.info("开始打包下载项目: {} -> {}.zip", projectPath, downloadFileName);

        // 设置 HTTP 响应头，我们为了返回文件，我们必须约定一个 HTTP 的响应头，这样客户端才能够识别到知道是一个文件的响应
        response.setStatus(HttpServletResponse.SC_OK);
        response.setContentType("application/zip");
        // 设置这个响应头，是为了告诉浏览器以附件形式下载文件，而不是在浏览器中直接显示
        // 效果：
        // 触发下载对话框：浏览器会显示"文件下载"对话框
        // 指定文件名：下载的文件将使用指定的文件名保存
        // 不预览内容：文件内容不会在浏览器中直接打开/显示
        response.addHeader("Content-Disposition",
                String.format("attachment; filename=\"%s.zip\"", downloadFileName));

        // 定义文件过滤器，接收一个文件，校验是否符合规则
        FileFilter filter = file -> isPathAllowed(projectDir.toPath(), file.toPath());
        try {
            // 使用 Hutool 的 ZipUtil 直接将过滤后的目录压缩到响应输出流
            // 最终要把这个文件返回给前端，返回前端的话，直接利用响应流，把我们压缩包生成的压缩文件流写入到我们返回给前端的响应流中
            // 这样前端就能拿到这个响应流
            // 只要传入文件过滤器， HuTool 工具库会自动的对文件进行遍历，决定是否进行下载
            ZipUtil.zip(response.getOutputStream(), StandardCharsets.UTF_8, false, filter, projectDir);
            log.info("项目打包下载完成: {}", downloadFileName);
        } catch (Exception e) {
            log.error("项目打包下载异常", e);
            throw new BusinessException(ErrorCode.SYSTEM_ERROR, "项目打包下载失败");
        }
    }


    /**
     * 检查路径中的所有文件是否需要被过滤
     * 能够根据一个路径，过滤掉这个路径下所有不符合要求的文件
     * 检查的是一个相对文件是否在项目根目录下，在项目根目录下是否处于合理的位置
     *
     * @param projectRoot 项目根目录，要打包的文件路径
     * @param fullPath    完整路径
     * @return 是否允许
     */
    private boolean isPathAllowed(Path projectRoot, Path fullPath) {
        // 根据这两个路径，换算出来一个相对路径
        // 直接获取到某一个路径相对于完整路径下的一个路径
        // projectRoot 就是这个项目的目录 E:\Javacode\ai-code-master\tmp\code_output\vue_project_350401757895372800
        // fullPath 就是这个目录中的一个文件 E:\Javacode\ai-code-master\tmp\code_output\vue_project_350401757895372800\src\index.html
        // relativePath 就是 src\index.html
        Path relativePath = projectRoot.relativize(fullPath);

        // 检查路径中的每一部分，前缀和后缀
        for (Path part : relativePath) {
            String partName = part.toString();
            // 检查是否在忽略名称列表中
            if (IGNORED_NAMES.contains(partName)) {
                return false;
            }
            // 检查文件扩展名，如果这些扩展名中有任何一个扩展名和我们现在的扩展名匹配了，就过滤掉
            if (IGNORED_EXTENSIONS.stream().anyMatch(partName::endsWith)) {
                return false;
            }
        }
        return true;
    }
}
