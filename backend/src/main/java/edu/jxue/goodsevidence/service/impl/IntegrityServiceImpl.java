package edu.jxue.goodsevidence.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import edu.jxue.goodsevidence.common.HashUtil;
import edu.jxue.goodsevidence.entity.ChainRecord;
import edu.jxue.goodsevidence.entity.Product;
import edu.jxue.goodsevidence.entity.QualityRecord;
import edu.jxue.goodsevidence.entity.RepairRecord;
import edu.jxue.goodsevidence.entity.TradeRecord;
import edu.jxue.goodsevidence.entity.VerifyLog;
import edu.jxue.goodsevidence.mapper.ChainRecordMapper;
import edu.jxue.goodsevidence.mapper.ProductMapper;
import edu.jxue.goodsevidence.mapper.QualityRecordMapper;
import edu.jxue.goodsevidence.mapper.RepairRecordMapper;
import edu.jxue.goodsevidence.mapper.TradeRecordMapper;
import edu.jxue.goodsevidence.mapper.VerifyLogMapper;
import edu.jxue.goodsevidence.service.IntegrityService;
import edu.jxue.goodsevidence.vo.AlertVO;
import edu.jxue.goodsevidence.vo.IntegrityVO;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * 一致性巡检实现。
 * <p>哈希原文口径必须与各业务 ServiceImpl 写入时保持逐字节一致：
 * <pre>
 *   product : productId|PRODUCT|name|registerBy
 *   quality : productId|QUALITY|org|result|qualityId
 *   repair  : productId|REPAIR|repairer|content|repairId
 *   trade   : productId|TRADE|seller|buyer|tradeId
 * </pre>
 * <p>注意 chain_record.bizId 统一存商品ID（验真靠它反查），因此同类型多条存证
 * 与同商品多条业务记录之间没有一一对应的外键，比对策略为：
 * 对每条链上存证，用该商品同类型的全部业务记录逐条重算，只要有一条能对上
 * 即判 PASS；全部对不上判 MISMATCH（链下被篡改或记录被删）。
 */
@Service
public class IntegrityServiceImpl implements IntegrityService {

    @Autowired
    private ChainRecordMapper chainRecordMapper;
    @Autowired
    private ProductMapper productMapper;
    @Autowired
    private QualityRecordMapper qualityRecordMapper;
    @Autowired
    private RepairRecordMapper repairRecordMapper;
    @Autowired
    private TradeRecordMapper tradeRecordMapper;
    @Autowired
    private VerifyLogMapper verifyLogMapper;

    /** 最近一次巡检结果缓存（内存），供告警接口读取；重启后为"未巡检" */
    private volatile IntegrityVO lastCheck;

