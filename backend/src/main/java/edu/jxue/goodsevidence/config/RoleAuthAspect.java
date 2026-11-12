package edu.jxue.goodsevidence.config;

import edu.jxue.goodsevidence.common.RequireRole;
import edu.jxue.goodsevidence.common.RoleForbiddenException;
import org.aspectj.lang.JoinPoint;
import org.aspectj.lang.annotation.Aspect;
import org.aspectj.lang.annotation.Before;
import org.aspectj.lang.annotation.Pointcut;
import org.aspectj.lang.reflect.MethodSignature;
import org.springframework.stereotype.Component;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

import java.lang.reflect.Method;
import java.util.Arrays;

/**
 * 角色鉴权切面（模块一：用户与角色权限管理）
 *
 * <p>JWT 拦截器只负责「认出你是谁」，本切面负责「你能不能访问这个接口」。
 * 所有带 @RequireRole 的 Controller 方法在执行前比对 request 中的 role，
 * 不匹配直接抛 RoleForbiddenException，由 GlobalExceptionHandler 转 403。</p>
 */
@Aspect
@Component
public class RoleAuthAspect {

    @Pointcut("execution(* edu.jxue.goodsevidence.controller..*.*(..))")
    public void controllerPointcut() {
    }

    @Before("controllerPointcut()")
    public void checkRole(JoinPoint jp) {
        RequireRole ann = resolveAnnotation(jp);
        if (ann == null) {
            return;   // 未标注即为公开（或仅需登录）
        }
        ServletRequestAttributes attrs = (ServletRequestAttributes) RequestContextHolder.currentRequestAttributes();
        if (attrs == null) {
            return;
        }
        Object role = attrs.getRequest().getAttribute("role");
        String actual = role == null ? "" : String.valueOf(role);
        for (String allowed : ann.value()) {
            if (allowed.equalsIgnoreCase(actual)) {
                return;
            }
        }
        throw new RoleForbiddenException("无权访问该资源，需要角色：" + Arrays.toString(ann.value()));
    }

    /** 方法级注解优先，其次类级注解 */
    private RequireRole resolveAnnotation(JoinPoint jp) {
        MethodSignature ms = (MethodSignature) jp.getSignature();
        Method method = ms.getMethod();
        RequireRole ann = method.getAnnotation(RequireRole.class);
        if (ann == null) {
            ann = jp.getTarget().getClass().getAnnotation(RequireRole.class);
        }
        return ann;
    }
}
