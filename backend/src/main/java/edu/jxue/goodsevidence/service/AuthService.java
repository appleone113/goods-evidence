package edu.jxue.goodsevidence.service;

import edu.jxue.goodsevidence.dto.LoginDTO;
import edu.jxue.goodsevidence.vo.LoginVO;

/**
 * 用户与角色权限（模块一）
 */
public interface AuthService {

    /** 登录：校验密码（BCrypt），签发 JWT */
    LoginVO login(LoginDTO dto);

    /** 注册用户 */
    Long register(String username, String password, String role, Long enterpriseId);
}
