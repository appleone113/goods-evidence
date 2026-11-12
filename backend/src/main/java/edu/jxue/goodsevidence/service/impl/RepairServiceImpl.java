package edu.jxue.goodsevidence.service.impl;

import com.baomidou.mybatisplus.core.toolkit.IdWorker;
import edu.jxue.goodsevidence.common.CacheService;
import edu.jxue.goodsevidence.common.HashUtil;
import edu.jxue.goodsevidence.dto.EvidenceResult;
import edu.jxue.goodsevidence.dto.RepairDTO;
import edu.jxue.goodsevidence.entity.ChainRecord;
import edu.jxue.goodsevidence.entity.RepairRecord;
import edu.jxue.goodsevidence.mapper.ChainRecordMapper;
import edu.jxue.goodsevidence.mapper.RepairRecordMapper;
import edu.jxue.goodsevidence.service.ChainService;
import edu.jxue.goodsevidence.service.FileStorageService;
import edu.jxue.goodsevidence.service.RepairService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;

/**
 * 维修管理实现：录入 + 凭证存 MinIO + 摘要上链存证
 */
@Service
public class RepairServiceImpl implements RepairService {

    @Autowired
    private RepairRecordMapper repairRecordMapper;
    @Autowired
    private ChainRecordMapper chainRecordMapper;
    @Autowired
    private FileStorageService fileStorageService;
    @Autowired
    private ChainService chainService;
    @Autowired
    private CacheService cacheService;

    @Override
    public Long addRepair(RepairDTO dto) throws Exception {
        RepairRecord record = new RepairRecord();
        record.setProductId(dto.getProductId());
        record.setRepairer(dto.getRepairer());
        record.setContent(dto.getContent());
        record.setTime(LocalDateTime.now());
        if (dto.getVoucher() != null && !dto.getVoucher().isEmpty()) {
            record.setVoucher(fileStorageService.uploadFile(dto.getVoucher()));
        }
        repairRecordMapper.insert(record);

        String plaintext = dto.getProductId() + "|REPAIR|" + dto.getRepairer() + "|" + dto.getContent() + "|" + record.getRepairId();
        String hash = HashUtil.sha256(plaintext);
        Long evidenceId = IdWorker.getId();
        EvidenceResult ev = chainService.saveEvidence(evidenceId, "repair", hash);

        ChainRecord chainRecord = new ChainRecord();
        chainRecord.setEvidenceId(evidenceId);
        chainRecord.setBizType("repair");
        chainRecord.setBizId(dto.getProductId());
        chainRecord.setOriginHash(hash);
        chainRecord.setBlockHeight(ev.getBlockNumber());
        chainRecord.setTxHash(ev.getTxHash());
        chainRecord.setChainTime(LocalDateTime.now());
        chainRecordMapper.insert(chainRecord);

        // 写后失效验真缓存，避免 60 秒内仍读到变更前的验真结果
        cacheService.evictVerify(dto.getProductId());

        return record.getRepairId();
    }
}
