package org.jeecg.common.util;

/**
 * @author : cdh
 * @date : 2023/8/15
 */
public class ConverterUtil {

    public static byte[] intToByteArray(int source) {
        String hexString = String.format("%08X", source);
        return hexStringToByteArray(hexString);
    }

    public static int byteArrayToInt(byte[] source, int startIndex) {
        String value = byteArrayToHexString(source, startIndex, 4);
        return Integer.parseInt(value, 16);
    }

    public static String byteArrayToHexString(byte[] source, int startIndex, int len) {
        StringBuilder stringBuilder = new StringBuilder();
        for (int i = startIndex; i < startIndex + len; i++) {
            stringBuilder.append(String.format("%02X", source[i]));
        }
        return stringBuilder.toString();
    }

    public static byte[] hexStringToByteArray(String hexString) {
        if (hexString.length() % 2 != 0) {
            throw new IllegalArgumentException("hexString must have an even length");
        }
        int num = hexString.length() / 2;
        byte[] array = new byte[num];
        for (int i = 0; i < num; i++) {
            int num2 = i * 2;
            String value = hexString.substring(num2, num2 + 2);
            array[i] = (byte) Integer.parseInt(value, 16);
        }
        return array;
    }

    public static byte[] subByteArray(byte[] source, int startIndex, int len) {
        byte[] array = new byte[len];
        System.arraycopy(source, startIndex, array, 0, len);
        return array;
    }

    public static byte xor(byte[] value) {
        byte b = 0;
        for (byte aValue : value) {
            b ^= aValue;
        }
        return b;
    }

    public static byte[] combinByteArray(byte[] array1, byte[] array2) {
        if (array1 == null) {
            return array2;
        }
        if (array2 == null) {
            return array1;
        }
        byte[] array3 = new byte[array1.length + array2.length];
        System.arraycopy(array1, 0, array3, 0, array1.length);
        System.arraycopy(array2, 0, array3, array1.length, array2.length);
        return array3;
    }

    public static byte[] codingMessageHeader(byte[] source) {
        byte[] array = new byte[] { source[0] };
        for (int i = 1; i < source.length - 1; i++) {
            byte b = source[i];
            switch (b) {
                case 127:
                    array = combinByteArray(array, new byte[] { 126, (byte) (b ^ 0x20) });
                    break;
                case 126:
                    array = combinByteArray(array, new byte[] { 126, (byte) (b ^ 0x20) });
                    break;
                default:
                    array = combinByteArray(array, new byte[] { b });
                    break;
            }
        }
        return combinByteArray(array, new byte[] { source[source.length - 1] });
    }

    public static byte[] decodingMessageHeader(byte[] source) {
        byte[] array = null;
        for (int i = 0; i < source.length; i++) {
            if (source[i] == 126) {
                array = combinByteArray(array, new byte[] { (byte) (source[i + 1] ^ 0x20) });
                i++;
            } else {
                array = combinByteArray(array, new byte[] { source[i] });
            }
        }
        return array;
    }

    public static byte[] getBinaryMessageRequestHeader(int dataLen, byte[] mti) {
        byte[] array = new byte[] { 127, 66 };
        array = combinByteArray(array, mti);
        array = combinByteArray(array, intToByteArray(dataLen));
        return combinByteArray(array, new byte[] { 82, 0, 0, 0, 0, 0, 0, 127 });
    }

    public static byte[] getBinaryMessageResponseHeader(int dataLen, byte[] mti) {
        byte[] array = new byte[] { 127, 66 };
        array = combinByteArray(array, mti);
        array = combinByteArray(array, intToByteArray(dataLen));
        return combinByteArray(array, new byte[] { 65, 0, 0, 0, 0, 0, 0, 127 });
    }

    public static byte[] getJsonMessageRequestHeader(int dataLen) {
        byte[] array = new byte[] { 127, 74 };
        array = combinByteArray(array, new byte[] { 0, 0 });
        array = combinByteArray(array, intToByteArray(dataLen));
        return combinByteArray(array, new byte[] { 82, 0, 0, 0, 0, 0, 0, 127 });
    }

    public static byte[] getJsonMessageResponseHeader(int dataLen) {
        byte[] array = new byte[] { 127, 74 };
        array = combinByteArray(array, new byte[] { 0, 0 });
        array = combinByteArray(array, intToByteArray(dataLen));
        return combinByteArray(array, new byte[] { 65, 0, 0, 0, 0, 0, 0, 127 });
    }

    public static byte[] getJsonMessageRequestHeader(int dataLen, byte mti1, byte mti2) {
        byte[] array = new byte[] { 127, 74 };
        array = combinByteArray(array, new byte[] { mti1, mti2 });
        array = combinByteArray(array, intToByteArray(dataLen));
        return combinByteArray(array, new byte[] { 82, 0, 0, 0, 0, 0, 0, 127 });
    }

    public static byte[] getJsonMessageResponseHeader(int dataLen, byte mti1, byte mti2) {
        byte[] array = new byte[] { 127, 74 };
        array = combinByteArray(array, new byte[] { mti1, mti2 });
        array = combinByteArray(array, intToByteArray(dataLen));
        return combinByteArray(array, new byte[] { 65, 0, 0, 0, 0, 0, 0, 127 });
    }

    public static byte[] checkMessage(byte[] bytes) {
        if (bytes == null) {
            return null;
        }
        int num = 0;
        for (int i = 1; i < bytes.length; i++) {
            if (bytes[i] == 127) {
                num = i;
                break;
            }
        }
        if (num == 0) {
            return null;
        }
        byte[] array = new byte[num + 1];
        System.arraycopy(bytes, 0, array, 0, array.length);
        return array;
    }

    // Other methods and logic
}
