package edu.jxue.goodsevidence.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * 验真日志
 */
@Data
@TableName("verify_log")
public class VerifyLog {

    @TableId(type = IdType.ASSIGN_ID)
    private Long verifyId;

    private Long productId;
    private Long codeId;
    private String verifier;       // 验真方
    private Integer result;        // 0-失败 1-成功
    private String ip;
    private LocalDateTime time;
}
