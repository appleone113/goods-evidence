package edu.jxue.goodsevidence.service.impl;

import com.baomidou.mybatisplus.core.toolkit.IdWorker;
import edu.jxue.goodsevidence.common.CacheService;
import edu.jxue.goodsevidence.common.HashUtil;
import edu.jxue.goodsevidence.dto.EvidenceResult;
import edu.jxue.goodsevidence.entity.ChainRecord;
import edu.jxue.goodsevidence.entity.Product;
import edu.jxue.goodsevidence.entity.TradeRecord;
import edu.jxue.goodsevidence.mapper.ChainRecordMapper;
import edu.jxue.goodsevidence.mapper.ProductMapper;
import edu.jxue.goodsevidence.mapper.TradeRecordMapper;
import edu.jxue.goodsevidence.service.ChainService;
import edu.jxue.goodsevidence.service.TradeService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;

/**
 * 智能合约权属转移实现
 */
@Service
public class TradeServiceImpl implements TradeService {

    @Autowired
    private ProductMapper productMapper;
    @Autowired
    private TradeRecordMapper tradeRecordMapper;
    @Autowired
    private ChainRecordMapper chainRecordMapper;
    @Autowired
    private ChainService chainService;
    @Autowired
    private CacheService cacheService;

    @Override
    public Long transfer(Long productId, String buyer) throws Exception {
        Product product = productMapper.selectById(productId);
        if (product == null) {
            throw new RuntimeException("商品不存在");
        }
        String seller = product.getCurrentOwner();

        // 1) 链上权属转移：合约内强制 require(owner == msg.sender)
        EvidenceResult tr = chainService.transferOwnership(productId, seller, buyer);

        // 2) 落库交易记录
        TradeRecord record = new TradeRecord();
        record.setProductId(productId);
        record.setSeller(seller);
        record.setBuyer(buyer);
        record.setTradeTime(LocalDateTime.now());
        record.setBlockHeight(tr.getBlockNumber());
        record.setTxHash(tr.getTxHash());
        tradeRecordMapper.insert(record);

        // 3) 更新链下商品当前权属
        product.setCurrentOwner(buyer);
        product.setStatus(3);   // 3-已转移
        productMapper.updateById(product);

        // 4) 交易摘要额外上链存证
        String hash = HashUtil.sha256(productId + "|TRADE|" + seller + "|" + buyer + "|" + record.getTradeId());
        Long evidenceId = IdWorker.getId();
        EvidenceResult ev = chainService.saveEvidence(evidenceId, "trade", hash);
        ChainRecord chainRecord = new ChainRecord();
        // 存证ID 由链下生成并写入链上账本，业务主键与链上 key 天然一致
        chainRecord.setEvidenceId(evidenceId);
        chainRecord.setBizType("trade");
        // 关联商品ID：验真时靠它反查存证（不能靠 txHash——权属转移和存证是两笔不同的交易）
        chainRecord.setBizId(productId);
        chainRecord.setOriginHash(hash);
        chainRecord.setBlockHeight(ev.getBlockNumber());
        chainRecord.setTxHash(ev.getTxHash());
        chainRecord.setChainTime(LocalDateTime.now());
        chainRecordMapper.insert(chainRecord);

        // 5) 写后失效验真缓存：权属已变更，旧的验真结果（含旧权属人）必须立即作废
        cacheService.evictVerify(productId);

        return record.getTradeId();
    }
}
