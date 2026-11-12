package edu.jxue.goodsevidence.vo;

import edu.jxue.goodsevidence.entity.OperationLog;
import lombok.Data;

import java.util.List;

/**
 * 监管看板概览数据
 */
@Data
public class DashboardVO {
    private Long productCount;        // 商品总数
    private Long tradeCount;          // 交易总数
    private Long verifyCount;         // 验真次数
    private Long chainRecordCount;    // 上链存证数
    private List<OperationLog> recentOperations; // 最近操作日志
}
