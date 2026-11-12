package edu.jxue.goodsevidence.vo;

import lombok.Data;

/**
 * 验真结果
 */
@Data
public class VerifyResultVO {
    private Long productId;
    private String productName;
    private String imageUrl;     // 商品实物图（手机验真页展示）
    private boolean authentic;       // 验真是否通过
    private String message;
    private LifecycleVO lifecycle;   // 全流程履历
}
