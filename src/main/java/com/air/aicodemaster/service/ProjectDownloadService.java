package com.air.aicodemaster.service;

import jakarta.servlet.http.HttpServletResponse;

/**
 * @author WyH524
 * @since 2025/11/25 17:36
 */
public interface ProjectDownloadService {
    /**
     * 下载项目文件压缩包
     * projectPath 要打包的文件路径
     * downloadFileName 要下载的文件名称
     * response 最终我们是要返回给前端的，给前端设置一个特殊的 HTTP 响应头
     */
    void downloadProjectAsZip(String projectPath, String downloadFileName, HttpServletResponse response);
}
