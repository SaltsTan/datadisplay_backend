package org.jeecg.common.util.encryption;

import org.apache.shiro.codec.Base64;

import javax.crypto.Cipher;
import javax.crypto.spec.IvParameterSpec;
import javax.crypto.spec.SecretKeySpec;

/**
 * @Description: AES 加密
 * @author: jeecg-boot
 * @date: 2022/3/30 11:48
 */
public class AesEncryptUtil {

    /**
     * 使用AES-128-CBC加密模式 key和iv可以相同
     */
    private static String KEY = EncryptedString.key;
    private static String IV = EncryptedString.iv;

    /**
     * 加密方法
     * @param data  要加密的数据
     * @param key 加密key
     * @param iv 加密iv
     * @return 加密的结果
     * @throws Exception
     */
    public static String encrypt(String data, String key, String iv) throws Exception {
        try {
            //密钥base64解码
            byte[] keyBytes = key.getBytes();
            byte[] ivBytes = iv.getBytes();
            byte[] dataBytes = data.getBytes();
            SecretKeySpec secretKeySpec=new SecretKeySpec(keyBytes,"AES");
            IvParameterSpec ivSpec = new IvParameterSpec(ivBytes);
            //根据参数获取加密实例
            Cipher cipher=Cipher.getInstance("AES/CBC/PKCS5Padding");
            //初始化对象为加密模式
            cipher.init(Cipher.ENCRYPT_MODE,secretKeySpec,ivSpec);
            byte[] encrypted = cipher.doFinal(dataBytes);
            //加密后的密文进行base64编码
            return Base64.encodeToString(encrypted);
        } catch (Exception e) {
            e.printStackTrace();
            return null;
        }
    }

    /**
     * 解密方法
     * @param data 要解密的数据
     * @param key  解密key
     * @param iv 解密iv
     * @return 解密的结果
     * @throws Exception
     */
    public static String desEncrypt(String data, String key, String iv) throws Exception {
        byte[] encrypted1 = Base64.decode(data);
        //密钥base64解码
        byte[] keyBytes = key.getBytes();
        byte[] ivBytes = iv.getBytes();
        Cipher cipher = Cipher.getInstance("AES/CBC/PKCS5Padding");
        SecretKeySpec keyspec = new SecretKeySpec(keyBytes, "AES");
        IvParameterSpec ivspec = new IvParameterSpec(ivBytes);
        cipher.init(Cipher.DECRYPT_MODE, keyspec, ivspec);
        byte[] original = cipher.doFinal(encrypted1);
        String originalString = new String(original);
        return originalString;
    }

    /**
     * 使用默认的key和iv加密
     * @param data
     * @return
     * @throws Exception
     */
    public static String encrypt(String data) throws Exception {
        return encrypt(data, KEY, IV);
    }

    /**
     * 使用默认的key和iv解密
     * @param data
     * @return
     * @throws Exception
     */
    public static String desEncrypt(String data) throws Exception {
        return desEncrypt(data, KEY, IV);
    }


//    public static void main(String[] args) {
//        String s="123456789";
//        try {
//            String s1 = encrypt(s);
//            System.out.println("加密："+s1);
//            String s2 = desEncrypt(s1);
//            System.out.println("解密："+s2);
//        } catch (Exception e) {
//            System.out.println("加密失败");
//
//        }
//    }

}
