package edu.jxue.goodsevidence.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import edu.jxue.goodsevidence.entity.Product;
import edu.jxue.goodsevidence.entity.QualityRecord;
import edu.jxue.goodsevidence.entity.RepairRecord;
import edu.jxue.goodsevidence.entity.TradeRecord;
import edu.jxue.goodsevidence.mapper.ProductMapper;
import edu.jxue.goodsevidence.mapper.QualityRecordMapper;
import edu.jxue.goodsevidence.mapper.RepairRecordMapper;
import edu.jxue.goodsevidence.mapper.TradeRecordMapper;
import edu.jxue.goodsevidence.service.ProductLifecycleService;
import edu.jxue.goodsevidence.vo.LifecycleEvent;
import edu.jxue.goodsevidence.vo.LifecycleVO;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.stream.Collectors;

/**
 * 商品全生命周期履历实现：聚合登记/交易/质检/维修为时间线
 */
@Service
public class ProductLifecycleServiceImpl implements ProductLifecycleService {

    @Autowired
    private ProductMapper productMapper;
    @Autowired
    private TradeRecordMapper tradeRecordMapper;
    @Autowired
    private QualityRecordMapper qualityRecordMapper;
    @Autowired
    private RepairRecordMapper repairRecordMapper;

    @Override
    public LifecycleVO getLifecycle(Long productId) {
        Product product = productMapper.selectById(productId);
        if (product == null) {
            throw new RuntimeException("商品不存在");
        }
        LifecycleVO vo = new LifecycleVO();
        vo.setProductId(productId);
        vo.setName(product.getName());
        vo.setStatus(product.getStatus());

        List<LifecycleEvent> events = new ArrayList<>();
        events.add(new LifecycleEvent("REGISTER", product.getRegisterTime(),
                String.valueOf(product.getRegisterBy()), "商品登记"));

        tradeRecordMapper.selectList(new LambdaQueryWrapper<TradeRecord>().eq(TradeRecord::getProductId, productId))
                .forEach(t -> events.add(new LifecycleEvent("TRADE", t.getTradeTime(), t.getSeller(),
                        "权属转移至 " + t.getBuyer())));

        qualityRecordMapper.selectList(new LambdaQueryWrapper<QualityRecord>().eq(QualityRecord::getProductId, productId))
                .forEach(q -> events.add(new LifecycleEvent("QUALITY", q.getTime(), q.getOrg(),
                        "质检结果：" + q.getResult())));

        repairRecordMapper.selectList(new LambdaQueryWrapper<RepairRecord>().eq(RepairRecord::getProductId, productId))
                .forEach(r -> events.add(new LifecycleEvent("REPAIR", r.getTime(), r.getRepairer(), r.getContent())));

        events.sort(Comparator.comparing(LifecycleEvent::getTime, Comparator.nullsLast(Comparator.naturalOrder())));
        vo.setEvents(events);
        return vo;
    }
}
