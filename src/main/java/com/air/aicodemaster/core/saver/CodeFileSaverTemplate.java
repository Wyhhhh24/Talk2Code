package com.air.aicodemaster.core.saver;

import cn.hutool.core.io.FileUtil;
import cn.hutool.core.util.StrUtil;
import com.air.aicodemaster.exception.BusinessException;
import com.air.aicodemaster.exception.ErrorCode;
import com.air.aicodemaster.model.enums.CodeGenTypeEnum;

import java.io.File;
import java.nio.charset.StandardCharsets;

/**
 * @author WyH524
 * @since 2025/11/17 23:13
 * saver 抽象代码文件保存器
 *
 * 模板方法模式
 * 定义一个抽象模板类
 * 一般我们的模板类是抽象类，因为有些方法要交给具体的文件保存器去实现
 * 也需要用到泛型，根据生成代码的类型，请求参数类别是不一样的，比如 HtmlCodeResult 或者 MultiFileCodeResult
 * 使用模板方法设计模式，最直观的一个效果是，提高了代码的可维护性，不一定减少代码量
 * 但是可维护性大幅度提高了，可以降低改错代码的可能性
 */
public abstract class CodeFileSaverTemplate<T> {

    // 文件保存根目录
    protected static final String FILE_SAVE_ROOT_DIR = System.getProperty("user.dir") + "/tmp/code_output";

    /**
     * 定义一个标准的文件保存流程
     * 模板方法最核心的流程就是定义一个流程，然后子类都要遵循这个流程
     * 这个方法最好加一个 final ，不要让它被子类复写，因为流程是不能被子类去重写的，这也是模板方法的一个特点
     */
    public final File saveCode(T result , Long appId){
        // 1. 验证输入
        validateInput(result);
        // 2. 构建唯一目录
        String baseDirPath = buildUniqueDir(appId);
        // 3. 保存文件（具体实现由子类提供）
        saveFiles(result, baseDirPath);
        // 4. 返回目录文件对象
        return new File(baseDirPath);
    }

    /**
     * 验证输入参数（可由子类覆盖）
     *
     * @param result 代码结果对象
     */
    protected void validateInput(T result) {
        if (result == null) {
            throw new BusinessException(ErrorCode.SYSTEM_ERROR, "代码结果对象不能为空");
        }
    }


    /**
     * 构建唯一目录路径
     *
     * @param appId 应用ID
     * @return 目录路径
     */
    protected final String buildUniqueDir(Long appId) {
        if(appId == null){
            throw new BusinessException(ErrorCode.PARAMS_ERROR, "appId 不能为空");
        }
        String codeType = getCodeType().getValue();
        String uniqueDirName = StrUtil.format("{}_{}", codeType, appId);
        String dirPath = FILE_SAVE_ROOT_DIR + File.separator + uniqueDirName;
        FileUtil.mkdir(dirPath);
        return dirPath;
    }

    /**
     * 写入单个文件的工具方法
     *
     * @param dirPath  目录路径
     * @param filename 文件名
     * @param content  文件内容
     */
    protected final void writeToFile(String dirPath, String filename, String content) {
        if (StrUtil.isNotBlank(content)) {
            String filePath = dirPath + File.separator + filename;
            FileUtil.writeString(content, filePath, StandardCharsets.UTF_8);
        }
    }

    /**
     * 获取代码类型（由子类实现）
     * 文件的类型，这个方法我们可以交给子类实现，这样我们需要获取文件类型的时候，可以直接调用这个方法，不需要传参了
     * 每个子类类型是不一样的
     *
     * @return 代码生成类型
     */
    protected abstract CodeGenTypeEnum getCodeType();

    /**
     * 保存文件的具体实现（由子类实现）
     *
     * @param result      代码结果对象
     * @param baseDirPath 基础目录路径
     */
    protected abstract void saveFiles(T result, String baseDirPath);
}
