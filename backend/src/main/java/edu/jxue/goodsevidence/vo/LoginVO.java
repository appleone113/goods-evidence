package edu.jxue.goodsevidence.vo;

import lombok.Data;

/**
 * 登录返回（含 JWT）
 */
@Data
public class LoginVO {
    private Long userId;
    private String username;
    private String role;
    private String token;
    /** 关联企业ID（企业账号使用），前端登记商品时作为登记方回填 */
    private Long enterpriseId;
}
