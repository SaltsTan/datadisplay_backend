package org.jeecg.common.util;

import cn.hutool.core.util.ObjectUtil;
import com.aliyun.oss.ServiceException;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.web.client.RestTemplate;
import sun.misc.BASE64Decoder;
import sun.misc.BASE64Encoder;

import javax.crypto.Cipher;
import javax.net.ssl.*;
import java.io.*;
import java.lang.reflect.Field;
import java.net.HttpURLConnection;
import java.net.URI;
import java.net.URISyntaxException;
import java.net.URL;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.nio.file.StandardCopyOption;
import java.security.KeyFactory;
import java.security.cert.X509Certificate;
import java.security.interfaces.RSAPublicKey;
import java.security.spec.X509EncodedKeySpec;
import java.util.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * @author cdh
 */
@Slf4j
public class BusinessHelpUtil {

    /**
     * 根据实体类属性值拼接请求参数
     * @param <T> 实体类泛型
     * @param obj 实体类对象
     * @return 拼接后的请求参数字符串
     */
    public static  <T> String buildRequestParam(T obj) {
        if (obj == null) {
            return "";
        }
        List<String> params = new ArrayList<>();
        Class<?> clazz = obj.getClass();
        // 获取所有声明的字段（包括私有字段）
        Field[] fields = clazz.getDeclaredFields();
        for (Field field : fields) {
            try {
                // 设置字段可访问
                field.setAccessible(true);
                Object value = field.get(obj);
                // 只处理非空值
                if (value != null && !"".equals(value.toString())) {
                    params.add(field.getName() + "=" + value.toString());
                }
            } catch (Exception e) {
                // 忽略访问异常
            }
        }
        return String.join("&", params);
    }


    /**
     * 网络图片资源转文件
     * @param imageUrl 网络图片地址
     * @param targetFilePath 文件保存地址
     * @throws IOException
     */
    public static void urlToFile(String imageUrl, String targetFilePath) {
        try {
            URL url = new URL(imageUrl);
            Files.copy(url.openStream(), Paths.get(targetFilePath), StandardCopyOption.REPLACE_EXISTING);
        } catch (IOException e) {
            throw new ServiceException("");
        }
    }


    /**
     * 查找字符串中指定字符有多少个
     *
     * @param oriStr
     * @param findStr
     * @param count
     * @return
     */
    public static int findStrCount(String oriStr, String findStr, int count) {
        if (oriStr.contains(findStr)) {
            count++;
            count = findStrCount(oriStr.substring(oriStr.indexOf(findStr) + findStr.length()), findStr, count);
        }
        return count;
    }
//    /**
//     * 网络图片资源转Base64
//     */
//    public static String photoToBase64(String photoUrl) {
//        String encode = null;
//        URI uri = URI.create(photoUrl);
//        try (InputStream inputStream = uri.toURL().openStream()) {
//            String fileName = photoUrl.substring(photoUrl.lastIndexOf("/") + 1);
//            File file = new File(fileName);
//            copyInputStreamToFile(inputStream, file);
//            encode = cn.hutool.core.codec.Base64.encode(file);
//        } catch (Exception e) {
//            log.error("下载网络图片出错" + e.getMessage(), e);
//        } finally {
//            return encode;
//        }
//    }

    public static void inputStreamToFile(InputStream ins, File file) {
        try {
            OutputStream os = new FileOutputStream(file);
            int bytesRead = 0;
            byte[] buffer = new byte[8192];
            while ((bytesRead = ins.read(buffer, 0, 8192)) != -1) {
                os.write(buffer, 0, bytesRead);
            }
            os.close();
            ins.close();
        } catch (Exception e) {
            throw new RuntimeException("读取文件错误", e);
        }
    }
    /**
     * 获取网络图片的inputStream
     * @param imgUrl
     * @return
     */
    public static InputStream picUrlToInputStream(String imgUrl) {
        RestTemplate restTemplate = RestUtil.getRestTemplate();
        // 使用 RestTemplate 获取字节数组
        ResponseEntity<byte[]> response = restTemplate.getForEntity(imgUrl, byte[].class);

        // 检查响应状态是否正常
        if (response.getStatusCode().is2xxSuccessful() && response.getBody() != null) {
            // 将字节数组转换为 InputStream
            return new ByteArrayInputStream(response.getBody());
        }
        return null;
    }

