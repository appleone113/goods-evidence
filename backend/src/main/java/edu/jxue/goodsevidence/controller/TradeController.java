package edu.jxue.goodsevidence.controller;

import edu.jxue.goodsevidence.common.RequireRole;
import edu.jxue.goodsevidence.common.Result;
import edu.jxue.goodsevidence.service.TradeService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.*;

/**
 * 智能合约权属转移（模块四）
 */
@RestController
@RequestMapping("/api/trade")
@RequireRole({"ENTERPRISE", "ADMIN"})
public class TradeController {

    @Autowired
    private TradeService tradeService;

    @PostMapping("/transfer")
    public Result<Long> transfer(@RequestParam Long productId, @RequestParam String buyer) {
        try {
            return Result.ok(tradeService.transfer(productId, buyer));
        } catch (Exception e) {
            return Result.fail(e.getMessage());
        }
    }
}
