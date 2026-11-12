package edu.jxue.goodsevidence.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * 操作日志（审计留痕）
 */
@Data
@TableName("operation_log")
public class OperationLog {

    @TableId(type = IdType.ASSIGN_ID)
    private Long logId;

    private String operator;        // 操作人
    private String action;         // 操作类型
    private String detail;         // 详情
    private LocalDateTime time;
}
