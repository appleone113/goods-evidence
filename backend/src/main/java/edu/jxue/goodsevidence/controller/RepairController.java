package edu.jxue.goodsevidence.controller;

import edu.jxue.goodsevidence.common.RequireRole;
import edu.jxue.goodsevidence.common.Result;
import edu.jxue.goodsevidence.dto.RepairDTO;
import edu.jxue.goodsevidence.service.RepairService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.*;

/**
 * 维修管理（模块五）
 */
@RestController
@RequestMapping("/api/repair")
@RequireRole({"ENTERPRISE", "ADMIN"})
public class RepairController {

    @Autowired
    private RepairService repairService;

    @PostMapping("/add")
    public Result<Long> add(@ModelAttribute RepairDTO dto) {
        try {
            return Result.ok(repairService.addRepair(dto));
        } catch (Exception e) {
            return Result.fail(e.getMessage());
        }
    }
}
