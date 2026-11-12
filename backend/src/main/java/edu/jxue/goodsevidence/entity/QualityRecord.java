package edu.jxue.goodsevidence.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * 质检记录
 */
@Data
@TableName("quality_record")
public class QualityRecord {

    @TableId(type = IdType.ASSIGN_ID)
    private Long qualityId;

    private Long productId;
    private String org;            // 质检机构
    private String result;         // 合格 / 不合格
    private String reportFile;     // 质检报告文件（MinIO 对象名）
    private LocalDateTime time;
}
