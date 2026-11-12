package edu.jxue.goodsevidence.controller;

import edu.jxue.goodsevidence.common.Result;
import edu.jxue.goodsevidence.config.AuditLogAspect;
import edu.jxue.goodsevidence.dto.LoginDTO;
import jakarta.servlet.http.HttpServletRequest;
import edu.jxue.goodsevidence.service.AuthService;
import edu.jxue.goodsevidence.vo.LoginVO;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.*;

/**
 * 用户与角色权限（模块一）
 */
@RestController
@RequestMapping("/api/auth")
public class AuthController {

    @Autowired
    private AuthService authService;

    /**
     * 登录。
     * <p>登录/注册走的是公开路径，JwtInterceptor 不会执行，request 里没有 userId，
     * AuditLogAspect 只能记成 anonymous。这里把账号名显式透传给切面，
     * 使"谁在什么时候登录（或登录失败）"也能进审计日志——这正是监管端最需要看的记录。</p>
     */
    @PostMapping("/login")
    public Result<LoginVO> login(@RequestBody LoginDTO dto, HttpServletRequest request) {
        if (dto != null && dto.getUsername() != null) {
            request.setAttribute(AuditLogAspect.ATTR_OPERATOR, dto.getUsername());
        }
        try {
            return Result.ok(authService.login(dto));
        } catch (Exception e) {
            return Result.fail(e.getMessage());
        }
    }

    @PostMapping("/register")
    public Result<Long> register(@RequestParam String username,
                                 @RequestParam String password,
                                 @RequestParam(defaultValue = "USER") String role,
                                 @RequestParam(required = false) Long enterpriseId,
                                 HttpServletRequest request) {
        request.setAttribute(AuditLogAspect.ATTR_OPERATOR, username);
        try {
            return Result.ok(authService.register(username, password, role, enterpriseId));
        } catch (Exception e) {
            return Result.fail(e.getMessage());
        }
    }
}
