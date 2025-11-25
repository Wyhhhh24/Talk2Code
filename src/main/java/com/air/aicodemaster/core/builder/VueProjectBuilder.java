package com.air.aicodemaster.core.builder;

import cn.hutool.core.util.RuntimeUtil;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.io.File;
import java.util.concurrent.TimeUnit;

/**
 * @author WyH524
 * @since 2025/11/24 14:59
 * VUE 项目的构建
 */
@Slf4j
@Component
public class VueProjectBuilder {


    /**
     * 异步构建项目（不阻塞主流程，也就是虚拟线程进行构建项目）
     *
     * 打包构建是一个耗时操作，对于耗时操作，我们如果想节约一下系统的性能，如果想同时支持更多的打包构建 VUE 项目
     * 我们可以异步，构造过程不阻塞主线程
     *
     * @param projectPath 项目路径
     */
    public void buildProjectAsync(String projectPath) {
        // 在单独的线程中执行构建，避免阻塞主流程
        // java 21 新特性，虚拟线程，更轻量级的线程，JVM 层面的调度
        // Java 21 的虚؜拟线程（Virtual Thread）特性，这是由 JVM 管理的轻量级线程。它的创建成本极低（几乎无内存开销），且在执行 I/O 操作时会自动‌让出 CPU 给其他虚拟线程
        // 从而在同样的系统资源下支持百万级并发而不是传统平台线程的几千级并发。而且它的使用和传统 Java 线程几乎没有区别‍，非常适合处理这种 I/O 密集型的异步任务。
        Thread.ofVirtual()
                .name("vue-builder-" + System.currentTimeMillis())
                .start(() -> {
            // 这里定义做什么任务
            try {
                buildProject(projectPath);
            } catch (Exception e) {
                log.error("异步构建 Vue 项目时发生异常: {}", e.getMessage(), e);
            }
            // 尽量不要在异步线程里出异常，对于这种异步类的任务出了异常，最好在内部把异常处理一下，因为异步的线程出了异常不好定位，多做一些异常的处理
        });
    }


    /**
     * 也就是在对应的项目目录下，执行 npm run build ，进行构建 Vue 项目 （阻塞构建）
     *
     * @param projectPath 项目根目录路径（绝对路径）
     * @return 是否构建成功
     */
    public boolean buildProject(String projectPath) {
        File projectDir = new File(projectPath);

        // 判断当前路径是否存在
        if (!projectDir.exists() || !projectDir.isDirectory()) {
            log.error("项目目录不存在: {}", projectPath);
            return false;
        }

        // 检查当前目录下是否存在 package.json
        File packageJson = new File(projectDir, "package.json");
        if (!packageJson.exists()) {
            log.error("package.json 文件不存在: {}", packageJson.getAbsolutePath());
            return false;
        }

        // 执行 npm install 下载依赖
        log.info("开始构建 Vue 项目: {}", projectPath);
        if (!executeNpmInstall(projectDir)) {
            log.error("npm install 执行失败");
            return false;
        }

        // 执行 npm run build 构建项目
        if (!executeNpmBuild(projectDir)) {
            log.error("npm run build 执行失败");
            return false;
        }

        // 若 build 成功的话，会在该目录下，生成 dist 目录，所以这里验证 dist 目录是否生成成功
        File distDir = new File(projectDir, "dist");
        if (!distDir.exists()) {
            log.error("构建完成但 dist 目录未生成: {}", distDir.getAbsolutePath());
            return false;
        }

        // 做了检查项目构建成功了
        log.info("Vue 项目构建成功，dist 目录: {}", distDir.getAbsolutePath());
        return true;
    }


    /**
     * 在对应的目录，执行 npm install 命令
     */
    private boolean executeNpmInstall(File projectDir) {
        log.info("执行 npm install...");
        String command = String.format("%s install", buildCommand("npm"));
        return executeCommand(projectDir, command, 300); // 5分钟超时
    }


    /**
     * 执行 npm run build 命令
     */
    private boolean executeNpmBuild(File projectDir) {
        log.info("执行 npm run build...");
        String command = String.format("%s run build", buildCommand("npm"));
        return executeCommand(projectDir, command, 180); // 3分钟超时
    }


    /**
     * 执行命令
     *
     * @param workingDir     工作目录
     * @param command        命令字符串
     * @param timeoutSeconds 超时时间（秒）
     * @return 是否执行成功
     * 对于这种通用的方法，我们就没有必要直接抛出异常，而是返回 false ，然后让方法外面进行判断
     */
    private boolean executeCommand(File workingDir, String command, int timeoutSeconds) {
        try {
            log.info("在目录 {} 中执行命令: {}", workingDir.getAbsolutePath(), command);
            Process process = RuntimeUtil.exec(
                    null,
                    workingDir,
                    command.split("\\s+") // 命令分割为数组
            );
            // 等待进程完成，设置超时
            boolean finished = process.waitFor(timeoutSeconds, TimeUnit.SECONDS);
            if (!finished) {
                log.error("命令执行超时（{}秒），强制终止进程", timeoutSeconds);
                process.destroyForcibly();
                return false;
            }
            int exitCode = process.exitValue();
            if (exitCode == 0) {
                log.info("命令执行成功: {}", command);
                return true;
            } else {
                log.error("命令执行失败，退出码: {}", exitCode);
                return false;
            }
        } catch (Exception e) {
            log.error("执行命令失败: {}, 错误信息: {}", command, e.getMessage());
            return false;
        }
    }


    /**
     * 根据操作系统构造命令
     *
     * @param baseCommand 基础命令
     * @return 构建后的命令字符串
     */
    private String buildCommand(String baseCommand) {
        if (isWindows()) {
            return baseCommand + ".cmd";
        }
        return baseCommand;
    }


    /**
     * 判断当前操作系统是否是 windows 系统
     */
    private boolean isWindows() {
        // 拿到操作系统的名称，转小写，判断是不是 windows
        return System.getProperty("os.name").toLowerCase().contains("windows");
    }
}
