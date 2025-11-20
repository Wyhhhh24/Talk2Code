package com.air.aicodemaster.core;

import com.air.aicodemaster.ai.AiCodeGeneratorService;
import com.air.aicodemaster.ai.AiCodeGeneratorServiceFactory;
import com.air.aicodemaster.ai.model.HtmlCodeResult;
import com.air.aicodemaster.ai.model.MultiFileCodeResult;
import com.air.aicodemaster.core.parser.CodeParserExecutor;
import com.air.aicodemaster.core.saver.CodeFileSaverExecutor;
import com.air.aicodemaster.exception.BusinessException;
import com.air.aicodemaster.exception.ErrorCode;
import com.air.aicodemaster.model.enums.CodeGenTypeEnum;
import jakarta.annotation.Resource;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Flux;

import java.io.File;

/**
 * AI 代码生成门面类，组合生成和保存功能
 * 分流式输出和阻塞输出
 * 有两种代码生成类型：单文件类型和多文件类型
 * 抽象出代码生成门面类，一个方法，根据生成模式枚举选择对应的方法
 */
@Slf4j
@Service
public class AiCodeGeneratorFacade {

    /**
     * AI Service 工厂
     */
    @Resource
    private AiCodeGeneratorServiceFactory aiCodeGeneratorServiceFactory;

    // 原始单个 AI 服务实例，直接注入，现在是通过 AiCodeGeneratorServiceFactory 工厂获取
//    @Resource
//    private AiCodeGeneratorService aiCodeGeneratorService;



    /**
     * 统一入口：根据类型生成并保存代码（流式输出）
     * 核心逻辑都‌是：拼接 AI 实时响应的字符串，并在 流式返回完成后解‍析字符串并保存代码文件
     *
     * @param userMessage     用户提示词
     * @param codeGenTypeEnum 生成类型
     * @param appId           应用id
     */
    public Flux<String> generateAndSaveCodeStream(String userMessage, CodeGenTypeEnum codeGenTypeEnum , Long appId) {
        if (codeGenTypeEnum == null) {
            throw new BusinessException(ErrorCode.SYSTEM_ERROR, "生成类型为空");
        }
        // 调用 AI Service 工厂根据 appId 获取相应的 AI 服务实例
        AiCodeGeneratorService aiCodeGeneratorService = aiCodeGeneratorServiceFactory.getAiCodeGeneratorService(appId);
        return switch (codeGenTypeEnum) {
            case HTML -> {
                // 获取响应流，调用所封装的通用方法，解析响应结果，保存响应文件
                Flux<String> codeStream = aiCodeGeneratorService.generateHtmlCodeStream(userMessage);
                // processCodeStream 通用方法就是对 generateAndSaveHtmlCodeStream 和 generateAndSaveMultiFileCodeStream
                // 这两个具有相同的流程，进行封装
                yield processCodeStream(codeStream, CodeGenTypeEnum.HTML, appId);
            }
            case MULTI_FILE -> {
                Flux<String> codeStream = aiCodeGeneratorService.generateMultiFileCodeStream(userMessage);
                yield processCodeStream(codeStream, CodeGenTypeEnum.MULTI_FILE,appId);
            }
            default -> {
                String errorMessage = "不支持的生成类型：" + codeGenTypeEnum.getValue();
                throw new BusinessException(ErrorCode.SYSTEM_ERROR, errorMessage);
            }
        };
    }

    /**
     * 通用流式代码处理方法
     *
     * @param codeStream  代码流
     * @param codeGenType 代码生成类型
     * @param appId       应用id
     * @return 流式响应
     */
    private Flux<String> processCodeStream(Flux<String> codeStream, CodeGenTypeEnum codeGenType ,Long appId) {
        StringBuilder codeBuilder = new StringBuilder();
        return codeStream.doOnNext(chunk -> {
            // 实时收集代码片段
            codeBuilder.append(chunk);
        }).doOnComplete(() -> {
            // 流式返回完成后保存代码
            try {
                String completeCode = codeBuilder.toString();
                // 使用代码解析执行器解析代码，因为有两种生成模式，所以解析响应结果，返回值是 Object 类型
                Object parsedResult = CodeParserExecutor.executeParser(completeCode, codeGenType);
                // 代码文件保存执行器，保存代码
                File savedDir = CodeFileSaverExecutor.executeSaver(parsedResult, codeGenType, appId);
                // 这代码文件保存执行器所需的参数正好是代码解析执行器的返回值，这两个执行器也就实现了链式调用
                log.info("保存成功，路径为：" + savedDir.getAbsolutePath());
            } catch (Exception e) {
                log.error("保存失败: {}", e.getMessage());
            }
        });
    }



