package edu.jxue.goodsevidence.dto;

import lombok.Data;

/**
 * 链上交易回执
 */
@Data
public class EvidenceResult {
    private Long evidenceId;     // 存证 ID（仅 saveEvidence 返回）
    private Long blockNumber;    // 区块高度
    private String txHash;       // 交易哈希
}
