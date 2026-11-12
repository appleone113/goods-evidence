package edu.jxue.goodsevidence;

import edu.jxue.goodsevidence.service.QrCodeService;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 二维码生成单元测试：保证"一物一码"能产出合法 Base64 PNG（模块二/九）
 */
class QrCodeServiceTest {

    private final QrCodeService qrCodeService = new QrCodeService();

    @Test
    @DisplayName("生成非空 Base64 PNG 字符串")
    void generateBase64() throws Exception {
        String b64 = qrCodeService.generateBase64("P123", 200);
        assertNotNull(b64);
        assertTrue(b64.length() > 0);
        // 标准 PNG 头经 Base64 编码后以 iVBOR 开头
        assertTrue(b64.startsWith("iVBOR"));
    }
}
