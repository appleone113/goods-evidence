package edu.jxue.goodsevidence.config;

import edu.jxue.goodsevidence.common.RequireRole;
import edu.jxue.goodsevidence.common.Result;
import edu.jxue.goodsevidence.common.RoleForbiddenException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

/**
 * 全局异常处理：把异常统一转成 Result 结构，前端拦截器才能稳定解析 code/message
 */
@RestControllerAdvice
public class GlobalExceptionHandler {

    /** 角色鉴权失败 -> 403 */
    @ExceptionHandler(RoleForbiddenException.class)
    public Result<Void> handleForbidden(RoleForbiddenException e) {
        return Result.fail(403, e.getMessage());
    }

    /** 兜底异常 -> 500，避免把堆栈直接吐给前端 */
    @ExceptionHandler(Exception.class)
    public Result<Void> handleException(Exception e) {
        return Result.fail(500, e.getMessage() == null ? "系统异常" : e.getMessage());
    }
}