    @Override
    public IntegrityVO fullCheck() {
        // 一次性拉全量数据（当前规模数百条，避免逐条查询造成 N+1）
        List<ChainRecord> evidences = chainRecordMapper.selectList(null);
        List<Product> products = productMapper.selectList(null);
        List<QualityRecord> qualities = qualityRecordMapper.selectList(null);
        List<RepairRecord> repairs = repairRecordMapper.selectList(null);
        List<TradeRecord> trades = tradeRecordMapper.selectList(null);

        // 按商品ID归集各类型业务记录：productId -> 该类型的候选原文列表
        Map<Long, List<String>> candidateMap = new HashMap<>();
        for (Product p : products) {
            candidateMap.computeIfAbsent(p.getProductId(), k -> new ArrayList<>())
                    .add(p.getProductId() + "|PRODUCT|" + p.getName() + "|" + p.getRegisterBy());
        }
        for (QualityRecord q : qualities) {
            candidateMap.computeIfAbsent(q.getProductId(), k -> new ArrayList<>())
                    .add(q.getProductId() + "|QUALITY|" + q.getOrg() + "|" + q.getResult() + "|" + q.getQualityId());
        }
        for (RepairRecord r : repairs) {
            candidateMap.computeIfAbsent(r.getProductId(), k -> new ArrayList<>())
                    .add(r.getProductId() + "|REPAIR|" + r.getRepairer() + "|" + r.getContent() + "|" + r.getRepairId());
        }
        for (TradeRecord t : trades) {
            candidateMap.computeIfAbsent(t.getProductId(), k -> new ArrayList<>())
                    .add(t.getProductId() + "|TRADE|" + t.getSeller() + "|" + t.getBuyer() + "|" + t.getTradeId());
        }
        Map<Long, Product> productMap = new HashMap<>();
        for (Product p : products) {
            productMap.put(p.getProductId(), p);
        }

        IntegrityVO vo = new IntegrityVO();
        vo.setCheckedAt(LocalDateTime.now());
        List<IntegrityVO.Item> items = new ArrayList<>();
        int pass = 0;

        for (ChainRecord cr : evidences) {
            vo.setCheckedCount(vo.getCheckedCount() + 1);
            Long productId = cr.getBizId();
            boolean ok = false;
            for (String plaintext : candidateMap.getOrDefault(productId, List.of())) {
                if (HashUtil.sha256(plaintext).equals(cr.getOriginHash())) {
                    ok = true;
                    break;
                }
            }
            if (ok) {
                pass++;
                continue;
            }
            vo.setMismatchCount(vo.getMismatchCount() + 1);
            IntegrityVO.Item item = new IntegrityVO.Item();
            item.setType("MISMATCH");
            item.setBizType(cr.getBizType());
            item.setProductId(productId);
            item.setEvidenceId(cr.getEvidenceId());
            item.setExpectedHash(shortHash(firstCandidate(candidateMap, productId, cr.getBizType())));
            item.setChainHash(shortHash(cr.getOriginHash()));
            item.setDetail(productId == null
                    ? "该" + cnType(cr.getBizType()) + "存证记录未关联商品ID（旧版本数据），无法定位业务记录，建议清理或补录"
                    : "商品[" + displayName(productMap, productId) + "]的 " + cnType(cr.getBizType())
                    + " 存证与链下当前数据不一致：链下数据可能已被篡改，或业务记录被删除");
            items.add(item);
        }

        // 应上链未上链检查：每件商品必须有登记存证（ProductServiceImpl 已保证，此处兜底发现漏网）
        Map<Long, Boolean> hasProductEvidence = new HashMap<>();
        for (ChainRecord cr : evidences) {
            if ("product".equals(cr.getBizType())) {
                hasProductEvidence.put(cr.getBizId(), Boolean.TRUE);
            }
        }
        for (Product p : products) {
            if (!hasProductEvidence.containsKey(p.getProductId())) {
                vo.setMissingCount(vo.getMissingCount() + 1);
                IntegrityVO.Item item = new IntegrityVO.Item();
                item.setType("MISSING");
                item.setBizType("product");
                item.setProductId(p.getProductId());
                item.setDetail("商品[" + p.getName() + "]（ID " + p.getProductId() + "）没有登记存证，"
                        + "可能创建于修复前的旧版本，建议重新登记或补录存证");
                items.add(item);
            }
        }

        vo.setPassCount(pass);
        vo.setHealthy(vo.getMismatchCount() == 0 && vo.getMissingCount() == 0);
        vo.setItems(items);
        this.lastCheck = vo;
        return vo;
    }

    @Override
    public AlertVO alerts() {
        AlertVO vo = new AlertVO();
        Long failTotal = verifyLogMapper.selectCount(new LambdaQueryWrapper<VerifyLog>().eq(VerifyLog::getResult, 0));
        vo.setVerifyFailTotal(failTotal == null ? 0 : failTotal);
        vo.setRecentFailed(verifyLogMapper.selectList(new LambdaQueryWrapper<VerifyLog>()
                .eq(VerifyLog::getResult, 0)
                .orderByDesc(VerifyLog::getTime)
                .last("limit 20")));
        if (lastCheck != null) {
            vo.setLastMismatchCount((long) lastCheck.getMismatchCount());
            vo.setLastMissingCount((long) lastCheck.getMissingCount());
            vo.setLastCheckedAt(lastCheck.getCheckedAt().format(DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss")));
        }
        return vo;
    }

    private String firstCandidate(Map<Long, List<String>> candidateMap, Long productId, String bizType) {
        return candidateMap.getOrDefault(productId, List.of()).stream().findFirst().orElse("");
    }

    private String shortHash(String hash) {
        return hash == null ? "" : (hash.length() <= 16 ? hash : hash.substring(0, 16) + "…");
    }

    private String displayName(Map<Long, Product> productMap, Long productId) {
        Product p = productMap.get(productId);
        return p == null ? String.valueOf(productId) : p.getName();
    }

    private String cnType(String bizType) {
        switch (bizType == null ? "" : bizType) {
            case "product": return "登记";
            case "quality": return "质检";
            case "repair": return "维修";
            case "trade": return "权属转移";
            default: return bizType;
        }
    }
}
