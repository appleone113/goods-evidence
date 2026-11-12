package edu.jxue.goodsevidence.service;

import edu.jxue.goodsevidence.vo.VerifyResultVO;

/**
 * 扫码验真与溯源（模块七）
 */
public interface VerifyService {

    /**
     * 扫码验真：解析码 → 查商品与履历 → 链上哈希校验 → 记录验真日志
     * @param code 二维码内容中的 code（如 P123）
     */
    VerifyResultVO verify(String code) throws Exception;
}