    public static void main(String[] args) throws IOException {
        String url="https://192.168.18.201/evo-pic/f0c17ab7-49a4-11ef-b8a1-e8611a0bc8a9/20240821/1/18797b3e-5f92-11ef-8f44-e8611a0bc8a9.jpg?token=2:ME97gAS6Gyec6KsAHrXE4s3mYb50Ng5j&oss_addr=192.168.18.201:9876";
        InputStream inputStream = picUrlToInputStream(url);

        System.out.println(inputStream.available());
    }

    public static void trustAllCertificates() {
        // Create a trust manager that does not validate certificate chains
        TrustManager[] trustAllCerts = new TrustManager[]{
                new X509TrustManager() {
                    @Override
                    public X509Certificate[] getAcceptedIssuers() {
                        return new X509Certificate[0];
                    }
                    @Override
                    public void checkClientTrusted(X509Certificate[] certs, String authType) {
                    }
                    @Override
                    public void checkServerTrusted(X509Certificate[] certs, String authType) {
                    }
                }
        };

        // Install the all-trusting trust manager
        try {
            SSLContext sc = SSLContext.getInstance("SSL");
            sc.init(null, trustAllCerts, new java.security.SecureRandom());
            HttpsURLConnection.setDefaultSSLSocketFactory(sc.getSocketFactory());
        } catch (Exception e) {
            throw new RuntimeException("不安全的https连接");
        }
        // Create all-trusting host name verifier
        HostnameVerifier allHostsValid = new HostnameVerifier() {
            @Override
            public boolean verify(String hostname, SSLSession session) {
                return true;
            }
        };

        // Install the all-trusting host verifier
        HttpsURLConnection.setDefaultHostnameVerifier(allHostsValid);

    }
    /**
     * 图片URL转Base64编码
     * @param imgUrl 图片URL
     * @return Base64编码
     */
    public static String photoToBase64(String imgUrl) {
        URL url = null;
        InputStream is = null;
        ByteArrayOutputStream outStream = null;
        HttpURLConnection httpUrl = null;

        try {
            url = new URL(imgUrl);
            httpUrl = (HttpURLConnection) url.openConnection();
            httpUrl.connect();
            httpUrl.getInputStream();

            is = httpUrl.getInputStream();
            outStream = new ByteArrayOutputStream();

            //创建一个Buffer字符串
            byte[] buffer = new byte[1024];
            //每次读取的字符串长度，如果为-1，代表全部读取完毕
            int len = 0;
            //使用输入流从buffer里把数据读取出来
            while( (len = is.read(buffer)) != -1 ){
                //用输出流往buffer里写入数据，中间参数代表从哪个位置开始读，len代表读取的长度
                outStream.write(buffer, 0, len);
            }
            // 对字节数组Base64编码
            return encode(outStream.toByteArray());
        } catch (Exception e) {
            e.printStackTrace();
        } finally {
            try {
                if(is != null) {
                    is.close();
                }
                if(outStream != null) {
                    outStream.close();
                }
                if(httpUrl != null) {
                    httpUrl.disconnect();
                }
            } catch (Exception e) {
                e.printStackTrace();
            }
        }

        return null;
    }


    /**
     * 图片转字符串
     * @param image 图片Buffer
     * @return Base64编码
     */
    public static String encode(byte[] image){
        BASE64Encoder decoder = new BASE64Encoder();
        return replaceEnter(decoder.encode(image));
    }

