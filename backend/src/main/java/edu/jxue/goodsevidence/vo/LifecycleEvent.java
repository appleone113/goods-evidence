package edu.jxue.goodsevidence.vo;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

/**
 * 生命周期事件节点
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class LifecycleEvent {
    private String type;          // REGISTER / TRADE / QUALITY / REPAIR
    private LocalDateTime time;
    private String operator;      // 操作方
    private String description;   // 描述
}
