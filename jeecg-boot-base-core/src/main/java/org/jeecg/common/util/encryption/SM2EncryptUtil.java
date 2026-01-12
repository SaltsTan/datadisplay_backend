package org.jeecg.common.util.encryption;

import cn.hutool.crypto.asymmetric.SM2;
import org.bouncycastle.asn1.gm.GMNamedCurves;
import org.bouncycastle.asn1.x9.X9ECParameters;
import org.bouncycastle.crypto.InvalidCipherTextException;
import org.bouncycastle.crypto.params.ECDomainParameters;
import org.bouncycastle.crypto.params.ECPrivateKeyParameters;
import org.bouncycastle.crypto.params.ECPublicKeyParameters;
import org.bouncycastle.crypto.params.ParametersWithRandom;
import org.bouncycastle.math.ec.ECPoint;
import org.bouncycastle.util.encoders.Hex;

import java.io.UnsupportedEncodingException;
import java.math.BigInteger;
import java.security.SecureRandom;


/**
 * 国密sm2加解密
 */
public class SM2EncryptUtil {

    /**sm2曲线参数名称*/
    private static final String CRYPTO_NAME_SM2 = "sm2p256v1";

    private static final String PUBLIC_KEY = "04942DCDE6470E070599E029B0EA6BD2013CD0DA3ACB929239C4B8D82284B3155A07878BD76796AE2CA610C8CD1FB4455C63F8BDBD2F2DA0A0F5F1F43ED2D91C95";

    private static final String PRIVATE_KEY = "75037321CD865AF67C6BE4F0B3BC8E431E2B60A0DB7E27794F172C8CBAA237CD";

    /**
     * SM2加密算法
     * @param publicKey 公钥
     * @param data 待加密的数据
     * @return 密文，BC库产生的密文带由04标识符，与非BC库对接时需要去掉开头的04
     */
    public static String encrypt(String publicKey, String data) throws InvalidCipherTextException {
        // 按国密排序标准加密
        return encrypt(publicKey, data, StandardSM2Engine.CIPHERMODE_NORM);
    }

    /**
     * SM2加密算法 （设置密文排列方式）
     * @param publicKey 公钥
     * @param data 待加密的数据
     * @param cipherMode 密文排列方式0-C1C2C3；1-C1C3C2；
     * @return 密文，BC库产生的密文带由04标识符，与非BC库对接时需要去掉开头的04
     */
    public static String encrypt(String publicKey, String data, int cipherMode) throws InvalidCipherTextException {
        // 获取一条SM2曲线参数
        X9ECParameters sm2ECParameters = GMNamedCurves.getByName(CRYPTO_NAME_SM2);
        // 构造ECC算法参数，曲线方程、椭圆曲线G点、大整数N
        ECDomainParameters domainParameters = new ECDomainParameters(sm2ECParameters.getCurve(), sm2ECParameters.getG(), sm2ECParameters.getN());
        //提取公钥点
        ECPoint pukPoint = sm2ECParameters.getCurve().decodePoint(Hex.decode(publicKey));
        // 公钥前面的02或者03表示是压缩公钥，04表示未压缩公钥, 04的时候，可以去掉前面的04
        ECPublicKeyParameters publicKeyParameters = new ECPublicKeyParameters(pukPoint, domainParameters);

        StandardSM2Engine sm2Engine = new StandardSM2Engine();
        // 设置sm2为加密模式
        sm2Engine.init(true, cipherMode, new ParametersWithRandom(publicKeyParameters, new SecureRandom()));

        byte[] arrayOfBytes = null;
        try {
            byte[] in = data.getBytes();
            arrayOfBytes = sm2Engine.processBlock(in, 0, in.length);
        } catch (Exception e) {
            throw  e;
        }
        return Hex.toHexString(arrayOfBytes);
    }

