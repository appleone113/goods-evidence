package edu.jxue.goodsevidence.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.toolkit.IdWorker;
import edu.jxue.goodsevidence.common.CacheService;
import edu.jxue.goodsevidence.common.HashUtil;
import edu.jxue.goodsevidence.dto.EvidenceResult;
import edu.jxue.goodsevidence.dto.ProductRegisterDTO;
import edu.jxue.goodsevidence.entity.ChainRecord;
import edu.jxue.goodsevidence.entity.Product;
import edu.jxue.goodsevidence.entity.ProductCode;
import edu.jxue.goodsevidence.mapper.ChainRecordMapper;
import edu.jxue.goodsevidence.mapper.ProductCodeMapper;
import edu.jxue.goodsevidence.mapper.ProductMapper;
import edu.jxue.goodsevidence.service.ChainService;
import edu.jxue.goodsevidence.service.FileStorageService;
import edu.jxue.goodsevidence.service.ProductService;
import edu.jxue.goodsevidence.service.QrCodeService;
import edu.jxue.goodsevidence.vo.ProductVO;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.io.ByteArrayInputStream;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Base64;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * 商品业务实现：登记 + 一物一码 + 列表/详情查询
 */
@Service
public class ProductServiceImpl implements ProductService {

    @Autowired
    private ProductMapper productMapper;
    @Autowired
    private ProductCodeMapper productCodeMapper;
    @Autowired
    private QrCodeService qrCodeService;
    @Autowired
    private FileStorageService fileStorageService;
    @Autowired
    private ChainService chainService;
    @Autowired
    private ChainRecordMapper chainRecordMapper;
    @Autowired
    private CacheService cacheService;

    /**
     * 二维码里写的对外访问基址。默认 http://localhost:8080（仅本机可用），
     * 局域网演示时用 --app.public-base-url=http://<本机IP>:8080 覆盖，
     * 手机扫码才会跳到可达地址而不是「手机自己」。
     */
    @Value("${app.public-base-url:http://localhost:8080}")
    private String publicBaseUrl;

    @Override
    public ProductVO register(ProductRegisterDTO dto) throws Exception {
        // 1. 落库商品
        Product product = new Product();
        product.setName(dto.getName());
        product.setCategory(dto.getCategory());
        product.setDescription(dto.getDescription());
        product.setImageUrl(dto.getImageUrl());      // 商品实物图（可选，先上传再登记）
        product.setCurrentOwner(dto.getEnterpriseId() != null ? dto.getEnterpriseId().toString() : "");
        product.setStatus(0);                 // 0-登记
        product.setRegisterBy(dto.getEnterpriseId());
        product.setRegisterTime(LocalDateTime.now());
        productMapper.insert(product);

        // 2. 生成"一物一码"：二维码内容 = 手机可直接打开的公开验真页 URL
        //    /view 返回 HTML 结果页，/scan 保留 JSON 供前端 axios 调用
        String code = "P" + product.getProductId();
        String qrContent = buildQrContent(code);

        // 3. 生成二维码图片并上传文件存储（MinIO 或本地目录，取决于 storage.type）
        String base64 = qrCodeService.generateBase64(qrContent, 300);
        byte[] png = Base64.getDecoder().decode(base64);
        String objectName = "qr/" + code + ".png";
        fileStorageService.upload(objectName, new ByteArrayInputStream(png), png.length, "image/png");

        // 4. 落库商品码（同时保存可读的一物一码编码，供扫码验真查询）
        ProductCode productCode = new ProductCode();
        productCode.setProductId(product.getProductId());
        productCode.setCode(code);
        productCode.setQrContent(qrContent);
        productCode.setGenerateTime(LocalDateTime.now());
        productCode.setStatus(0);             // 0-有效
        productCodeMapper.insert(productCode);

        // 5. 权属上链初始化：把"登记人 = 初始权属人"写入链上状态机，
        //    后续 transfer 才能被合约 require(owner == msg.sender) 校验
        chainService.registerOwnership(product.getProductId(), product.getCurrentOwner());

        // 6. 登记摘要上链存证（补齐"哈希上链存证"模块的登记环节）
        //    原文口径：productId|PRODUCT|name|registerBy
        //    只取登记后不会变动的字段（registerBy、name），current_owner 会随交易变化，
        //    若纳入摘要，权属转移后重算必然对不上，故排除。
        String plaintext = product.getProductId() + "|PRODUCT|" + product.getName()
                + "|" + product.getRegisterBy();
        String hash = HashUtil.sha256(plaintext);
        Long evidenceId = IdWorker.getId();
        EvidenceResult ev = chainService.saveEvidence(evidenceId, "product", hash);

        ChainRecord chainRecord = new ChainRecord();
        chainRecord.setEvidenceId(evidenceId);
        chainRecord.setBizType("product");
        chainRecord.setBizId(product.getProductId());
        chainRecord.setOriginHash(hash);
        chainRecord.setBlockHeight(ev.getBlockNumber());
        chainRecord.setTxHash(ev.getTxHash());
        chainRecord.setChainTime(LocalDateTime.now());
        chainRecordMapper.insert(chainRecord);

        // 7. 回写业务表后失效该商品的验真缓存（开题「数据流转第 5 环」）
        //    新商品本无旧缓存，此处保持「登记/质检/维修/权属转移」四类写操作口径一致
        cacheService.evictVerify(product.getProductId());

        // 8. 回带商品详情（含一物一码与二维码地址），供登记页直接展示二维码
        return detail(product.getProductId());
    }