    /**
     * 统一入口：根据类型生成并保存代码（阻塞输出）
     *
     * @param userMessage     用户提示词
     * @param codeGenTypeEnum 生成代码的类型，单文件类型还是多文件类型
     * @param appId           应用id
     * @return 保存的目录
     */
    public File generateAndSaveCode(String userMessage, CodeGenTypeEnum codeGenTypeEnum,Long appId) {
        // 判断传过来的生成文件类型枚举是否有效
        if (codeGenTypeEnum == null) {
            throw new BusinessException(ErrorCode.SYSTEM_ERROR, "生成文件类型为空");
        }
        // 调用 AI Service 工厂根据 appId 获取相应的 AI 服务实例
        AiCodeGeneratorService aiCodeGeneratorService = aiCodeGeneratorServiceFactory.getAiCodeGeneratorService(appId);
        // 根据生成类型，调用不同的方法
        return switch (codeGenTypeEnum) {
            case HTML -> {
                HtmlCodeResult result = aiCodeGeneratorService.generateHtmlCode(userMessage);
                // 获得响应结果，直接传给代码文件保存执行器
                // 如果希望直接将这个返回值直接给最外层去返回，需要用到一个语法 yield 越过，这样就能把内层的返回值直接作为外层 switch 的返回结果
                yield CodeFileSaverExecutor.executeSaver(result, CodeGenTypeEnum.HTML,appId);
            }
            case MULTI_FILE -> {
                MultiFileCodeResult result = aiCodeGeneratorService.generateMultiFileCode(userMessage);
                yield CodeFileSaverExecutor.executeSaver(result, CodeGenTypeEnum.MULTI_FILE,appId);
            }
            default -> {
                String errorMessage = "不支持的生成类型：" + codeGenTypeEnum.getValue();
                throw new BusinessException(ErrorCode.SYSTEM_ERROR, errorMessage);
            }
        };
    }


//    /**
//     * 生成 HTML 模式的代码并保存（阻塞输出）
//     *
//     * @param userMessage 用户提示词
//     * @return 保存的目录
//     */
//    private File generateAndSaveHtmlCode(String userMessage) {
//        // 传用户提示词，直接调用 AI 服务的方法，通过代理实现大模型的调用，获取大模型返回结果
//        HtmlCodeResult result = aiCodeGeneratorService.generateHtmlCode(userMessage);
//        // 获取到结果之后，通过文件写入工具类，写入到对应的目录文件中
//        return CodeFileSaver.saveHtmlCodeResult(result);
//    }
//
//
//    /**
//     * 生成多文件模式的代码并保存（阻塞输出）
//     *
//     * @param userMessage 用户提示词
//     * @return 保存的目录
//     */
//    private File generateAndSaveMultiFileCode(String userMessage) {
//        // 传用户提示词，直接调用 AI 服务的方法，通过代理实现大模型的调用，获取大模型返回结果
//        MultiFileCodeResult result = aiCodeGeneratorService.generateMultiFileCode(userMessage);
//        // 获取到结果之后，通过文件写入工具类，写入到对应的目录文件中
//        return CodeFileSaver.saveMultiFileCodeResult(result);
//    }
//
//
//    /**
//     * 生成 HTML 模式的代码并保存（流式输出）
//     *
//     * @param userMessage 用户提示词
//     * @return 保存的目录
//     */
//    private Flux<String> generateAndSaveHtmlCodeStream(String userMessage) {
//        Flux<String> result = aiCodeGeneratorService.generateHtmlCodeStream(userMessage);
//        // 字符串拼接器，当流式输出返回全部生成的代码之后，最后再保存代码
//        StringBuilder codeBuilder = new StringBuilder();
//
//        return result // 监听一些事件对 AI 的返回进行拼接，虽然要对流进行处理和监听，但是我们返回到最外层的肯定还是一个流，因为外层也要进行相应的处理
//                .doOnNext(chunk -> {      // 流就是一条河流到了 A 村庄，A 村庄处理完了会流到 B 村庄，B 村庄还要接着处理，所以流的返回值一般也还是一个流
//                                  // 对流进行处理
//                    // 实时收集代码片段，通过 StringBuilder 进行动态拼接字符串
//                    codeBuilder.append(chunk);
//                })  // 什么时候响应完成呢？就是如果我们触发了 doOnComplete 方法，就表示 AI 此次响应结束，所以要监听这个流的 doOnComplete 方法
//                .doOnComplete(() -> {
//                    // 响应结束，流式返回完成后保存代码
//                    try {
//                        // 得到流式输出的完整结果
//                        String completeHtmlCode = codeBuilder.toString();
//                        // 将 AI 返回的结果，通过正则表达式提取需要的代码片段，封装到响应结果对象中
//                        HtmlCodeResult htmlCodeResult = CodeParser.parseHtmlCode(completeHtmlCode);
//                        // 将 AI 响应写入文件中进行保存
//                        File savedDir = CodeFileSaver.saveHtmlCodeResult(htmlCodeResult);
//                        log.info("保存成功，路径为：" + savedDir.getAbsolutePath());
//                    } catch (Exception e) {
//                        log.error("保存失败: {}", e.getMessage());
//                    }
//                });
//    }
//
//
//    /**
//     * 生成多文件模式的代码并保存（流式输出）
//     *
//     * @param userMessage 用户提示词
//     * @return 保存的目录
//     */
//    private Flux<String> generateAndSaveMultiFileCodeStream(String userMessage) {
//        Flux<String> result = aiCodeGeneratorService.generateMultiFileCodeStream(userMessage);
//        // 字符串拼接器，当流式返回生成代码完成后，再保存代码
//        StringBuilder codeBuilder = new StringBuilder();
//        return result
//                .doOnNext(chunk -> {
//                    // 实时收集代码片段
//                    codeBuilder.append(chunk);
//                })
//                .doOnComplete(() -> {
//                    // 流式返回完成后保存代码
//                    try {
//                        // 得到流式输出的完整结果
//                        String completeMultiFileCode = codeBuilder.toString();
//                        // 将 AI 返回的结果，通过正则表达式提取需要的代码片段，封装到响应结果对象中
//                        MultiFileCodeResult multiFileResult = CodeParser.parseMultiFileCode(completeMultiFileCode);
//                        // 将 AI 响应写入文件中进行保存
//                        File savedDir = CodeFileSaver.saveMultiFileCodeResult(multiFileResult);
//                        log.info("保存成功，路径为：" + savedDir.getAbsolutePath());
//                    } catch (Exception e) {
//                        log.error("保存失败: {}", e.getMessage());
//                    }
//                });
//    }
}