    /**
     * sm2加密
     * @param data
     * @return
     * @throws InvalidCipherTextException
     */
    public static String encrypt(String data) throws InvalidCipherTextException{
        return encrypt(PUBLIC_KEY, data, StandardSM2Engine.CIPHERMODE_NORM);
    }

    /**
     * sm2解密
     * @param data
     * @return
     * @throws InvalidCipherTextException
     */
    public static String decrypt(String data) throws InvalidCipherTextException{
        return decrypt(PRIVATE_KEY, data, StandardSM2Engine.CIPHERMODE_NORM);
    }

    /**
     * SM2解密算法
     * @param privateKey    私钥
     * @param cipherData    密文数据
     * @description  密文数据以04开头，传入的密文前面没有04则补上
     * @return
     */
    public static String decrypt(String privateKey, String cipherData) throws InvalidCipherTextException {
        // // 按国密排序标准解密
        return decrypt(privateKey, cipherData, StandardSM2Engine.CIPHERMODE_NORM);
    }

    /**
     * SM2解密算法
     * @param privateKey    私钥
     * @param cipherData    密文数据
     * @param cipherMode 密文排列方式0-C1C2C3；1-C1C3C2；
     * @return
     */
    public static String decrypt(String privateKey, String cipherData, int cipherMode) throws InvalidCipherTextException {
        // 使用BC库加解密时密文以04开头，传入的密文前面没有04则补上
        if (!cipherData.startsWith("04")){
            cipherData = "04" + cipherData;
        }
        byte[] cipherDataByte = Hex.decode(cipherData);

        //获取一条SM2曲线参数
        X9ECParameters sm2ECParameters = GMNamedCurves.getByName(CRYPTO_NAME_SM2);
        //构造domain参数
        ECDomainParameters domainParameters = new ECDomainParameters(sm2ECParameters.getCurve(), sm2ECParameters.getG(), sm2ECParameters.getN());

        BigInteger privateKeyD = new BigInteger(privateKey, 16);
        ECPrivateKeyParameters privateKeyParameters = new ECPrivateKeyParameters(privateKeyD, domainParameters);

        StandardSM2Engine sm2Engine = new StandardSM2Engine();
        // 设置sm2为解密模式
        sm2Engine.init(false, cipherMode, privateKeyParameters);

        String result = "";
        try {
            byte[] arrayOfBytes = sm2Engine.processBlock(cipherDataByte, 0, cipherDataByte.length);
            return new String(arrayOfBytes);
        } catch (Exception e) {
            throw  e;
        }
    }

    /**
     * SM2 加签
     * @param privateKey 承建商私钥
     * @param id 授权码
     * @param source 16进制加密原文
     * @return 16进制签名
     */

    public static String sign(String privateKey,String id,  String source) throws UnsupportedEncodingException {
        SM2 sm2=  new SM2(privateKey,null);
        String id16= Hex.toHexString(id.getBytes("utf-8"));
        String source16=Hex.toHexString(source.getBytes("utf-8"));

        String sign= sm2.signHex(source16 ,id16);
        return sign;
    }

    /**
     * 校验签名
     * @param publicKey 民科提供公钥
     * @param data 16进制加密原文
     * @param signHex 16进制签名
     * @param id 授权码
     * @return
     */
    public static  boolean verifysign(String publicKey,String data, String signHex, String id) throws UnsupportedEncodingException {
        SM2 sm = new SM2(null,publicKey);
        String id16=Hex.toHexString(id.getBytes("utf-8"));
        String source16=Hex.toHexString(data.getBytes("utf-8"));
        boolean isSign= sm.verifyHex(data,signHex,id);
        return isSign;
    }


    public static void main(String[] args) {
        String s="123456789";
        try {
            String s1 = encrypt(s);
            System.out.println("加密："+s1);
            String s2 = decrypt(s1);
            System.out.println("解密："+s2);
        } catch (Exception e) {
            System.out.println("加密失败");

        }
    }

}
