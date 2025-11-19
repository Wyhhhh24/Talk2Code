package com.air.aicodemaster.controller;

import com.air.aicodemaster.constant.AppConstant;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.core.io.FileSystemResource;
import org.springframework.core.io.Resource;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.servlet.HandlerMapping;

import java.io.File;

/**
 * 静态资源的访问器
 * 提供网页的浏览功能
 * 部署的话，这种方式性能也没有这么高
 * 通过这种方式仅提供网站的浏览服务
 *
 * 这种方式的有点显而易见，不用安装任何其它的环境，也不用额外起一个 serve 进程，非常方便
 * 缺点就是这个功能相对简单，测一些简单的三个文件的页面没什么问题，我们只定义了几种文件的访问，如果要加载音视频的话，不确定能否完成
 * 下载的能力能否提供，所以这个功能只适合我们临时的浏览，并不适合给用户去看已经部署成功网站的方式，功能上性能上都不如专业的 Web 服务器
 */
@RestController
@RequestMapping("/static")
public class StaticResourceController {

    // 应用生成根目录（用于浏览）
    private static final String PREVIEW_ROOT_DIR = AppConstant.CODE_OUTPUT_ROOT_DIR;


    /**
     * 提供静态资源访问，支持目录重定向
     * 访问格式：http://localhost:8123/api/static/{deployKey}[/{fileName}]
     * 返回的是一个资源文件
     */
    @GetMapping("/{deployKey}/**")
    public ResponseEntity<Resource> serveStaticResource(
            @PathVariable String deployKey,
            HttpServletRequest request) {
        try {
            // 获取资源文件的路径，根据用户输入的访问地址，找到本地的文件
            String resourcePath = (String) request.getAttribute(HandlerMapping.PATH_WITHIN_HANDLER_MAPPING_ATTRIBUTE);
            resourcePath = resourcePath.substring(("/static/" + deployKey).length());
            // 如果是目录访问（不带斜杠），重定向到带斜杠的URL
            if (resourcePath.isEmpty()) {
                HttpHeaders headers = new HttpHeaders();
                headers.add("Location", request.getRequestURI() + "/");
                return new ResponseEntity<>(headers, HttpStatus.MOVED_PERMANENTLY);
            }
            // 如果用户输入的就是 / 不加任何文件，就应该访问的就是 index.html 文件，网页入口文件，默认返回 index.html
            if (resourcePath.equals("/")) {
                resourcePath = "/index.html";
            }

            // 构建文件路径，读取本地目录对应的文件
            String filePath = PREVIEW_ROOT_DIR + "/" + deployKey + resourcePath;
            File file = new File(filePath);
            // 检查文件是否存在
            if (!file.exists()) {
                // 如果不存在，构造一个 404 不存在的响应
                return ResponseEntity.notFound().build();
            }
            // 返回文件资源
            Resource resource = new FileSystemResource(file);
            return ResponseEntity.ok()
                    .header("Content-Type", getContentTypeWithCharset(filePath))
                    .body(resource);
        } catch (Exception e) {
            // 这其中出现任何异常，响应 500 服务器异常响应
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).build();
        }
    }

    /**
     * 根据文件扩展名返回带字符编码的 Content-Type
     * 文件服务器，不关要处理网页文件，可能还要处理一些图片资源啊，js，css 资源
     * 这个操作其实就是我们在最终响应给前端的时候，加一个响应头，根据用户要访问的文件类别，来告诉浏览器告诉前端，我要访问的文件
     * 它的格式应该是这样的，如果不加这个头，可能会出现一些乱码，前端可能不认识你要访问的这个文件，它是什么类型的，就可能出现一些问题
     */
    private String getContentTypeWithCharset(String filePath) {
        if (filePath.endsWith(".html")) return "text/html; charset=UTF-8";
        if (filePath.endsWith(".css")) return "text/css; charset=UTF-8";
        if (filePath.endsWith(".js")) return "application/javascript; charset=UTF-8";
        if (filePath.endsWith(".png")) return "image/png";
        if (filePath.endsWith(".jpg")) return "image/jpeg";
        return "application/octet-stream";
    }
}
