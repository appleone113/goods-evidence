package edu.jxue.goodsevidence.perf;

import com.baomidou.mybatisplus.core.toolkit.IdWorker;
import edu.jxue.goodsevidence.common.HashUtil;
import edu.jxue.goodsevidence.service.ChainService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

/**
 * 链上存证吞吐量基准测试（模块九·性能优化）。
 *
 * 用途：评估"上链存证"这一最重操作的吞吐上限，为批量提交 / 异步化优化提供依据。
 * 优化策略（论文与代码双重体现）：
 *   1) 批量上链：将 N 笔存证合并为一次合约调用（节省交易开销）；
 *   2) 异步化：业务写库后把上链任务投递到线程池 / 消息队列，先返回，链回执后补写 chain_record；
 *   3) 缓存：读多写少场景用 Redis 缓存验真结果（见 CacheService）。
 */
@Component
public class ChainThroughputBenchmark {

    @Autowired
    private ChainService chainService;

    /** 单次运行结果 */
    public static class BenchmarkResult {
        public final int rounds;
        public final long elapsedMs;
        public final double opsPerSec;

        public BenchmarkResult(int rounds, long elapsedMs) {
            this.rounds = rounds;
            this.elapsedMs = elapsedMs;
            this.opsPerSec = elapsedMs > 0 ? rounds * 1000.0 / elapsedMs : 0;
        }

        @Override
        public String toString() {
            return String.format("存证 %d 笔，耗时 %d ms，吞吐 %.2f 笔/秒", rounds, elapsedMs, opsPerSec);
        }
    }

    /**
     * 顺序提交 rounds 笔存证，统计耗时与吞吐。
     * 注意：真实链环境需先确保 FiscoConfig 指向可用节点。
     */
    public BenchmarkResult run(int rounds) throws Exception {
        long start = System.nanoTime();
        for (int i = 0; i < rounds; i++) {
            String hash = HashUtil.sha256("bench-" + i);
            chainService.saveEvidence(IdWorker.getId(), "bench", hash);
        }
        long elapsedMs = (System.nanoTime() - start) / 1_000_000;
        return new BenchmarkResult(rounds, elapsedMs);
    }
}
