package edu.jxue.goodsevidence.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * 交易记录（权属转移）
 */
@Data
@TableName("trade_record")
public class TradeRecord {

    @TableId(type = IdType.ASSIGN_ID)
    private Long tradeId;

    private Long productId;
    private String seller;
    private String buyer;
    private LocalDateTime tradeTime;
    private Long blockHeight;     // 链上区块高度
    private String txHash;        // 链上交易哈希
}
