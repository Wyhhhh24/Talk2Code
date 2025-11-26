package com.air.aicodemaster.utils;
import cn.hutool.core.img.ImgUtil;
import cn.hutool.core.io.FileUtil;
import cn.hutool.core.lang.UUID;
import cn.hutool.core.util.RandomUtil;
import cn.hutool.core.util.StrUtil;
import com.air.aicodemaster.exception.BusinessException;
import com.air.aicodemaster.exception.ErrorCode;
import io.github.bonigarcia.wdm.WebDriverManager;
import jakarta.annotation.PreDestroy;
import lombok.extern.slf4j.Slf4j;
import org.openqa.selenium.JavascriptExecutor;
import org.openqa.selenium.OutputType;
import org.openqa.selenium.TakesScreenshot;
import org.openqa.selenium.WebDriver;
import org.openqa.selenium.chrome.ChromeDriver;
import org.openqa.selenium.chrome.ChromeOptions;
import org.openqa.selenium.support.ui.WebDriverWait;

import java.io.File;
import java.time.Duration;

/**
 * @author WyH524
 * @since 2025/11/25 15:57
 * 网页截图工具类
 */
@Slf4j
public class WebScreenshotUtils {

    // 第一步是初始化驱动，需要注意避免重复初始化驱动程序，因为重复初始化驱动是一个比较耗时的操作了，那怎么避免呢？
    // 1.所有的每次截图，都共用一个浏览器，也就是都共用一个驱动，只要初始化驱动一次就够了
    // 在静态代码块里初始化驱动，确保整个应用生命周期内只初始化一次
    // 默认使用已经初始化好的驱动实例
    // 在项目停止前正确销毁驱动，释放资源

    private static final WebDriver webDriver;

    // 全局静态初始化
    // 静态代码块初始化这个 Driver
    static {
        final int DEFAULT_WIDTH = 1600;
        final int DEFAULT_HIGHT = 900;
        // 就相当于打开了一个 Chrome 浏览器
        webDriver = initChromeDriver(DEFAULT_WIDTH, DEFAULT_HIGHT); // 这里使用传参，就是如果后面需要进行修改的话，允许每次动态的传递宽高，也是比较好修改
    }


    /**
     * 初始化 Chrome 浏览器驱动
     * 样板代码
     */
    private static WebDriver initChromeDriver(int width, int height) {
        try {
            // 自动管理 ChromeDriver
            WebDriverManager.chromedriver().setup();
            // 配置 Chrome 选项
            ChromeOptions options = new ChromeOptions();
            // 无头模式，消耗内存更低一些
            options.addArguments("--headless");
            // 禁用GPU（在某些环境下避免问题）
            options.addArguments("--disable-gpu");
            // 禁用沙盒模式（Docker环境需要）
            options.addArguments("--no-sandbox");
            // 禁用开发者shm使用
            options.addArguments("--disable-dev-shm-usage");
            // 设置窗口大小
            options.addArguments(String.format("--window-size=%d,%d", width, height));
            // 禁用扩展
            options.addArguments("--disable-extensions");
            // 设置用户代理
            options.addArguments("--user-agent=Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/91.0.4472.124 Safari/537.36");
            // 创建驱动
            WebDriver driver = new ChromeDriver(options);
            // 设置页面加载超时时间，防止一直在等，一个页面加载不出来，还卡住后面的页面了
            driver.manage().timeouts().pageLoadTimeout(Duration.ofSeconds(30));
            // 设置隐式等待
            driver.manage().timeouts().implicitlyWait(Duration.ofSeconds(10));
            return driver;
        } catch (Exception e) {
            log.error("初始化 Chrome 浏览器失败", e);
            throw new BusinessException(ErrorCode.SYSTEM_ERROR, "初始化 Chrome 浏览器失败");
        }
    }


