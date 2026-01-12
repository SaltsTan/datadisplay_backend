package org.jeecg.common.util;

import org.apache.commons.lang3.StringUtils;

import java.nio.charset.StandardCharsets;
import java.text.Normalizer;

public class CharacterConvert {

    /**
     * 全角转半角
     * @param input
     * @return
     */
    public static String convertFullwidthToHalfwidth(String input) {
        if(StringUtils.isEmpty(input)){
            return null;
        }
        // 使用Normalizer将全角字符转换为半角字符
        String normalized = Normalizer.normalize(input, Normalizer.Form.NFKC);
        return normalized;
    }

    /**
     * byte转换成int
     *
     * @param number
     * @return
     */
    public static int byteToInt(byte number) {
        return number & 0xff;
    }

    /**
     * short转成int
     *
     * @param number
     * @return
     */
    public static int shortToInt(short number) {
        return number & 0xff;
    }

    /**
     * int型数据，低八位获取
     *
     * @param data
     * @return
     */
    public static int getLow8(int number) {
        //转换成二进制的字符串形式
        //Integer.toBinaryString(number & 0xff)
        return number & 0xff;
    }

    /**
     * int型数据，取低四位
     *
     * @param number
     * @return
     */
    public static int getLow4(int number) {
        return number & 0x0f;
    }

    /**
     * bytes2HexString
     * 字节数组转16进制字符串
     *
     * @param b 字节数组
     * @return 16进制字符串
     */
    public static String bytes2HexString(byte[] b) {
        StringBuffer result = new StringBuffer();
        for (int i = 0; i < b.length; i++) {
            result.append(String.format("%02X",b[i]).toUpperCase());
        }
        return result.toString();
    }

    /**
     * string2HexString
     * 字符串转16进制字符串
     *
     * @param strPart 字符串
     * @return 16进制字符串
     */
    public static String string2HexString(String strPart) {
        StringBuffer hexString = new StringBuffer();
        for (int i = 0; i < strPart.length(); i++) {
            int ch = (int) strPart.charAt(i);
            String strHex = Integer.toHexString(ch);
            hexString.append(strHex);
        }
        return hexString.toString();
    }

    /**
     * @Title:string2HexString
     * @Description:字符串转16进制字符串
     * @param strPart 字符串
     * @param tochartype hex目标编码
     * @return 16进制字符串
     * @throws
     */
    public static String string2HexString(String strPart,String tochartype) {
        try{
            return bytes2HexString(strPart.getBytes(tochartype));
        }catch (Exception e){
            return "";
        }
    }


    /**
     * hexString2String
     * 16进制字符串转字符串
     *
     * @param src 16进制字符串
     * @return 字节数组
     * @throws
     */
    public static String hexString2String(String src) {
        StringBuffer str = new StringBuffer();
        for (int i = 0; i < src.length() / 2; i++) {
            String substring = src.substring(i * 2, i * 2 + 2);
            if(!"00".equals(substring)){
                str.append((char) Integer.valueOf(substring, 16).byteValue());
            }
        }
        return str.toString();
    }
    /**
     * @Title:hexUTF82String
     * @Description:16进制UTF-8字符串转字符串
     * @param src
     * 16进制字符串
     * @return 字节数组
     * @throws
     */
    public static String hexUTF82String(String src) {

        return hexString2String(src,"UTF-8","UTF-8");
    }

    /**
     * @Title:hexString2String
     * @Description:16进制字符串转字符串
     * @param src
     * 16进制字符串
     * @return 字节数组
     * @throws
     */
    public static String hexString2String(String src,String oldchartype, String chartype) {
        byte[] bts=hexString2Bytes(src);
        try {
            if (oldchartype.equals(chartype)) {
                return new String(bts, oldchartype);
            } else {
                return new String(new String(bts, oldchartype).getBytes(), chartype);
            }
        }catch (Exception e){

            return"";
        }
    }
    /**
     * char2Byte
     * 字符转成字节数据char-->integer-->byte
     *
     * @param src
     * @return
     * @throws
     */
    public static Byte char2Byte(Character src) {
        return Integer.valueOf((int) src).byteValue();
    }

