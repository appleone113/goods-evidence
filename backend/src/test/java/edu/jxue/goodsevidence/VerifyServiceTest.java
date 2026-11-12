package edu.jxue.goodsevidence;

import edu.jxue.goodsevidence.common.CacheService;
import edu.jxue.goodsevidence.entity.ChainRecord;
import edu.jxue.goodsevidence.entity.Product;
import edu.jxue.goodsevidence.entity.TradeRecord;
import edu.jxue.goodsevidence.entity.VerifyLog;
import edu.jxue.goodsevidence.mapper.ChainRecordMapper;
import edu.jxue.goodsevidence.mapper.ProductMapper;
import edu.jxue.goodsevidence.mapper.TradeRecordMapper;
import edu.jxue.goodsevidence.mapper.VerifyLogMapper;
import edu.jxue.goodsevidence.service.ChainService;
import edu.jxue.goodsevidence.service.ProductLifecycleService;
import edu.jxue.goodsevidence.service.impl.VerifyServiceImpl;
import edu.jxue.goodsevidence.vo.VerifyResultVO;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * 扫码验真逻辑单元测试（Mockito，无需启动链/DB/Redis）（模块七/九）
 */
@ExtendWith(MockitoExtension.class)
class VerifyServiceTest {

    @Mock
    private ProductMapper productMapper;
    @Mock
    private TradeRecordMapper tradeRecordMapper;
    @Mock
    private ChainRecordMapper chainRecordMapper;
    @Mock
    private VerifyLogMapper verifyLogMapper;
    @Mock
    private ProductLifecycleService lifecycleService;
    @Mock
    private ChainService chainService;
    @Mock
    private CacheService cacheService;

    @InjectMocks
    private VerifyServiceImpl verifyService;

    @Test
    @DisplayName("商品不存在 → 验真未通过，并写入验真日志")
    void verifyNotFound() throws Exception {
        when(cacheService.getJson(anyString(), any())).thenReturn(null);
        when(productMapper.selectById(1L)).thenReturn(null);

        VerifyResultVO vo = verifyService.verify("P1");

        assertFalse(vo.isAuthentic());
        assertTrue(vo.getMessage().contains("未查询到"));
        verify(verifyLogMapper).insert(any(VerifyLog.class));
    }

    @Test
    @DisplayName("商品存在但无链上交易 → 验真未通过，回填缓存")
    void verifyNoTrade() throws Exception {
        Product p = new Product();
        p.setProductId(2L);
        p.setName("二手手机");
        when(cacheService.getJson(anyString(), any())).thenReturn(null);
        when(productMapper.selectById(2L)).thenReturn(p);
        when(tradeRecordMapper.selectOne(any())).thenReturn(null);
        when(lifecycleService.getLifecycle(2L)).thenReturn(new edu.jxue.goodsevidence.vo.LifecycleVO());

        VerifyResultVO vo = verifyService.verify("P2");

        assertFalse(vo.isAuthentic());
        verify(cacheService).putJson(anyString(), any(), anyLong());
    }

    @Test
    @DisplayName("存在链上存证且哈希一致 → 验真通过")
    void verifySuccess() throws Exception {
        Product p = new Product();
        p.setProductId(3L);
        p.setName("显卡");
        TradeRecord trade = new TradeRecord();
        trade.setTradeId(9L);
        trade.setSeller("甲");
        trade.setBuyer("乙");
        trade.setTxHash("0xabc");

        ChainRecord cr = new ChainRecord();
        cr.setEvidenceId(7L);

        when(cacheService.getJson(anyString(), any())).thenReturn(null);
        when(productMapper.selectById(3L)).thenReturn(p);
        when(tradeRecordMapper.selectOne(any())).thenReturn(trade);
        when(lifecycleService.getLifecycle(3L)).thenReturn(new edu.jxue.goodsevidence.vo.LifecycleVO());
        when(chainRecordMapper.selectOne(any())).thenReturn(cr);
        when(chainService.verifyEvidence(7L, anyString())).thenReturn(true);

        VerifyResultVO vo = verifyService.verify("P3");

        assertTrue(vo.isAuthentic());
        assertTrue(vo.getMessage().contains("通过"));
    }
}
