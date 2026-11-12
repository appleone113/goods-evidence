package edu.jxue.goodsevidence.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import edu.jxue.goodsevidence.common.JwtUtil;
import edu.jxue.goodsevidence.dto.LoginDTO;
import edu.jxue.goodsevidence.entity.SysUser;
import edu.jxue.goodsevidence.mapper.SysUserMapper;
import edu.jxue.goodsevidence.service.AuthService;
import edu.jxue.goodsevidence.vo.LoginVO;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.List;

/**
 * 用户与角色权限实现
 */
@Service
public class AuthServiceImpl implements AuthService {

    @Autowired
    private SysUserMapper userMapper;
    @Autowired
    private BCryptPasswordEncoder passwordEncoder;
    @Autowired
    private JwtUtil jwtUtil;

    @Override
    public LoginVO login(LoginDTO dto) {
        SysUser user = userMapper.selectOne(
                new LambdaQueryWrapper<SysUser>().eq(SysUser::getUsername, dto.getUsername()));
        if (user == null || !passwordEncoder.matches(dto.getPassword(), user.getPassword())) {
            throw new RuntimeException("用户名或密码错误");
        }
        if (user.getStatus() != null && user.getStatus() == 0) {
            throw new RuntimeException("账号已被禁用");
        }
        String token = jwtUtil.createToken(user.getUserId(), user.getRole());
        LoginVO vo = new LoginVO();
        vo.setUserId(user.getUserId());
        vo.setUsername(user.getUsername());
        vo.setRole(user.getRole());
        vo.setToken(token);
        vo.setEnterpriseId(user.getEnterpriseId());
        return vo;
    }

    /** 允许注册的角色白名单（与 @RequireRole 保持一致） */
    private static final List<String> ALLOW_ROLES = List.of("USER", "ENTERPRISE", "ADMIN");

    @Override
    public Long register(String username, String password, String role, Long enterpriseId) {
        // 角色大写归一 + 白名单校验，避免非法角色混入后绕过鉴权切面
        String normalized = role == null ? "USER" : role.trim().toUpperCase();
        if (!ALLOW_ROLES.contains(normalized)) {
            throw new RuntimeException("非法角色：" + role + "，可选值 USER / ENTERPRISE / ADMIN");
        }
        SysUser exist = userMapper.selectOne(
                new LambdaQueryWrapper<SysUser>().eq(SysUser::getUsername, username));
        if (exist != null) {
            throw new RuntimeException("用户名已存在");
        }
        SysUser user = new SysUser();
        user.setUsername(username);
        user.setPassword(passwordEncoder.encode(password));
        user.setRole(normalized);
        user.setEnterpriseId(enterpriseId);
        user.setStatus(1);
        user.setCreateTime(LocalDateTime.now());
        userMapper.insert(user);
        return user.getUserId();
    }
}