    /**
     * intToHexString
     * 10进制数字转成16进制
     *
     * @param a   转化数据
     * @param len 占用字节数
     * @return
     * @throws
     */
    public static String intToHexString(int a, int len) {
        len <<= 1;
        String hexString = Integer.toHexString(a);
        int b = len - hexString.length();
        if (b > 0) {
            for (int i = 0; i < b; i++) {
                hexString = "0" + hexString;
            }
        }
        return hexString;
    }

    /**
     * 将16进制的2个字符串进行异或运算
     * http://blog.csdn.net/acrambler/article/details/45743157
     *
     * @param strHex_X
     * @param strHex_Y 注意：此方法是针对一个十六进制字符串一字节之间的异或运算，如对十五字节的十六进制字符串异或运算：1312f70f900168d900007df57b4884
     *                 先进行拆分：13 12 f7 0f 90 01 68 d9 00 00 7d f5 7b 48 84
     *                 13 xor 12-->1
     *                 1 xor f7-->f6
     *                 f6 xor 0f-->f9
     *                 ....
     *                 62 xor 84-->e6
     *                 即，得到的一字节校验码为：e6
     * @return
     */
    public static String xor(String strHex_X, String strHex_Y) {

        //将x、y转成二进制形式
        String anotherBinary = Integer.toBinaryString(Integer.valueOf(strHex_X, 16));

        String thisBinary = Integer.toBinaryString(Integer.valueOf(strHex_Y, 16));

        String result = "";

        //判断是否为8位二进制，否则左补零
        if (anotherBinary.length() != 8) {
            for (int i = anotherBinary.length(); i < 8; i++) {
                anotherBinary = "0" + anotherBinary;

            }
        }

        if (thisBinary.length() != 8) {
            for (int i = thisBinary.length(); i < 8; i++) {
                thisBinary = "0" + thisBinary;
            }
        }

        //异或运算
        for (int i = 0; i < anotherBinary.length(); i++) {
            //如果相同位置数相同，则补0，否则补1
            if (thisBinary.charAt(i) == anotherBinary.charAt(i)) {
                result += "0";
            } else {
                result += "1";
            }
        }
        return Integer.toHexString(Integer.parseInt(result, 2));
    }

    /**
     * byte转换成int型字符串
     * Convert byte[] to hex string
     *
     * @param src byte[] data
     * @return hex string
     */
    public static String bytes2Str(byte[] src) {
        StringBuilder stringBuilder = new StringBuilder();
        if (src == null || src.length <= 0) {
            return null;
        }
        for (int i = 0; i < src.length; i++) {
            int v = src[i] & 0xFF;
            String hv = Integer.toHexString(v);
            if (hv.length() < 2) {
                stringBuilder.append(0);
            }
            stringBuilder.append(hv);
        }
        return stringBuilder.toString();
    }

    /**
     * @param by
     * @return 接收字节数据并转为16进制字符串
     */
    public static String receiveHexToString(byte[] by) {
        try {
            String str = bytes2Str(by);
            str = str.toLowerCase();
            return str;
        } catch (Exception ex) {
            ex.printStackTrace();
        }
        return null;
    }

    /**
     * "7dd",4,'0'==>"07dd"
     *
     * @param input  需要补位的字符串
     * @param size   补位后的最终长度
     * @param symbol 按symol补充 如'0'
     * @return N_TimeCheck中用到了
     */
    public static String fill(String input, int size, char symbol) {
        while (input.length() < size) {
            input = symbol + input;
        }
        return input;
    }

    /**
     * integer数据求和
     *
     * @param integers
     * @return
     */
    public static int sum(Integer... integers) {
        Integer sum = 0;
        for (int i = 0; i < integers.length; i++) {
            sum = Integer.sum(sum, integers[i]);
        }
        return sum;
    }



