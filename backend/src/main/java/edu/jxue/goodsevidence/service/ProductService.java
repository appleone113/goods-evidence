package edu.jxue.goodsevidence.service;

import edu.jxue.goodsevidence.dto.ProductRegisterDTO;
import edu.jxue.goodsevidence.vo.ProductVO;

import java.util.List;

/**
 * 商品业务接口
 */
public interface ProductService {

    /**
     * 登记商品并生成"一物一码"
     * @return 商品详情（含一物一码编码与二维码地址），便于登记页直接展示二维码
     */
    ProductVO register(ProductRegisterDTO dto) throws Exception;

    /**
     * 商品列表（含一物一码），按登记时间倒序
     */
    List<ProductVO> listProducts();

    /**
     * 商品详情（含一物一码）
     */
    ProductVO detail(Long productId);

    /**
     * 按当前 app.public-base-url 批量重刷历史二维码：
     * 一物一码编码不变，只重写二维码图片内容（URL 域名）与 product_code.qr_content。
     * 用于换 WiFi / 换 IP 后免重新登记商品。
     *
     * @return 成功刷新的二维码数量
     */
    int refreshAllQrCodes() throws Exception;
}
