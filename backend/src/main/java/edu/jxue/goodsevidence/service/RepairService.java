package edu.jxue.goodsevidence.service;

import edu.jxue.goodsevidence.dto.RepairDTO;

/**
 * 维修管理（模块五）
 */
public interface RepairService {

    /** 录入维修记录，凭证存 MinIO，摘要哈希上链 */
    Long addRepair(RepairDTO dto) throws Exception;
}
