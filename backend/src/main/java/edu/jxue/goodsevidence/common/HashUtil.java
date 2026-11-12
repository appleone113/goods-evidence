package edu.jxue.goodsevidence.common;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;

/**
 * 哈希工具：生成业务数据摘要（32 字节 / 64 hex），用于上链存证。
 * 注：生产环境建议与链一致的 keccak256（FISCO SDK 提供 CryptoSuite），
 * 此处用 SHA-256 便于演示，只要存证与验真用同一算法即可保持一致性校验。
 */
public class HashUtil {

    public static String sha256(String input) {
        try {
            MessageDigest md = MessageDigest.getInstance("SHA-256");
            byte[] bytes = md.digest(input.getBytes(StandardCharsets.UTF_8));
            StringBuilder sb = new StringBuilder();
            for (byte b : bytes) {
                sb.append(String.format("%02x", b));
            }
            return sb.toString();
        } catch (Exception e) {
            throw new RuntimeException("哈希计算失败", e);
        }
    }
}
