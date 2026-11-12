package edu.jxue.goodsevidence.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * 系统用户（三角色：USER / ENTERPRISE / ADMIN）
 */
@Data
@TableName("sys_user")
public class SysUser {

    @TableId(type = IdType.ASSIGN_ID)
    private Long userId;

    private String username;
    private String password;      // BCrypt 加密存储
    private String role;          // USER / ENTERPRISE / ADMIN
    private Long enterpriseId;    // 企业用户关联 enterprise
    private Integer status;       // 0-禁用 1-正常
    private LocalDateTime createTime;
}
