package edu.jxue.goodsevidence.service;

import edu.jxue.goodsevidence.vo.LifecycleVO;

/**
 * 商品全生命周期履历（模块三）
 */
public interface ProductLifecycleService {

    /** 聚合登记/交易/质检/维修，按时间线返回履历 */
    LifecycleVO getLifecycle(Long productId);
}