    // 解析设备地址
    public static String getAddress(String address){
        String startStr = address.substring(0, 4);
        String beginStr = descOrder(startStr);
        String endStr = address.substring(address.length() - 5);
        int parseInt = Integer.parseInt(endStr);
        String toHexString = Integer.toHexString(parseInt);
        String lastStr = descOrder(toHexString);
        String addressStr = beginStr + lastStr;
        return addressStr;
    }

    //倒序
    public static String descOrder(String devNum) {
        String res = "";
        if (devNum == null || devNum.length() % 2 != 0) {
            return res;
        }
        char[] charArray = devNum.toCharArray();
        for (int i = charArray.length - 1; i >= 0; i = i - 2) {
            res += "" + charArray[i - 1] + charArray[i];
        }
        return res;
    }

    //电表倒序
    public static String electricityDescOrder(String devNum) {
        devNum = String.format("%012d", Long.parseLong(devNum));
        String res = "";
        char[] charArray = devNum.toCharArray();
        for (int i = charArray.length - 1; i >= 0; i = i - 2) {
            res += "" + charArray[i - 1] + charArray[i];
        }
        return res;
    }

    //16进制字符串转2进制
    public static String hexString2binaryString(String hexString) {
        if (hexString == null || hexString.length() % 2 != 0) {
            return null;
        }
        String bString = "", tmp;
        for (int i = 0; i < hexString.length(); i++) {
            tmp = "0000"
                    + Integer.toBinaryString(Integer.parseInt(hexString
                    .substring(i, i + 1), 16));
            bString += tmp.substring(tmp.length() - 4);
        }
        return bString;
    }
    public static String toString(byte[] b) {
        StringBuffer buffer = new StringBuffer();
        for (int i = 0; i < b.length; ++i) {
            buffer.append(b[i]);
        }
        return buffer.toString();
    }

    public static byte[] hexString2Bytes(String src) {
        if("00000000000000000000".equals(src)){
            return new byte[0];
        }

        int l = src.length() / 2;

        byte[] ret = new byte[l];

        for (int i = 0; i < l; i++) {
            ret[i] = (byte) Integer.valueOf(src.substring(i * 2, i * 2 + 2), 16).byteValue();
        }
        return ret;
    }

    /**
     * 计算校验位 ，返回十六进制校验位
     */
    public static String makeCheckSum(String data) {
        String newData = data.replaceAll(" ", "");
        int dSum = 0;
        int length = newData.length();
        int index = 0;
        // 遍历十六进制，并计算总和
        while (index < length) {
            String s = newData.substring(index, index + 2); // 截取2位字符
            dSum += Integer.parseInt(s, 16); // 十六进制转成十进制 , 并计算十进制的总和
            index = index + 2;
        }

        int mod = dSum % 256; // 用256取余，十六进制最大是FF，FF的十进制是255
        String checkSumHex = Integer.toHexString(mod); // 余数转成十六进制
        length = checkSumHex.length();
        if (length < 2) {
            checkSumHex = "0" + checkSumHex;  // 校验位不足两位的，在前面补0
        }
        return checkSumHex.toUpperCase();
    }

    public static String bytesToHexString(byte[] bArray) {
        StringBuffer sb = new StringBuffer(bArray.length);
        String sTemp;
        for (int i = 0; i < bArray.length; i++) {
            sTemp = Integer.toHexString(0xFF & bArray[i]);
            if (sTemp.length() < 2) {
                sb.append(0);
            }
            sb.append(sTemp.toUpperCase());
        }
        return sb.toString();
    }


    //大小端转换
    public static String bigEndChange(String str){
        byte[] bytes = str.getBytes(StandardCharsets.UTF_8);
        byte temporary;
        for (int i = 0; i < bytes.length/2; i+=2) {
            temporary=bytes[bytes.length-i-2];
            bytes[bytes.length-i-2]=bytes[i];
            bytes[i]=temporary;

            temporary=bytes[bytes.length-i-1];
            bytes[bytes.length-i-1]=bytes[i+1];
            bytes[i+1]=temporary;
        }
        return new String(bytes);
    }


}
