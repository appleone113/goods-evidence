package edu.jxue.goodsevidence.service;

import edu.jxue.goodsevidence.dto.EvidenceResult;

/**
 * 区块链调用层（模块六）接口
 * <p>其他业务模块只依赖本接口，具体实现见 FiscoChainServiceImpl（隔离 FISCO SDK 细节）。</p>
 */
public interface ChainService {

    /**
     * 保存存证哈希到区块链（调 EvidenceContract.save）
     *
     * <p><b>存证ID 由链下生成后传入，不由链上自增返回。</b>
     * 早期版本让合约自增 ID 再回读返回值，一旦 SDK 没解析出返回值就会全部落成 -1，
     * 导致 chain_record 主键冲突。改成"链下发号、链上按号存"后，
     * 业务主键与链上账本 key 天然一致，验真可直接用主键反查。</p>
     *
     * @param evidenceId 存证ID（调用方用雪花算法生成，即 chain_record 主键）
     * @param bizType    业务类型 trade/quality/repair
     * @param originHash 业务数据原文哈希（keccak256）
     * @return 区块高度 / 交易哈希 / 存证 ID
     */
    EvidenceResult saveEvidence(Long evidenceId, String bizType, String originHash) throws Exception;

    /**
     * 权属上链初始化：商品登记时同步链上 owner，使后续转移可以被合约 require 校验
     * @param productId 商品 ID
     * @param owner     初始权属人地址/账号
     */
    EvidenceResult registerOwnership(Long productId, String owner) throws Exception;

    /**
     * 权属转移（调 OwnershipContract.transfer）
     * 合约内部已强制 require(owner == msg.sender)
     * @param productId 商品 ID（与链上对应）
     * @param from      当前权属人地址
     * @param to        接收方地址
     */
    EvidenceResult transferOwnership(Long productId, String from, String to) throws Exception;

    /**
     * 校验链上存证哈希是否与本地一致
     */
    boolean verifyEvidence(Long evidenceId, String originHash) throws Exception;
}
