package edu.jxue.goodsevidence.service.impl;

import edu.jxue.goodsevidence.dto.EvidenceResult;
import edu.jxue.goodsevidence.entity.ChainRecord;
import edu.jxue.goodsevidence.entity.Product;
import edu.jxue.goodsevidence.mapper.ChainRecordMapper;
import edu.jxue.goodsevidence.mapper.ProductMapper;
import edu.jxue.goodsevidence.service.ChainService;
import jakarta.annotation.PostConstruct;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Service;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicLong;

/**
 * 链下模拟实现（无链环境也能跑通全流程）
 *
 * <p>当 application.yml 中 fisco.enabled=false（默认）时启用本实现，
 * 用内存结构模拟「权属状态机 + 存证哈希」两块链上账本，
 * 完整保留链上合约的核心语义：
 *   1) 权属登记后，链上才有一物一码对应的 owner；
 *   2) 权属转移时校验 require(msg.sender == owner)，不一致直接拒绝；
 *   3) 存证写入后可用哈希回查比对。
 *
 * <p>⚠️ 内存账本会在服务重启后清空，导致历史商品验真恒为 false。
 * 因此启动时从 chain_record（链上回执表）与 product（权属）回填账本，
 * 保证「重启前后验真结论一致」，答辩笔记本上反复启停也不会丢演示数据。
 * 真链模式下账本在链上，无需此步骤。</p>
 *
 * <p>答辩/联调时把 fisco.enabled 改成 true，即可无缝切换到真实的
 * FiscoChainServiceImpl（走 FISCO BCOS v3 Java SDK），业务代码零改动。</p>
 */
@Slf4j
@Service
@ConditionalOnProperty(name = "fisco.enabled", havingValue = "false", matchIfMissing = true)
public class MockChainServiceImpl implements ChainService {

    /** 模拟链上存证账本：evidenceId -> 原文哈希 */
    private final Map<Long, String> evidenceStore = new ConcurrentHashMap<>();
    /** 模拟链上权属账本：productId -> 权属人 */
    private final Map<Long, String> ownershipStore = new ConcurrentHashMap<>();

    private final AtomicLong idSeq = new AtomicLong(100000L);
    private final AtomicLong blockNumber = new AtomicLong(200000L);

    @Autowired
    private ChainRecordMapper chainRecordMapper;
    @Autowired
    private ProductMapper productMapper;

    /**
     * 启动即从数据库回填模拟账本。
     * chain_record 保存了每次存证的 evidenceId 与 originHash，等价于链上回执；
     * 只要回执在，验真时重算哈希就能与账本比对成功。
     * 用 try-catch 兜底：数据库未就绪时仅告警，不阻断服务启动。
     */
    @PostConstruct
    public void reloadLedger() {
        try {
            List<ChainRecord> records = chainRecordMapper.selectList(null);
            if (records != null) {
                for (ChainRecord r : records) {
                    if (r.getEvidenceId() != null && r.getOriginHash() != null
                            && !r.getOriginHash().isEmpty()) {
                        evidenceStore.put(r.getEvidenceId(), r.getOriginHash());
                    }
                }
            }
            List<Product> products = productMapper.selectList(null);
            if (products != null) {
                for (Product p : products) {
                    if (p.getCurrentOwner() != null && !p.getCurrentOwner().isEmpty()) {
                        ownershipStore.put(p.getProductId(), p.getCurrentOwner());
                    }
                }
            }
            log.info("[模拟链] 启动回填完成：存证 {} 条，权属 {} 条",
                    evidenceStore.size(), ownershipStore.size());
        } catch (Exception e) {
            log.warn("[模拟链] 账本回填失败（不影响启动）：{}", e.getMessage());
        }
    }

    @Override
    public EvidenceResult registerOwnership(Long productId, String owner) {
        ownershipStore.put(productId, owner);
        log.info("[模拟链] 权属初始化 productId={}, owner={}", productId, owner);
        return receipt(null);
    }

    @Override
    public EvidenceResult saveEvidence(Long evidenceId, String bizType, String originHash) {
        // 与真链保持一致：ID 由链下生成并传入，模拟账本按该 ID 存
        long id = evidenceId != null && evidenceId > 0 ? evidenceId : idSeq.incrementAndGet();
        evidenceStore.put(id, originHash);
        log.info("[模拟链] 存证上链 bizType={}, hash={}, evidenceId={}", bizType, shortHash(originHash), id);
        return receipt(id);
    }

    @Override
    public EvidenceResult transferOwnership(Long productId, String from, String to) throws Exception {
        String current = ownershipStore.get(productId);
        // 等价于合约里的 require(owner == msg.sender)
        if (current != null && !Objects.equals(current, from)) {
            throw new RuntimeException("链上权属校验失败：当前权属人为 " + current + "，无法由 " + from + " 发起转移");
        }
        if (current == null) {
            // 商品未在链上登记过，按出让方初始化后再转移
            ownershipStore.put(productId, from);
        }
        ownershipStore.put(productId, to);
        log.info("[模拟链] 权属转移 productId={}: {} -> {}", productId, from, to);
        return receipt(null);
    }

    @Override
    public boolean verifyEvidence(Long evidenceId, String originHash) {
        String stored = evidenceStore.get(evidenceId);
        boolean ok = stored != null && stored.equalsIgnoreCase(originHash);
        log.info("[模拟链] 存证校验 evidenceId={}, result={}", evidenceId, ok);
        return ok;
    }

    /** 生成模拟链回执（存证ID + 区块高度 + 交易哈希） */
    private EvidenceResult receipt(Long evidenceId) {
        EvidenceResult r = new EvidenceResult();
        long height = blockNumber.incrementAndGet();
        r.setEvidenceId(evidenceId);
        r.setBlockNumber(height);
        r.setTxHash("0x" + sha256(String.valueOf(height) + "-" + System.nanoTime()));
        return r;
    }

    private static String sha256(String input) {
        try {
            MessageDigest md = MessageDigest.getInstance("SHA-256");
            byte[] digest = md.digest(input.getBytes(StandardCharsets.UTF_8));
            StringBuilder sb = new StringBuilder();
            for (byte b : digest) {
                sb.append(String.format("%02x", b));
            }
            return sb.toString();
        } catch (Exception e) {
            return Long.toHexString(System.nanoTime());
        }
    }

    private static String shortHash(String hash) {
        if (hash == null) return "";
        return hash.length() > 16 ? hash.substring(0, 16) + "..." : hash;
    }
}
