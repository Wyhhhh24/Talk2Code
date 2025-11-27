package com.air.aicodemaster.ai.tools;

import cn.hutool.core.io.FileUtil;
import cn.hutool.json.JSONObject;
import com.air.aicodemaster.constant.AppConstant;
import dev.langchain4j.agent.tool.P;
import dev.langchain4j.agent.tool.Tool;
import dev.langchain4j.agent.tool.ToolMemoryId;
import lombok.extern.slf4j.Slf4j;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardOpenOption;

/**
 * @author WyH524
 * @since 2025/11/23 19:05
 * 文件写入工具
 */
@Slf4j
public class FileWriteTool extends BaseTool{


    /**
     * 标准工具类的定义
     * 为了减轻工具幻觉（错误调用工具或者传参错误），尽量给工具和每个参数添加描述
     * 由于每个 appId 对应一个生成的网站，因此需要根据 appId 构造文件保存路径，可以利用 LangChain4j 工具调用提供的 上下文传参 能力，把 appId 传进工具中来。
     * 在 AI Service 对话方法中加上 memoryId 注解标注的参数，
     * 然后在调用这个方法时传入这个 appId ，这样工具调用时 LangChain4j 框架会自动把传过来的 appId 作为参数提供给工具方法内部
     * 而且这个 appId 不需要告诉 AI ，是框架帮你维护了这个 appId ，框架发现要调工具就顺手帮你把 appId 传过来
     * 然后就能在工具中使用 appId 了。
     *
     * 标识了工具的方法返回值可以是任意类型，框架会帮你转换，不过能返回 String 就返回 String ，因为这样的话减少了框架帮你做的转换，因为最终给 AI 的还是 String
     * 如果你的返回值就是 String ，返回值会完完全全交给 AI ，减少了中间的转换，防止语义的丢失
     *
     * LangChain4j 框架能够把我们在构造 AI 服务时指定的工具翻译成对应的 JSON 文本，放到传递给 AI 的参数中，包含工具的中文描述，参数的描述那些，所以描述的越具体
     * 生成内容会更精准
     */
    @Tool("写入文件到指定路径")
    public String writeFile(
            @P("文件的相对路径")
            String relativeFilePath,
            @P("要写入文件的内容")
            String content,
            @ToolMemoryId Long appId  // 接口中传的参数，工具这里可以获得
    ) {
        try {
            // 将传入的相对文件路径转换为Path对象
            Path path = Paths.get(relativeFilePath);

            // 把相对路径变成绝对路径
            // 判断是否是绝对路径，如果不是，就对相对路径进行处理，创建基于 appId 的项目目录
            if (!path.isAbsolute()) {
                // 生成项目目录名
                String projectDirName = "vue_project_" + appId;

                // 创建完整项目路径
                // /code_output/vue_project_123456
                Path projectRoot = Paths.get(AppConstant.CODE_OUTPUT_ROOT_DIR, projectDirName);

                // 将相对路径解析到项目目录下
                // /code_output/vue_project_project123/src/main.js
                path = projectRoot.resolve(relativeFilePath);
            }

            // 如果父目录不存在，创建父目录，也就是如果这个目录不存在 E:\Javacode\ai-code-master\tmp 就创建
            Path parentDir = path.getParent();
            if (parentDir != null) {
                Files.createDirectories(parentDir);
            }

            // 将内容写入文件
            Files.write(path, content.getBytes(),
                    StandardOpenOption.CREATE,
                    StandardOpenOption.TRUNCATE_EXISTING);

            log.info("成功写入文件: {}", path.toAbsolutePath());

            // 注意要返回相对路径，不能让 AI 把文件绝对路径返回给用户
            return "文件写入成功: " + relativeFilePath;
        } catch (IOException e) {
            String errorMessage = "文件写入失败: " + relativeFilePath + ", 错误: " + e.getMessage();
            log.error(errorMessage, e);
            return errorMessage;
        }
    }


    @Override
    public String getToolName() {
        return "writeFile";
    }

    @Override
    public String getDisplayName() {
        return "写入文件";
    }

    @Override
    public String generateToolExecutedResult(JSONObject arguments) {
        String relativeFilePath = arguments.getStr("relativeFilePath");
        String suffix = FileUtil.getSuffix(relativeFilePath);
        String content = arguments.getStr("content");
        return String.format("""
                        [工具调用] %s %s
                        ```%s
                        %s
                        ```
                        """, getDisplayName(), relativeFilePath, suffix, content);
    }
}
