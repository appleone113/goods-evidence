package edu.jxue.goodsevidence.service.impl;

import com.baomidou.mybatisplus.core.toolkit.IdWorker;
import edu.jxue.goodsevidence.common.CacheService;
import edu.jxue.goodsevidence.common.HashUtil;
import edu.jxue.goodsevidence.dto.EvidenceResult;
import edu.jxue.goodsevidence.dto.QualityDTO;
import edu.jxue.goodsevidence.entity.ChainRecord;
import edu.jxue.goodsevidence.entity.QualityRecord;
import edu.jxue.goodsevidence.mapper.ChainRecordMapper;
import edu.jxue.goodsevidence.mapper.QualityRecordMapper;
import edu.jxue.goodsevidence.service.ChainService;
import edu.jxue.goodsevidence.service.FileStorageService;
import edu.jxue.goodsevidence.service.QualityService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;

/**
 * 质检管理实现：录入 + 报告存 MinIO + 摘要上链存证
 */
@Service
public class QualityServiceImpl implements QualityService {

    @Autowired
    private QualityRecordMapper qualityRecordMapper;
    @Autowired
    private ChainRecordMapper chainRecordMapper;
    @Autowired
    private FileStorageService fileStorageService;
    @Autowired
    private ChainService chainService;
    @Autowired
    private CacheService cacheService;

    @Override
    public Long addQuality(QualityDTO dto) throws Exception {
        QualityRecord record = new QualityRecord();
        record.setProductId(dto.getProductId());
        record.setOrg(dto.getOrg());
        record.setResult(dto.getResult());
        record.setTime(LocalDateTime.now());
        if (dto.getReport() != null && !dto.getReport().isEmpty()) {
            record.setReportFile(fileStorageService.uploadFile(dto.getReport()));
        }
        qualityRecordMapper.insert(record);

        // 业务摘要上链：存证ID 先由链下雪花算法生成，再连同哈希一起写入链上账本，
        // 保证 chain_record 主键 == 链上存证 key，验真时可直接用主键反查
        String plaintext = dto.getProductId() + "|QUALITY|" + dto.getOrg() + "|" + dto.getResult() + "|" + record.getQualityId();
        String hash = HashUtil.sha256(plaintext);
        Long evidenceId = IdWorker.getId();
        EvidenceResult ev = chainService.saveEvidence(evidenceId, "quality", hash);

        ChainRecord chainRecord = new ChainRecord();
        chainRecord.setEvidenceId(evidenceId);
        chainRecord.setBizType("quality");
        chainRecord.setBizId(dto.getProductId());
        chainRecord.setOriginHash(hash);
        chainRecord.setBlockHeight(ev.getBlockNumber());
        chainRecord.setTxHash(ev.getTxHash());
        chainRecord.setChainTime(LocalDateTime.now());
        chainRecordMapper.insert(chainRecord);

        // 写后失效验真缓存，避免 60 秒内仍读到变更前的验真结果
        cacheService.evictVerify(dto.getProductId());

        return record.getQualityId();
    }
}