    /**
     * 字符替换
     * @param str 字符串
     * @return 替换后的字符串
     */
    public static String replaceEnter(String str){
        String reg ="[\n-\r]";
        Pattern p = Pattern.compile(reg);
        Matcher m = p.matcher(str);
        return m.replaceAll("");
    }
    /**
     * 将网络图片转为字节数组
     *
     * @param strUrl
     * @return
     */
    public static byte[] getImgByteArray(String strUrl) {
        if (strUrl == null) {
            return null;
        }
        ByteArrayOutputStream baos = null;
        try {
            HttpURLConnection httpUrl = null;
            BufferedInputStream bis = null;
            URL url = new URL(strUrl);
            httpUrl = (HttpURLConnection) url.openConnection();
            httpUrl.connect();

            bis = new BufferedInputStream(httpUrl.getInputStream());
            baos = new ByteArrayOutputStream();
            byte[] buf = new byte[1024];
            int size = 0;
            while ((size = bis.read(buf)) != -1) {
                baos.write(buf, 0, size);
            }
            baos.flush();
            return baos.toByteArray();
        } catch (Exception e) {
            log.error(e.toString());
        } finally {
            if (baos != null) {
                try {
                    baos.close();
                } catch (IOException e) {
                }
            }
        }
        return null;
    }

    /**
     * 将inputstream转为Base64
     *
     * @param is
     * @return
     * @throws Exception
     */
    public static String inputStream2Base64(InputStream is) throws Exception {
        byte[] data = null;
        try {
            ByteArrayOutputStream swapStream = new ByteArrayOutputStream();
            byte[] buff = new byte[100];
            int rc = 0;
            while ((rc = is.read(buff, 0, 100)) > 0) {
                swapStream.write(buff, 0, rc);
            }
            data = swapStream.toByteArray();
        } catch (IOException e) {
            e.printStackTrace();
        } finally {
            if (is != null) {
                try {
                    is.close();
                } catch (IOException e) {
                    throw new Exception("输入流关闭异常");
                }
            }
        }

        return Base64.getEncoder().encodeToString(data);
    }

