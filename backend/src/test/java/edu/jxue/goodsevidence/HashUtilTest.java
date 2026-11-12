package edu.jxue.goodsevidence;

import edu.jxue.goodsevidence.common.HashUtil;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;

/**
 * 哈希工具单元测试：保证存证/验真两端摘要算法一致（模块九）
 */
class HashUtilTest {

    @Test
    @DisplayName("相同输入产生相同摘要（一致性）")
    void sameInputSameHash() {
        String a = HashUtil.sha256("商品1|TRADE|甲|乙|100");
        String b = HashUtil.sha256("商品1|TRADE|甲|乙|100");
        assertEquals(a, b);
    }

    @Test
    @DisplayName("不同输入产生不同摘要（抗碰撞）")
    void differentInputDifferentHash() {
        String a = HashUtil.sha256("商品1|TRADE|甲|乙|100");
        String b = HashUtil.sha256("商品1|TRADE|甲|乙|101");
        assertNotEquals(a, b);
    }

    @Test
    @DisplayName("摘要为 64 位 hex 字符串（SHA-256）")
    void hashLength() {
        String h = HashUtil.sha256("test");
        assertEquals(64, h.length());
    }
}
