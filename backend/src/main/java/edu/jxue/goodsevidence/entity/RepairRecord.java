package edu.jxue.goodsevidence.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * 维修记录
 */
@Data
@TableName("repair_record")
public class RepairRecord {

    @TableId(type = IdType.ASSIGN_ID)
    private Long repairId;

    private Long productId;
    private String repairer;       // 维修方
    private String content;        // 维修内容
    private String voucher;        // 维修凭证（MinIO 对象名）
    private LocalDateTime time;
}
