package edu.jxue.goodsevidence.dto;

import lombok.Data;
import org.springframework.web.multipart.MultipartFile;

/**
 * 维修录入入参
 */
@Data
public class RepairDTO {
    private Long productId;
    private String repairer;      // 维修方
    private String content;       // 维修内容
    private MultipartFile voucher; // 维修凭证（存 MinIO）
}