    /**
     * 生成网页截图
     *
     * @param webUrl 网页URL
     * @return 压缩后的截图文件路径，失败返回 null
     */
    public static String saveWebPageScreenshot(String webUrl) {
        // 非空校验
        if (StrUtil.isBlank(webUrl)) {
            log.error("网页URL不能为空");
            return null;
        }
        try {
            // 创建临时目录
            String rootPath = System.getProperty("user.dir") + File.separator + "tmp" + File.separator + "screenshots"
                    + File.separator + UUID.randomUUID().toString().substring(0, 8);
            FileUtil.mkdir(rootPath);
            // 图片后缀
            final String IMAGE_SUFFIX = ".png";
            // 原始截图文件路径，也就是初次截图，先存到这个路径下
            String imageSavePath = rootPath + File.separator + RandomUtil.randomNumbers(5) + IMAGE_SUFFIX;
            // 访问网页 TODO 共用一个 WebDriver 可能会有并发安全问题，可以使用队列，挨个取出任务来执行即可
            webDriver.get(webUrl);
            // 等待页面加载完成
            waitForPageLoad(webDriver);
            // 截图，返回字节流
            byte[] screenshotBytes = ((TakesScreenshot) webDriver).getScreenshotAs(OutputType.BYTES);
            // 保存原始图片
            saveImage(screenshotBytes, imageSavePath);
            log.info("原始截图保存成功: {}", imageSavePath);

            // 压缩图片
            final String COMPRESSION_SUFFIX = "_compressed.jpg";
            String compressedImagePath = rootPath + File.separator + RandomUtil.randomNumbers(5) + COMPRESSION_SUFFIX;
            compressImage(imageSavePath, compressedImagePath);
            log.info("压缩图片保存成功: {}", compressedImagePath);

            // 删除原始图片，只保留压缩图片
            FileUtil.del(imageSavePath);
            return compressedImagePath;
        } catch (Exception e) {
            log.error("网页截图失败: {}", webUrl, e);
            return null;
        }
    }


    /**
     * 保存图片到文件
     * 等会截出来的图片可能是一些什么图片流，字节流转成图片的方法，传文件路径
     */
    private static void saveImage(byte[] imagesBytes, String imagePath){
        try {
            FileUtil.writeBytes(imagesBytes, imagePath);
        } catch (Exception e) {
            log.error("保存图片失败", e);
            throw new BusinessException(ErrorCode.SYSTEM_ERROR, "保存图片失败");
        }
    }

    /**
     * 压缩图片，本地压缩
     * originImagePath 初次截图时，保存的图片路径
     * compressImagePath 压缩后，将结果图片保存的路径
     */
    private static void compressImage(String originImagePath , String compressImagePath) {
        // 压缩图片质量（0.1 = 10% 质量） 压缩到 30% 的质量
        final float COMPRESSION_QUALITY = 0.3f;
        try {
                             // 原本图片                                 压缩后的图片              压缩的质量
            ImgUtil.compress(FileUtil.file(originImagePath), FileUtil.file(compressImagePath), COMPRESSION_QUALITY);
        } catch (Exception e) {
            log.error("压缩图片失败: {} -> {}", originImagePath, compressImagePath, e);
            throw new BusinessException(ErrorCode.SYSTEM_ERROR, "压缩图片失败");
        }
    }


    /**
     * 等待页面加载完成
     * 我们需要调用 WebDriver 来实现页面的等待，确保页面的动态内容能够动态加载出来
     */
    private static void waitForPageLoad(WebDriver driver) {
        try {
            // 创建等待页面加载对象                            等待的超时时间，最多等 10 s
            WebDriverWait wait = new WebDriverWait(driver, Duration.ofSeconds(10));

            // 等待 document.readyState 为complete 页面加载完成
            wait.until(webDriver ->
                    ((JavascriptExecutor) webDriver)
                            .executeScript("return document.readyState")
                            .equals("complete")
            );
            // 额外等待一段时间，确保动态内容加载完成，不仅等待 ؜DOM 完全加载，还额外等待 2 秒‌确保动态内容渲染完成。
            Thread.sleep(2000);
            log.info("页面加载完成");
        } catch (Exception e) {
            log.error("等待页面加载时出现异常，继续执行截图", e);
        }
    }


    /**
     * 退出时销毁
     * 既然是要初始化驱动，肯定有开有关，肯定要在整个项目关闭的时候，把这个浏览器，也就是这个 WebDriver 给销毁掉
     * 当 JVM 要关闭的时候，可以执行一些操作，这里是进行销毁
     */
    @PreDestroy
    public void destroy() {
        webDriver.quit();
    }

}
