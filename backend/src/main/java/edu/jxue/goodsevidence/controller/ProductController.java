package edu.jxue.goodsevidence.controller;

import edu.jxue.goodsevidence.common.RequireRole;
import edu.jxue.goodsevidence.common.Result;
import edu.jxue.goodsevidence.dto.ProductRegisterDTO;
import edu.jxue.goodsevidence.service.FileStorageService;
import edu.jxue.goodsevidence.service.ProductService;
import edu.jxue.goodsevidence.vo.ProductVO;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.util.List;

/**
 * 商品登记与一物一码（模块二）
 */
@RestController
@RequestMapping("/api/product")
@RequireRole({"ENTERPRISE", "ADMIN"})
public class ProductController {

    @Autowired
    private ProductService productService;
    @Autowired
    private FileStorageService fileStorageService;

    /** 登记商品 + 生成一物一码，返回商品详情（含二维码地址） */
    @PostMapping("/register")
    public Result<ProductVO> register(@RequestBody ProductRegisterDTO dto) {
        try {
            return Result.ok(productService.register(dto));
        } catch (Exception e) {
            return Result.fail(e.getMessage());
        }
    }

    /**
     * 上传商品实物图：返回 /api/file/{对象名}，登记时作为 imageUrl 一并提交。
     * 图片与二维码共用本地/MinIO 存储与 /api/file/** 免鉴权回读链路。
     */
    @PostMapping("/upload-image")
    public Result<String> uploadImage(@RequestParam("file") MultipartFile file) {
        if (file == null || file.isEmpty()) {
            return Result.fail("请选择要上传的图片");
        }
        String contentType = file.getContentType();
        if (contentType == null || !contentType.startsWith("image/")) {
            return Result.fail("仅支持图片文件（png / jpg / gif）");
        }
        try {
            String objectName = fileStorageService.uploadFile(file);
            return Result.ok(fileStorageService.getUrl(objectName));
        } catch (Exception e) {
            return Result.fail(e.getMessage());
        }
    }

    /** 商品列表（含一物一码），供商品列表页使用 */
    @GetMapping("/list")
    public Result<List<ProductVO>> list() {
        try {
            return Result.ok(productService.listProducts());
        } catch (Exception e) {
            return Result.fail(e.getMessage());
        }
    }

    /** 商品详情（含一物一码与二维码地址） */
    @GetMapping("/{productId}")
    public Result<ProductVO> detail(@PathVariable Long productId) {
        try {
            ProductVO vo = productService.detail(productId);
            return vo == null ? Result.fail("商品不存在") : Result.ok(vo);
        } catch (Exception e) {
            return Result.fail(e.getMessage());
        }
    }

    /**
     * 换局域网 IP 后一键重刷历史二维码（一物一码编码不变）。
     * 只影响二维码里写的访问域名，不动商品/权属/链上存证数据。
     */
    @PostMapping("/refresh-qr")
    public Result<Integer> refreshQr() {
        try {
            return Result.ok(productService.refreshAllQrCodes());
        } catch (Exception e) {
            return Result.fail(e.getMessage());
        }
    }
}