    @Override
    public List<ProductVO> listProducts() {
        List<Product> products = productMapper.selectList(
                new LambdaQueryWrapper<Product>().orderByDesc(Product::getRegisterTime));
        if (products.isEmpty()) {
            return Collections.emptyList();
        }
        // 一次性查全部商品码，避免 N+1 查询
        List<Long> ids = products.stream().map(Product::getProductId).toList();
        Map<Long, ProductCode> codeMap = productCodeMapper
                .selectList(new LambdaQueryWrapper<ProductCode>().in(ProductCode::getProductId, ids))
                .stream()
                .collect(Collectors.toMap(ProductCode::getProductId, Function.identity(), (a, b) -> a));

        List<ProductVO> result = new ArrayList<>(products.size());
        for (Product p : products) {
            result.add(toVO(p, codeMap.get(p.getProductId())));
        }
        return result;
    }

    @Override
    public ProductVO detail(Long productId) {
        Product product = productMapper.selectById(productId);
        if (product == null) {
            return null;
        }
        ProductCode pc = productCodeMapper.selectOne(
                new LambdaQueryWrapper<ProductCode>()
                        .eq(ProductCode::getProductId, productId)
                        .orderByDesc(ProductCode::getGenerateTime)
                        .last("limit 1"));
        return toVO(product, pc);
    }

    /** 统一构造二维码内容：换 IP 时只改 app.public-base-url，一处生效 */
    private String buildQrContent(String code) {
        String base = publicBaseUrl.endsWith("/")
                ? publicBaseUrl.substring(0, publicBaseUrl.length() - 1)
                : publicBaseUrl;
        return base + "/api/verify/view?code=" + code;
    }

    /**
     * 换局域网 IP 后重刷历史二维码：一物一码编码不变，
     * 只重写静态 PNG 图片内容与 product_code.qr_content，不动商品/权属/链上存证数据。
     * 已打印或已发出的旧二维码会失效（内容写死旧 IP），需重新导出图片。
     */
    @Override
    public int refreshAllQrCodes() throws Exception {
        List<ProductCode> codes = productCodeMapper.selectList(new LambdaQueryWrapper<ProductCode>());
        int count = 0;
        for (ProductCode pc : codes) {
            if (!StringUtils.hasText(pc.getCode())) {
                continue;
            }
            String qrContent = buildQrContent(pc.getCode());
            String base64 = qrCodeService.generateBase64(qrContent, 300);
            byte[] png = Base64.getDecoder().decode(base64);
            fileStorageService.upload("qr/" + pc.getCode() + ".png",
                    new ByteArrayInputStream(png), png.length, "image/png");
            pc.setQrContent(qrContent);
            productCodeMapper.updateById(pc);
            count++;
        }
        return count;
    }

    /** Product + ProductCode 组装 VO */
    private ProductVO toVO(Product p, ProductCode pc) {
        ProductVO vo = new ProductVO();
        vo.setProductId(p.getProductId());
        vo.setName(p.getName());
        vo.setCategory(p.getCategory());
        vo.setDescription(p.getDescription());
        vo.setImageUrl(p.getImageUrl());
        vo.setCurrentOwner(p.getCurrentOwner());
        vo.setStatus(p.getStatus());
        vo.setRegisterBy(p.getRegisterBy());
        vo.setRegisterTime(p.getRegisterTime());
        if (pc != null) {
            vo.setCode(pc.getCode());
            vo.setQrContent(pc.getQrContent());
            vo.setQrUrl(StringUtils.hasText(pc.getCode()) ? fileStorageService.getUrl("qr/" + pc.getCode() + ".png") : null);
        }
        return vo;
    }
}
