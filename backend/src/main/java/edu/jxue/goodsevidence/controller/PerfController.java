package edu.jxue.goodsevidence.controller;

import edu.jxue.goodsevidence.common.RequireRole;
import edu.jxue.goodsevidence.common.Result;
import edu.jxue.goodsevidence.perf.ChainThroughputBenchmark;
import edu.jxue.goodsevidence.service.ChainService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * 性能测试接口（模块九）
 *
 * 暴露给监管端/答辩现场：直接触发链上存证吞吐量基准，拿到"多少笔/秒"这组数据。
 * 说明：默认 fisco.enabled=false 时走 MockChainServiceImpl（内存账本），
 * 测出来的是**链下模拟吞吐**，量级远高于真实链；接真链后同一接口即可测真实吞吐，
 * 两组数据正好可以做论文里的对比。
 */
@RestController
@RequestMapping("/api/perf")
@RequireRole("ADMIN")
public class PerfController {

    @Autowired
    private ChainThroughputBenchmark benchmark;
    @Autowired
    private ChainService chainService;

    /**
     * 顺序提交 rounds 笔存证，返回耗时与吞吐
     * @param rounds 笔数，默认 1000
     */
    @GetMapping("/benchmark")
    public Result<Map<String, Object>> benchmark(@RequestParam(defaultValue = "1000") int rounds) {
        try {
            ChainThroughputBenchmark.BenchmarkResult r = benchmark.run(rounds);
            Map<String, Object> m = new LinkedHashMap<>();
            m.put("chainImpl", chainService.getClass().getSimpleName());
            m.put("rounds", r.rounds);
            m.put("elapsedMs", r.elapsedMs);
            m.put("opsPerSec", Math.round(r.opsPerSec * 100.0) / 100.0);
            m.put("avgMsPerOp", Math.round(r.elapsedMs * 1000.0 / r.rounds) / 1000.0);
            m.put("summary", r.toString());
            return Result.ok(m);
        } catch (Exception e) {
            return Result.fail(e.getMessage());
        }
    }
}
