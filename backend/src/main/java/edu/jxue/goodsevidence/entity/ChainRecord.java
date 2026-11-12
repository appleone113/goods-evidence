package edu.jxue.goodsevidence.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * 上链存证记录：业务数据哈希上链后的回执
 */
@Data
@TableName("chain_record")
public class ChainRecord {

    @TableId(type = IdType.ASSIGN_ID)
    private Long evidenceId;

    private String bizType;        // trade / quality / repair
    private Long bizId;            // 关联业务ID（统一存商品ID，验真时按它反查存证）
    private String originHash;     // 业务数据原文哈希
    private Long blockHeight;      // 区块高度
    private String txHash;         // 交易哈希
    private LocalDateTime chainTime;
}
