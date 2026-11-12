package edu.jxue.goodsevidence.controller;

import edu.jxue.goodsevidence.common.RequireRole;
import edu.jxue.goodsevidence.common.Result;
import edu.jxue.goodsevidence.entity.OperationLog;
import edu.jxue.goodsevidence.service.DashboardService;
import edu.jxue.goodsevidence.service.IntegrityService;
import edu.jxue.goodsevidence.vo.AlertVO;
import edu.jxue.goodsevidence.vo.DashboardVO;
import edu.jxue.goodsevidence.vo.IntegrityVO;
import edu.jxue.goodsevidence.vo.TrendPointVO;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/**
 * 监管审计看板接口（模块八）
 * 监管端使用，受 JwtInterceptor 保护（需角色 admin）
 */
@RestController
@RequestMapping("/api/admin")
@RequireRole("ADMIN")
public class AdminController {

    @Autowired
    private DashboardService dashboardService;
    @Autowired
    private IntegrityService integrityService;

    @GetMapping("/dashboard")
    public Result<DashboardVO> dashboard() {
        try {
            return Result.ok(dashboardService.overview());
        } catch (Exception e) {
            return Result.fail(e.getMessage());
        }
    }

    @GetMapping("/logs")
    public Result<List<OperationLog>> logs(@RequestParam(defaultValue = "20") int limit) {
        try {
            return Result.ok(dashboardService.recentLogs(limit));
        } catch (Exception e) {
            return Result.fail(e.getMessage());
        }
    }

    /** 近 N 天权属转移趋势（交易趋势图数据源，无交易日期补 0） */
    @GetMapping("/trade-trend")
    public Result<List<TrendPointVO>> tradeTrend(@RequestParam(defaultValue = "14") int days) {
        try {
            return Result.ok(dashboardService.tradeTrend(days));
        } catch (Exception e) {
            return Result.fail(e.getMessage());
        }
    }

    /**
     * 链上链下一致性巡检：全量重算业务哈希与链上存证比对，
     * 链下篡改 / 记录删除 / 漏上链都会被点名定位（开题"一致性校验模型"落地）
     */
    @GetMapping("/integrity/check")
    public Result<IntegrityVO> integrityCheck() {
        try {
            return Result.ok(integrityService.fullCheck());
        } catch (Exception e) {
            return Result.fail(e.getMessage());
        }
    }

    /** 告警概览：验真失败记录 + 最近一次巡检异常数 */
    @GetMapping("/alerts")
    public Result<AlertVO> alerts() {
        try {
            return Result.ok(integrityService.alerts());
        } catch (Exception e) {
            return Result.fail(e.getMessage());
        }
    }
}
