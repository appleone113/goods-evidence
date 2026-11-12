package edu.jxue.goodsevidence.vo;

import lombok.Data;

import java.time.LocalDateTime;
import java.util.List;

/**
 * 链上链下数据完整性巡检结果（模块八·异常告警 / 开题报告"一致性校验模型"落地）
 */
@Data
public class IntegrityVO {

    private LocalDateTime checkedAt;     // 巡检时间
    private int checkedCount;            // 链上存证记录数（参与比对的条数）
    private int passCount;               // 摘要一致
    private int mismatchCount;           // 摘要不一致（链下被篡改或记录被删）
    private int missingCount;            // 应上链但无存证记录（目前仅检查"商品必须有登记存证"）
    private boolean healthy;             // mismatch + missing 是否为 0
    private List<Item> items;            // 异常明细（只返回异常与缺失，正常不展开）

    @Data
    public static class Item {
        private String type;             // MISMATCH / MISSING
        private String bizType;          // product / quality / repair / trade
        private Long productId;          // 商品ID（定位到具体商品）
        private Long evidenceId;         // 存证记录ID
        private String expectedHash;     // 按当前链下数据重算的哈希
        private String chainHash;        // 链上登记的原始哈希
        private String detail;           // 人类可读的定位说明
    }
}
