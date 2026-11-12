package edu.jxue.goodsevidence.config;

import edu.jxue.goodsevidence.common.Result;
import edu.jxue.goodsevidence.entity.OperationLog;
import edu.jxue.goodsevidence.mapper.OperationLogMapper;
import edu.jxue.goodsevidence.mapper.SysUserMapper;
import jakarta.servlet.http.HttpServletRequest;
import org.aspectj.lang.JoinPoint;
import org.aspectj.lang.annotation.AfterReturning;
import org.aspectj.lang.annotation.AfterThrowing;
import org.aspectj.lang.annotation.Aspect;
import org.aspectj.lang.annotation.Pointcut;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;
import org.springframework.web.multipart.MultipartFile;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

/**
 * 审计日志切面（模块八监管留痕的数据来源）
 *
 * 统一拦截所有 Controller 方法，请求处理完成后向 operation_log 表写入一条操作记录，
 * 使监管端「操作日志 / 监管看板」无需在各业务模块里手工埋点。
 * 写日志的异常一律吞掉，绝不影响正常业务流程。
 */
@Aspect
@Component
public class AuditLogAspect {

    /**
     * 公开接口（登录/注册）用于向本切面透传操作人账号名的 request 属性名。
     * 这些接口没有 JWT，request 里不会有 userId，若不透传就只能记 anonymous。
     */
    public static final String ATTR_OPERATOR = "auditOperator";

    @Autowired
    private OperationLogMapper operationLogMapper;
    @Autowired
    private SysUserMapper sysUserMapper;

    /** 切点：controller 包下所有类的所有方法 */
    @Pointcut("execution(* edu.jxue.goodsevidence.controller..*.*(..))")
    public void controllerPointcut() {
    }

    @AfterReturning(pointcut = "controllerPointcut()", returning = "ret")
    public void afterReturn(JoinPoint jp, Object ret) {
        writeLog(jp, ret, null);
    }

    @AfterThrowing(pointcut = "controllerPointcut()", throwing = "ex")
    public void afterThrow(JoinPoint jp, Exception ex) {
        writeLog(jp, null, ex);
    }

    private void writeLog(JoinPoint jp, Object ret, Exception ex) {
        try {
            ServletRequestAttributes attrs = (ServletRequestAttributes) RequestContextHolder.currentRequestAttributes();
            if (attrs == null) {
                return;
            }
            HttpServletRequest request = attrs.getRequest();
            Object userId = request.getAttribute("userId");
            Object role = request.getAttribute("role");
            Object named = request.getAttribute(ATTR_OPERATOR);

            // 1) 操作人：优先显式透传的账号名，其次按 userId 查用户名，再退化成 角色:用户ID，最后匿名
            String operator = resolveOperator(userId, role, named);

            // 2) 操作类型：类名.方法名
            String action = jp.getSignature().getDeclaringType().getSimpleName()
                    + "." + jp.getSignature().getName();

            // 3) 详情：HTTP 方法 + URI + 业务参数摘要 + 结果
            StringBuilder sb = new StringBuilder();
            sb.append(request.getMethod()).append(' ').append(request.getRequestURI());
            String args = describeArgs(jp.getArgs());
            if (!args.isEmpty()) {
                sb.append(" args=").append(args);
            }
            if (ex != null) {
                sb.append(" => ERROR: ").append(ex.getMessage());
            } else if (ret instanceof Result<?> r) {
                sb.append(" => ").append(r.getCode()).append(' ').append(r.getMessage());
            }

            OperationLog ol = new OperationLog();
            ol.setOperator(operator);
            ol.setAction(action);
            ol.setDetail(sb.length() > 450 ? sb.substring(0, 450) : sb.toString());
            ol.setTime(LocalDateTime.now());
            operationLogMapper.insert(ol);
        } catch (Exception ignored) {
            // 审计日志写入失败不影响业务
        }
    }

    private String resolveOperator(Object userId, Object role, Object named) {
        // 公开接口（登录/注册）显式透传的账号名最可信
        if (named != null && !named.toString().isBlank()) {
            return named.toString();
        }
        if (userId == null) {
            return "anonymous";
        }
        try {
            var user = sysUserMapper.selectById(Long.parseLong(String.valueOf(userId)));
            if (user != null && user.getUsername() != null) {
                return user.getUsername();
            }
        } catch (Exception ignored) {
            // 查不到用户名则退化
        }
        return role != null ? role + ":" + userId : String.valueOf(userId);
    }

    /** 参数摘要：跳过 Servlet 内置对象与文件对象，只保留业务参数 */
    private String describeArgs(Object[] args) {
        if (args == null || args.length == 0) {
            return "";
        }
        List<String> parts = new ArrayList<>();
        for (Object a : args) {
            if (a == null) continue;
            if (a instanceof HttpServletRequest || a instanceof jakarta.servlet.http.HttpServletResponse
                    || a instanceof MultipartFile) {
                continue;
            }
            parts.add(a.toString());
        }
        return String.join(", ", parts);
    }
}
