package edu.jxue.goodsevidence.controller;

import edu.jxue.goodsevidence.common.RequireRole;
import edu.jxue.goodsevidence.common.Result;
import edu.jxue.goodsevidence.dto.QualityDTO;
import edu.jxue.goodsevidence.service.QualityService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.*;

/**
 * 质检管理（模块五）
 */
@RestController
@RequestMapping("/api/quality")
@RequireRole({"ENTERPRISE", "ADMIN"})
public class QualityController {

    @Autowired
    private QualityService qualityService;

    @PostMapping("/add")
    public Result<Long> add(@ModelAttribute QualityDTO dto) {
        try {
            return Result.ok(qualityService.addQuality(dto));
        } catch (Exception e) {
            return Result.fail(e.getMessage());
        }
    }
}
