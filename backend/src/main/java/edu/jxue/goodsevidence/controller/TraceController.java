package edu.jxue.goodsevidence.controller;

import edu.jxue.goodsevidence.common.RequireRole;
import edu.jxue.goodsevidence.common.Result;
import edu.jxue.goodsevidence.service.ProductLifecycleService;
import edu.jxue.goodsevidence.vo.LifecycleVO;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.*;

/**
 * 商品全生命周期履历查询（模块三）
 */
@RestController
@RequestMapping("/api/trace")
@RequireRole({"USER", "ENTERPRISE", "ADMIN"})
public class TraceController {

    @Autowired
    private ProductLifecycleService lifecycleService;

    @GetMapping("/{productId}")
    public Result<LifecycleVO> lifecycle(@PathVariable Long productId) {
        try {
            return Result.ok(lifecycleService.getLifecycle(productId));
        } catch (Exception e) {
            return Result.fail(e.getMessage());
        }
    }
}
