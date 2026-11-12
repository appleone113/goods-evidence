package edu.jxue.goodsevidence.common;

import java.lang.annotation.*;

/**
 * 角色访问约束：标注在 Controller 类或方法上
 * <p>由 RoleAuthAspect 切面读取 JWT 中解析出的 role 做比对，
 * 不在允许列表内则抛出 RoleForbiddenException（由全局异常处理转 403）。</p>
 */
@Target({ElementType.TYPE, ElementType.METHOD})
@Retention(RetentionPolicy.RUNTIME)
@Documented
public @interface RequireRole {

    /** 允许访问的角色，取值 USER / ENTERPRISE / ADMIN（不区分大小写） */
    String[] value();
}
