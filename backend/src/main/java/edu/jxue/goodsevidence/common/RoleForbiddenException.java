package edu.jxue.goodsevidence.common;

/**
 * 角色鉴权失败异常：由 RoleAuthAspect 抛出，全局异常处理转 403
 */
public class RoleForbiddenException extends RuntimeException {

    public RoleForbiddenException(String message) {
        super(message);
    }
}
