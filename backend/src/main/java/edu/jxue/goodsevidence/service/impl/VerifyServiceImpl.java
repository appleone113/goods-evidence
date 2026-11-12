package edu.jxue.goodsevidence.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import edu.jxue.goodsevidence.common.HashUtil;
import edu.jxue.goodsevidence.entity.ChainRecord;
import edu.jxue.goodsevidence.entity.Product;
import edu.jxue.goodsevidence.entity.TradeRecord;
import edu.jxue.goodsevidence.entity.VerifyLog;
import edu.jxue.goodsevidence.mapper.ChainRecordMapper;
import edu.jxue.goodsevidence.mapper.ProductMapper;
import edu.jxue.goodsevidence.mapper.TradeRecordMapper;
import edu.jxue.goodsevidence.mapper.VerifyLogMapper;
import edu.jxue.goodsevidence.common.CacheService;
import edu.jxue.goodsevidence.service.ChainService;
import edu.jxue.goodsevidence.service.ProductLifecycleService;
import edu.jxue.goodsevidence.service.VerifyService;
import edu.jxue.goodsevidence.vo.LifecycleVO;
import edu.jxue.goodsevidence.vo.VerifyResultVO;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;

/**
 * 扫码验真与溯源实现
 * 核心：重算业务摘要哈希 → 与链上存证（经由 txHash 关联）比对 → 返回真伪 + 履历
 */
@Service
public class VerifyServiceImpl implements VerifyService {

    @Autowired
    private ProductMapper productMapper;
    @Autowired
    private TradeRecordMapper tradeRecordMapper;
    @Autowired
    private ChainRecordMapper chainRecordMapper;
    @Autowired
    private VerifyLogMapper verifyLogMapper;
    @Autowired
    private ProductLifecycleService lifecycleService;
    @Autowired
    private ChainService chainService;
    @Autowired
    private CacheService cacheService;

    /** 验真结果缓存键前缀 + TTL（秒）。读多写少，短 TTL 即可保证时效 */
    private static final String CACHE_PREFIX = CacheService.VERIFY_PREFIX;
    private static final long CACHE_TTL = 60;

    @Override
    public VerifyResultVO verify(String code) throws Exception {
        Long productId = parseCode(code);

        // 1) 缓存命中直接返回（性能优化：减轻 DB + 链查询压力）
        VerifyResultVO cached = cacheService.getJson(CACHE_PREFIX + code, VerifyResultVO.class);
        if (cached != null) {
            return cached;
        }

        Product product = productMapper.selectById(productId);

        VerifyResultVO vo = new VerifyResultVO();
        if (product == null) {
            vo.setAuthentic(false);
            vo.setMessage("未查询到该商品，请确认二维码");
            recordLog(productId, 0, vo.getMessage());
            return vo;
        }
        vo.setProductId(productId);
        vo.setProductName(product.getName());
        vo.setImageUrl(product.getImageUrl());
        LifecycleVO lifecycle = lifecycleService.getLifecycle(productId);
        vo.setLifecycle(lifecycle);

        // 取最近一次交易，通过 biz_id 关联到链上存证，重算哈希并链上校验；
        // 若该商品尚未发生交易（刚登记），退化为「登记存证」校验，避免新商品扫码即判假。
        TradeRecord trade = tradeRecordMapper.selectOne(new LambdaQueryWrapper<TradeRecord>()
                .eq(TradeRecord::getProductId, productId)
                .orderByDesc(TradeRecord::getTradeTime).last("limit 1"));

        boolean authentic = false;

        // ① 优先用交易存证校验（含最新权重属人）
        ChainRecord cr = null;
        String hash = null;
        if (trade != null) {
            // ⚠️ 不能用 txHash 关联：trade_record.txHash 是「权属转移」那笔交易的哈希，
            // 而 chain_record.txHash 是「存证」那笔交易的哈希，两者本就不同，按它对不上。
            // 统一改用 biz_id（存证时写入的商品ID）反查。
            cr = chainRecordMapper.selectOne(new LambdaQueryWrapper<ChainRecord>()
                    .eq(ChainRecord::getBizType, "trade")
                    .eq(ChainRecord::getBizId, productId)
                    .orderByDesc(ChainRecord::getChainTime).last("limit 1"));
            if (cr != null) {
                hash = HashUtil.sha256(productId + "|TRADE|" + trade.getSeller()
                        + "|" + trade.getBuyer() + "|" + trade.getTradeId());
            }
        }

        // ② 无交易存证时回退到登记存证（原文口径与 ProductServiceImpl.register 保持一致）
        if (cr == null) {
            cr = chainRecordMapper.selectOne(new LambdaQueryWrapper<ChainRecord>()
                    .eq(ChainRecord::getBizType, "product")
                    .eq(ChainRecord::getBizId, productId)
                    .orderByDesc(ChainRecord::getChainTime).last("limit 1"));
            if (cr != null) {
                hash = HashUtil.sha256(productId + "|PRODUCT|" + product.getName()
                        + "|" + product.getRegisterBy());
            }
        }

        if (cr != null && hash != null) {
            authentic = chainService.verifyEvidence(cr.getEvidenceId(), hash);
        }
        vo.setAuthentic(authentic);
        vo.setMessage(authentic ? "验真通过：权属与存证均已在链上确认" : "验真未通过：未找到链上存证或哈希不一致");
        recordLog(productId, authentic ? 1 : 0, vo.getMessage());

        // 2) 回填缓存（即便 Redis 不可用也会安全降级）
        cacheService.putJson(CACHE_PREFIX + code, vo, CACHE_TTL);
        return vo;
    }

    /** 解析码：P123 -> 123 */
    private Long parseCode(String code) {
        String c = code.startsWith("P") ? code.substring(1) : code;
        return Long.parseLong(c);
    }

    private void recordLog(Long productId, int result, String msg) {
        try {
            VerifyLog log = new VerifyLog();
            log.setProductId(productId);
            log.setVerifier("scan");
            log.setResult(result);
            log.setTime(LocalDateTime.now());
            verifyLogMapper.insert(log);
        } catch (Exception ignored) {
            // 验真日志失败不影响验真结果返回
        }
    }
}
