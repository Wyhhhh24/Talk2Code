package com.air.aicodemaster.exception;

import com.air.aicodemaster.common.BaseResponse;
import com.air.aicodemaster.common.ResultUtils;
import io.swagger.v3.oas.annotations.Hidden;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

/**
 * 为了防止意؜料之外的异常，利用 AOP 切面全局‌对我们自定义的业务异常 BusinessException 和 RuntimeException 进行捕获
 */
@Hidden
//  Spring Boot 版本 >= 3.4、并且是 OpenAPI 3 版本的 Knife4j，这会导致 @RestControllerAdvice 注解不兼容，所以必须给这个类加上 @Hidden 注解，不被 Swagger 加载。
@RestControllerAdvice
@Slf4j
public class GlobalExceptionHandler {

    /**
     * 捕捉自定义的业务异常
     */
    @ExceptionHandler(BusinessException.class)
    public BaseResponse<?> businessExceptionHandler(BusinessException e) {
        log.error("BusinessException", e);
        return ResultUtils.error(e.getCode(), e.getMessage());
    }

    /**
     * 捕捉系统异常，未知的异常这里被捕捉，然后统一的响应
     */
    @ExceptionHandler(RuntimeException.class)
    public BaseResponse<?> runtimeExceptionHandler(RuntimeException e) {
        log.error("RuntimeException", e);
        // 系统异常，状态码统一是：50000
        return ResultUtils.error(ErrorCode.SYSTEM_ERROR, "系统错误");
    }
}
