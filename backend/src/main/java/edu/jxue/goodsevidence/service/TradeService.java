package edu.jxue.goodsevidence.service;

/**
 * 智能合约权属转移（模块四）
 */
public interface TradeService {

    /**
     * 发起权属转移：调链上合约 transfer（合约强制校验 owner == msg.sender）
     * @param productId 商品 ID
     * @param buyer     接收方地址
     * @return 交易记录 ID
     */
    Long transfer(Long productId, String buyer) throws Exception;
}
