package edu.jxue.goodsevidence;

import edu.jxue.goodsevidence.dto.EvidenceResult;
import edu.jxue.goodsevidence.perf.ChainThroughputBenchmark;
import edu.jxue.goodsevidence.service.ChainService;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.mockito.Mockito.when;

import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.when;

/**
 * 吞吐基准测试：用 Mock 的 ChainService 验证基准框架能正确统计耗时与吞吐（模块九）
 * 真实链环境请将 ChainService 替换为 FiscoChainServiceImpl 后再 run()。
 */
@ExtendWith(MockitoExtension.class)
class ChainThroughputBenchmarkTest {

    @Mock
    private ChainService chainService;
    @InjectMocks
    private ChainThroughputBenchmark benchmark;

    @Test
    @DisplayName("基准框架统计吞吐 > 0")
    void benchmarkRuns() throws Exception {
        EvidenceResult result = new EvidenceResult();
        result.setEvidenceId(1L);
        result.setBlockNumber(100L);
        result.setTxHash("0xtx");
        when(chainService.saveEvidence(org.mockito.ArgumentMatchers.anyLong(),
                org.mockito.ArgumentMatchers.anyString(),
                org.mockito.ArgumentMatchers.anyString()))
                .thenReturn(result);

        ChainThroughputBenchmark.BenchmarkResult r = benchmark.run(50);
        assertTrue(r.opsPerSec > 0, "吞吐应大于 0");
        System.out.println(r);
    }
}
