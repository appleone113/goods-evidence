package edu.jxue.goodsevidence.service;

import edu.jxue.goodsevidence.dto.QualityDTO;

/**
 * 质检管理（模块五）
 */
public interface QualityService {

    /** 录入质检结果，报告存 MinIO，摘要哈希上链 */
    Long addQuality(QualityDTO dto) throws Exception;
}
