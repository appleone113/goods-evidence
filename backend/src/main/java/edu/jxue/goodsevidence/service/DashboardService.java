package edu.jxue.goodsevidence.service;

import edu.jxue.goodsevidence.vo.DashboardVO;
import edu.jxue.goodsevidence.vo.TrendPointVO;
import edu.jxue.goodsevidence.entity.OperationLog;

import java.util.List;

/**
 * 监管审计看板（模块八）
 */
public interface DashboardService {

    /**
     * 看板概览：各类业务总量 + 最近操作
     */
    DashboardVO overview();

    /**
     * 操作日志分页/条数查询
     */
    List<OperationLog> recentLogs(int limit);

    /**
     * 近 N 天权属转移趋势（监管看板交易趋势图数据源）
     */
    List<TrendPointVO> tradeTrend(int days);
}
