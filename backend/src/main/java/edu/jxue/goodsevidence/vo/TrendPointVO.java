package edu.jxue.goodsevidence.vo;

import lombok.Data;

/**
 * 交易趋势图数据点：某日的权属转移笔数
 */
@Data
public class TrendPointVO {

    private String date;    // yyyy-MM-dd
    private Integer count;  // 当日交易笔数（无交易补 0，保证前端横轴连续）

    public TrendPointVO() {
    }

    public TrendPointVO(String date, Integer count) {
        this.date = date;
        this.count = count;
    }
}