    /**
     * base64字符串转为流
     * @param base64
     * @return
     */
    public static InputStream base64ToInputStream(String base64){
        BASE64Decoder decoder = new BASE64Decoder();
        //解码
        base64 = base64.replaceAll(" ", "+");
        try {
            byte[] buffer = decoder.decodeBuffer(base64.replace("data:image/png;base64", "")
                    .replace("data:image/jpeg;base64", "")
                    .replace("data:image/jpeg;base64", ""));
            for(int i = 0;i<buffer.length;i++){
                if(buffer[i] < 0){
                    buffer[i] += 256;
                }
            }
            //生成流
            ByteArrayInputStream stream = new ByteArrayInputStream(buffer);
            return stream;
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }

    /**
     * 把列表转化为树结构
     * @param entityList
     * @param parentFieldName 父节点关联字段
     * @return
     * @param <T>
     */
    public static  <T> List<Map<String, Object>> listToTree(List<T> entityList, String parentFieldName,String fieldName){
        //返回的map Tree树形结构
        List<Map<String, Object>> treeMap = new ArrayList<>();
        //将传进的参数entityList转为MapList
        List<Map<String, Object>> listMap =new ArrayList<>();
        entityList.forEach(i->{
            Map<String, Object> objectMap = null;
            try {
                objectMap = objectToMap(i);
            } catch (IllegalAccessException e) {
                return;
            }
            listMap.add(objectMap);
        });


        //声明一个map用来存listMap中的对象，key为对象id，value为对象本身
        Map<String, Map<String, Object>> entityMap = new Hashtable<>();
        //循环listMap把map对象put到entityMap中去
        listMap.forEach(map -> entityMap.put(ObjectUtil.isEmpty(map.get(fieldName))?"":map.get(fieldName).toString(), map));
        //循环listMap进行Tree树形结构组装
        listMap.forEach(map -> {
            //获取map的pid
            String parentFieldValue = String.valueOf(map.get(parentFieldName));
            if (parentFieldValue == null||"0".equals(parentFieldValue)){ //判断pid是否为空，为空说明是最顶级，直接add到返回的treeMap中去
                treeMap.add(map);
            } else { //如果pid不为空
                //根据当前map的pid获取上级 parentMap
                Map<String, Object> parentMap = entityMap.get(parentFieldValue);
                if (parentMap == null){ //如果parentMap为空，则说明当前map没有父级，当前map就是顶级
                    treeMap.add(map);
                } else { //如果parentMap不为空，则当前map为parentMap的子级
                    //取出parentMap的所有子级的List集合
                    List<Map<String, Object>> children = (List<Map<String, Object>>)parentMap.get("children");
                    Object o = parentMap.get("mapServiceUrl");
                    if (children == null){ //判断子级集合是否为空，为空则新创建List
                        children = new ArrayList<>();
                        parentMap.put("children", children);
                    }
                    //设备封装父区域的mapServiceUrl
                    if(ObjectUtil.isNotEmpty(o)){
                        map.put("parentMapServiceUrl",o);
                    }
                    //把当前map对象add到parentMap的子级List中去
                    children.add(map);
                }
            }
        });
        return treeMap;
    }
    public static Map<String, Object> objectToMap(Object obj) throws IllegalAccessException {
        if (obj == null) {
            return null;
        }
        Map<String, Object> map = new HashMap<>();
        Class<?> aClass = obj.getClass();
        Field[] declaredFields = aClass.getDeclaredFields();
        Class<?> superclass = aClass.getSuperclass();
        Field[] superclassDeclaredFields = superclass.getDeclaredFields();
        List<Field> list = new ArrayList<>();
        list.addAll(Arrays.asList(declaredFields));
        list.addAll(Arrays.asList(superclassDeclaredFields));
        for (Field field : list) {
            field.setAccessible(true);
            map.put(field.getName(), field.get(obj));
        }
        return map;
    }


    /**
     * 处理ivs图片地址
     * @param url
     * @return
     */
    public static String decodeUrl(String url){
        URI uri = null;
        try {
            uri = new URI(url.replace("&amp;", "&"));
        } catch (URISyntaxException e) {
            throw new RuntimeException(e);
        }
        String host = uri.getHost() + ":" + uri.getPort();
        String rawPath = uri.getRawPath();
        String rawQuery = uri.getRawQuery();
        String query;
        if (rawQuery != null && rawQuery.contains("&auth_type")) {
            String[] queries = rawQuery.split("&auth_type");
            query = queries[0];
//            String pictureID = Arrays.stream(query.split("&")).filter(i -> {
//                return i.contains("PictureID");
//            }).findFirst().get();
//            pictureId.append(pictureID.replace("PictureID=",""));
        } else {
            query = rawQuery;
        }
        return "https://" + host + rawPath + "?" + query;
    }


    /**
     * RSA公钥加密
     *
     * @param publicKey 公钥
     * @param password 待加密的密码
     * @return 密文
     */
    public static String RSAencrypt(String publicKey,String password){
        try {
            byte[] decoded = Base64.getDecoder().decode(publicKey.getBytes("UTF-8"));
            RSAPublicKey pubKey =
                    (RSAPublicKey)
                            KeyFactory.getInstance("RSA").generatePublic(new X509EncodedKeySpec(decoded));
            // RSA加密
            Cipher cipher = Cipher.getInstance("RSA");
            cipher.init(Cipher.ENCRYPT_MODE, pubKey);
            //**此处Base64编码，开发者可以使用自己的库**
            String outStr = Base64.getEncoder().encodeToString(cipher.doFinal(password.getBytes("UTF-8")));
            //outStr 是加密密文
            System.out.println(outStr);
            return outStr;
        } catch (Exception e) {
            e.printStackTrace();
        }
        return null;
    }


    // 将文件转写为base64字符串
    public static String convertImageToBase64Data(String filePath) throws IOException {
        byte[] b = Files.readAllBytes(Paths.get(filePath));
        return Base64.getEncoder().encodeToString(b);
    }

    //判断图片base64字符串的文件格式
    public static String checkImageBase64Format(String base64ImgData) {
        byte[] b = Base64.getDecoder().decode(base64ImgData);
        String type = "";
        if (0x424D == ((b[0] & 0xff) << 8 | (b[1] & 0xff))) {
            type = "bmp";
        } else if (0x8950 == ((b[0] & 0xff) << 8 | (b[1] & 0xff))) {
            type = "png";
        } else if (0xFFD8 == ((b[0] & 0xff) << 8 | (b[1] & 0xff))) {
            type = "jpg";
        }
        return type;
    }

}
