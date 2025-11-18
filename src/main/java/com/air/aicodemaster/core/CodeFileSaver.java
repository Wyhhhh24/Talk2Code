package com.air.aicodemaster.core;
import cn.hutool.core.io.FileUtil;
import cn.hutool.core.util.IdUtil;
import cn.hutool.core.util.StrUtil;
import com.air.aicodemaster.ai.model.HtmlCodeResult;
import com.air.aicodemaster.ai.model.MultiFileCodeResult;
import com.air.aicodemaster.model.enums.CodeGenTypeEnum;
import java.io.File;
import java.nio.charset.StandardCharsets;

/**
 * 文件写入工具类
 */
@Deprecated
public class CodeFileSaver {
    // 文件保存根目录，在临时目录 tmp 下保存文件，每次生成都对应一个临时目录下的文件夹
    private static final String FILE_SAVE_ROOT_DIR = System.getProperty("user.dir") + "/tmp/code_output";

    /**
     * 保存 HtmlCodeResult
     */
    public static File saveHtmlCodeResult(HtmlCodeResult result) {
        // 创建唯一的文件存放目录路径
        String baseDirPath = buildUniqueDir(CodeGenTypeEnum.HTML.getValue());
        // 将 AI 生成的内容写入到对应文件中并保存到指定目录下
        writeToFile(baseDirPath, "index.html", result.getHtmlCode());
        // 返回文件路径
        return new File(baseDirPath);
    }

    /**
     * 保存 MultiFileCodeResult
     */
    public static File saveMultiFileCodeResult(MultiFileCodeResult result) {
        // 创建唯一的文件目录路径
        String baseDirPath = buildUniqueDir(CodeGenTypeEnum.MULTI_FILE.getValue());
        // 将内容写入到对应文件中并保存到指定目录下
        writeToFile(baseDirPath, "index.html", result.getHtmlCode());
        writeToFile(baseDirPath, "style.css", result.getCssCode());
        writeToFile(baseDirPath, "script.js", result.getJsCode());
        // 返回文件夹路径
        return new File(baseDirPath);
    }

    /**
     * 写入单个文件
     */
    private static void writeToFile(String dirPath, String filename, String content) {
        // 创建文件路径
        String filePath = dirPath + File.separator + filename;
        // 将内容写入文件中      内容     文件路径     编码格式
        FileUtil.writeString(content, filePath, StandardCharsets.UTF_8);
    }


    /**
     * 构建唯一的文件存放的目录路径：tmp/code_output/bizType_雪花ID
     * 每次生成都对应一个临时目录下的文件夹，使用 业务类型 + 雪花 ID 的命名方式来确保唯一性
     */
    private static String buildUniqueDir(String bizType) {
        // 文件名
        String uniqueDirName = StrUtil.format("{}_{}", bizType, IdUtil.getSnowflakeNextIdStr());
        // 文件路径
        String dirPath = FILE_SAVE_ROOT_DIR + File.separator + uniqueDirName;
        // 创建该路径下的文件夹
        FileUtil.mkdir(dirPath);
        // 返回文件夹路径
        return dirPath;
    }
}
