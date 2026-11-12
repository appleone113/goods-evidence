package edu.jxue.goodsevidence.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * 商品
 */
@Data
@TableName("product")
public class Product {

    @TableId(type = IdType.ASSIGN_ID)
    private Long productId;

    private String name;
    private String category;
    private String description;
    private String imageUrl;       // 商品实物图（/api/file/... 相对地址）
    private String currentOwner;   // 当前权属人（账号或链上地址）
    private Integer status;        // 0-登记 1-在售 2-交易中 3-已转移
    private Long registerBy;       // 登记人
    private LocalDateTime registerTime;
}
