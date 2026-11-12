package edu.jxue.goodsevidence.dto;

import lombok.Data;

/**
 * 商品登记入参
 */
@Data
public class ProductRegisterDTO {
    private String name;
    private String category;
    private String description;
    private String imageUrl;     // 商品实物图（先调 /api/product/upload-image 拿到地址）
    private Long enterpriseId;   // 登记方企业
}
