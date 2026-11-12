package edu.jxue.goodsevidence.dto;

import lombok.Data;
import org.springframework.web.multipart.MultipartFile;

/**
 * 质检录入入参
 */
@Data
public class QualityDTO {
    private Long productId;
    private String org;          // 质检机构
    private String result;        // 合格 / 不合格
    private MultipartFile report; // 质检报告（存 MinIO）
}
