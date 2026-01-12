package org.jeecg.common.util;

import lombok.experimental.UtilityClass;
import lombok.extern.slf4j.Slf4j;

import javax.crypto.Cipher;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.util.Base64;

/**
 * AES 加密/解密工具类
 * 提供了对称加密算法 AES 的加密和解密功能，采用静态方法方便调用。
 * 密钥长度为 16 字节，使用 AES 算法。
 * 如果加密或解密失败，将返回原始值，并记录错误日志。
 * 使用 @UtilityClass 注解标记为工具类，不需要手动创建实例。
 * 使用 @Slf4j 进行日志记录。
 */
@Slf4j
@UtilityClass
public class AESUtil {

    // 加密算法名称
    private static final String ALGORITHM = "AES";

    // AES 密钥，必须为 16 字节（128 位），如果要使用更长的密钥（如 192 位或 256 位），需要更改配置和密钥管理方式
    private static final byte[] KEYS = "Hyl987^%$321f2cb".getBytes(StandardCharsets.UTF_8);

    /**
     * 使用 AES 算法加密明文字符串。
     *
     * @param plainText 需要加密的明文字符串
     * @return 加密后的字符串，使用 Base64 编码。如果加密失败，返回原始值。
     */
    public String encrypt(String plainText) {
        try {
            // 创建 AES 密钥规格
            SecretKeySpec secretKey = new SecretKeySpec(KEYS, ALGORITHM);
            // 创建并初始化 Cipher 对象，用于加密操作
            Cipher cipher = Cipher.getInstance(ALGORITHM);
            cipher.init(Cipher.ENCRYPT_MODE, secretKey);
            // 执行加密操作
            byte[] encrypted = cipher.doFinal(plainText.getBytes());
            // 将加密后的字节数组转换为 Base64 编码的字符串并返回
            return Base64.getEncoder().encodeToString(encrypted);
        } catch (Exception e) {
            // 加密失败时记录错误日志，并返回原始的明文字符串
            log.error("加密失败，使用原始值，原值为：{}", plainText);
            return plainText;
        }
    }

    /**
     * 使用 AES 算法解密密文字符串。
     *
     * @param cipherText 需要解密的密文字符串（Base64 编码）
     * @return 解密后的明文字符串。如果解密失败，返回原始值。
     */
    public String decrypt(String cipherText) {
        try {
            // 创建 AES 密钥规格
            SecretKeySpec secretKey = new SecretKeySpec(KEYS, ALGORITHM);
            // 创建并初始化 Cipher 对象，用于解密操作
            Cipher cipher = Cipher.getInstance(ALGORITHM);
            cipher.init(Cipher.DECRYPT_MODE, secretKey);
            // 先将 Base64 编码的密文解码为字节数组
            byte[] decodedBytes = Base64.getDecoder().decode(cipherText);
            // 执行解密操作，将字节数组转换为明文字符串并返回
            byte[] decrypted = cipher.doFinal(decodedBytes);
            return new String(decrypted);
        } catch (Exception e) {
            // 解密失败时记录错误日志，并返回原始的密文字符串
            log.error("解密失败，使用原始值，原值为：{}", cipherText);
            return cipherText;
        }
    }
}

