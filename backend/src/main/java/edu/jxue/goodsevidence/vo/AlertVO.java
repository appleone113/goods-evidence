package edu.jxue.goodsevidence.vo;

import edu.jxue.goodsevidence.entity.VerifyLog;
import lombok.Data;

import java.util.List;

/**
 * 监管告警概览：验真失败 + 最近一次完整性巡检异常数
 */
@Data
public class AlertVO {

    private long verifyFailTotal;            // 历史验真失败总数
    private List<VerifyLog> recentFailed;    // 最近 20 条验真失败记录

    private Long lastMismatchCount;          // 最近一次巡检的不一致数（null = 尚未巡检）
    private Long lastMissingCount;           // 最近一次巡检的缺失数
    private String lastCheckedAt;            // 最近一次巡检时间
}
