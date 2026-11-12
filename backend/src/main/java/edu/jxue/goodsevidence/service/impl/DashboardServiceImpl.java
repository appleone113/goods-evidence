package edu.jxue.goodsevidence.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import edu.jxue.goodsevidence.entity.ChainRecord;
import edu.jxue.goodsevidence.entity.OperationLog;
import edu.jxue.goodsevidence.entity.TradeRecord;
import edu.jxue.goodsevidence.mapper.ChainRecordMapper;
import edu.jxue.goodsevidence.mapper.OperationLogMapper;
import edu.jxue.goodsevidence.mapper.ProductMapper;
import edu.jxue.goodsevidence.mapper.TradeRecordMapper;
import edu.jxue.goodsevidence.mapper.VerifyLogMapper;
import edu.jxue.goodsevidence.service.DashboardService;
import edu.jxue.goodsevidence.vo.DashboardVO;
import edu.jxue.goodsevidence.vo.TrendPointVO;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * 监管审计看板实现（模块八）
 */
@Service
public class DashboardServiceImpl implements DashboardService {

    @Autowired
    private ProductMapper productMapper;
    @Autowired
    private TradeRecordMapper tradeRecordMapper;
    @Autowired
    private VerifyLogMapper verifyLogMapper;
    @Autowired
    private ChainRecordMapper chainRecordMapper;
    @Autowired
    private OperationLogMapper operationLogMapper;

    @Override
    public DashboardVO overview() {
        DashboardVO vo = new DashboardVO();
        // 各业务表总量统计（MyBatis-Plus selectCount，无参上全部）
        vo.setProductCount(productMapper.selectCount(null));
        vo.setTradeCount(tradeRecordMapper.selectCount(null));
        vo.setVerifyCount(verifyLogMapper.selectCount(null));
        vo.setChainRecordCount(chainRecordMapper.selectCount(null));
        vo.setRecentOperations(recentLogs(10));
        return vo;
    }

    @Override
    public List<OperationLog> recentLogs(int limit) {
        QueryWrapper<OperationLog> wrapper = new QueryWrapper<>();
        wrapper.orderByDesc("time").last("LIMIT " + limit);
        return operationLogMapper.selectList(wrapper);
    }

    /**
     * 近 N 天权属转移趋势。
     * 交易表数据量小（原型规模），直接查全量后按日期在内存聚合，避免引入 XML/SQL 方言依赖；
     * 无交易的日期补 0，保证前端横轴连续、不会出现断点。
     */
    @Override
    public List<TrendPointVO> tradeTrend(int days) {
        int n = Math.max(1, Math.min(days, 60));
        LocalDate today = LocalDate.now();
        Map<LocalDate, Integer> counts = new HashMap<>();

        List<TradeRecord> all = tradeRecordMapper.selectList(null);
        if (all != null) {
            for (TradeRecord t : all) {
                if (t.getTradeTime() == null) {
                    continue;
                }
                counts.merge(t.getTradeTime().toLocalDate(), 1, Integer::sum);
            }
        }

        List<TrendPointVO> result = new ArrayList<>(n);
        for (int i = n - 1; i >= 0; i--) {
            LocalDate d = today.minusDays(i);
            result.add(new TrendPointVO(d.toString(), counts.getOrDefault(d, 0)));
        }
        return result;
    }
}
