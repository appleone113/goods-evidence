package edu.jxue.goodsevidence.vo;

import lombok.Data;

import java.time.LocalDateTime;

/**
 * 商品列表返回体：商品基本信息 + 一物一码 + 二维码访问地址
 * 供 /api/product/list 使用（商品列表页、履历页共用）
 */
@Data
public class ProductVO {

    private Long productId;
    private String name;
    private String category;
    private String description;
    private String imageUrl;       // 商品实物图地址
    private String currentOwner;   // 当前权属人
    private Integer status;        // 0-登记 1-在售 2-交易中 3-已转移
    private Long registerBy;
    private LocalDateTime registerTime;

    private String code;           // 一物一码编码，如 P123456
    private String qrContent;      // 二维码内容（验真 URL）
    private String qrUrl;          // 二维码图片地址（MinIO）
}
