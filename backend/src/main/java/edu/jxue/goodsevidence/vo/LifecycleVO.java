package edu.jxue.goodsevidence.vo;

import lombok.Data;

import java.util.List;

/**
 * 商品全生命周期履历
 */
@Data
public class LifecycleVO {
    private Long productId;
    private String name;
    private Integer status;
    private List<LifecycleEvent> events;   // 按时间排序的事件线
}
