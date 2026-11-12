package edu.jxue.goodsevidence.service;

import edu.jxue.goodsevidence.vo.AlertVO;
import edu.jxue.goodsevidence.vo.IntegrityVO;

/**
 * 链上链下一致性巡检与告警（模块八·监管审计）
 * <p>开题报告"链上链下数据一致性校验模型"的落地：
 * 对每一条链上存证，按当前链下业务数据重算 SHA-256 摘要并与链上登记的
 * origin_hash 双向比对，链下被篡改 / 业务记录被删时即可发现并定位。
 */
public interface IntegrityService {

    /** 全量巡检：重算哈希比对 + 应上链未上链检查 */
    IntegrityVO fullCheck();

    /** 告警概览：验真失败记录 + 最近一次巡检结果 */
    AlertVO alerts();
}
