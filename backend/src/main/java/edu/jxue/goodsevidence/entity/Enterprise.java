package edu.jxue.goodsevidence.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * 企业（商家 / 质检机构 / 维修方）
 */
@Data
@TableName("enterprise")
public class Enterprise {

    @TableId(type = IdType.ASSIGN_ID)
    private Long enterpriseId;

    private String name;          // 企业名称
    private String creditCode;    // 统一社会信用代码
    private String legalPerson;   // 法人
    private String contact;       // 联系方式
    private LocalDateTime createTime;
}
