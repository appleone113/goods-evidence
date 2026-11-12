package edu.jxue.goodsevidence.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * 商品码（一物一码）：每个商品生成唯一二维码
 */
@Data
@TableName("product_code")
public class ProductCode {

    @TableId(type = IdType.ASSIGN_ID)
    private Long codeId;

    private Long productId;
    private String code;           // 一物一码唯一编码，如 P123456
    private String qrContent;      // 二维码内容，如 http://域名/verify?code=XXXX
    private LocalDateTime generateTime;
    private Integer status;        // 0-有效 1-作废
}
