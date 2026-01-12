package org.jeecg.common.util;

import com.google.common.collect.HashMultimap;
import io.minio.*;
import io.minio.http.Method;
import io.minio.messages.Part;
import io.minio.messages.VersioningConfiguration;
import lombok.extern.slf4j.Slf4j;
import org.jeecg.common.constant.SymbolConstant;
import org.jeecg.common.util.filter.SsrfFileTypeFilter;
import org.jeecg.common.util.filter.StrAttackFilter;
import org.springframework.web.multipart.MultipartFile;

import java.io.InputStream;
import java.net.URLDecoder;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;

/**
 * minio文件上传工具类
 * @author: jeecg-boot
 */
@Slf4j
public class MinioUtil {

    private static String minioUrl;
    private static String minioName;
    private static String minioPass;
    private static String bucketName;

    public static void setMinioUrl(String minioUrl) {
        MinioUtil.minioUrl = minioUrl;
    }

    public static void setMinioName(String minioName) {
        MinioUtil.minioName = minioName;
    }

    public static void setMinioPass(String minioPass) {
        MinioUtil.minioPass = minioPass;
    }

    public static void setBucketName(String bucketName) {
        MinioUtil.bucketName = bucketName;
    }

    public static String getMinioUrl() {
        return minioUrl;
    }

    public static String getBucketName() {
        return bucketName;
    }

    private static MinioClient minioClient = null;

    private static CustomMinioClient minioClientMulty = null;

    /**
     * 上传文件
     * @param file
     * @return
     */
    public static String upload(MultipartFile file, String bizPath, String customBucket) throws Exception {
        String fileUrl = "";
        //update-begin-author:wangshuai date:20201012 for: 过滤上传文件夹名特殊字符，防止攻击
        bizPath = StrAttackFilter.filter(bizPath);
        //update-end-author:wangshuai date:20201012 for: 过滤上传文件夹名特殊字符，防止攻击

        //update-begin-author:liusq date:20210809 for: 过滤上传文件类型
        SsrfFileTypeFilter.checkUploadFileType(file);
        //update-end-author:liusq date:20210809 for: 过滤上传文件类型

        String newBucket = bucketName;
        if(oConvertUtils.isNotEmpty(customBucket)){
            newBucket = customBucket;
        }
        try {
            initMinio(minioUrl, minioName,minioPass);
            // 检查存储桶是否已经存在
            if(minioClient.bucketExists(BucketExistsArgs.builder().bucket(newBucket).build())) {
                log.info("Bucket already exists.");
            } else {
                // 创建一个名为ota的存储桶
                minioClient.makeBucket(MakeBucketArgs.builder().bucket(newBucket).build());
                log.info("create a new bucket.");
            }
            InputStream stream = file.getInputStream();
            // 获取文件名
            String orgName = file.getOriginalFilename();
            if("".equals(orgName)){
                orgName=file.getName();
            }
            orgName = CommonUtils.getFileName(orgName);
            String objectName = bizPath+"/"
                                +( orgName.indexOf(".")==-1
                                   ?orgName + "_" + System.currentTimeMillis()
                                   :orgName.substring(0, orgName.lastIndexOf(".")) + "_" + System.currentTimeMillis() + orgName.substring(orgName.lastIndexOf("."))
                                 );

            // 使用putObject上传一个本地文件到存储桶中。
            if(objectName.startsWith(SymbolConstant.SINGLE_SLASH)){
                objectName = objectName.substring(1);
            }
            PutObjectArgs objectArgs = PutObjectArgs.builder().object(objectName)
                    .bucket(newBucket)
                    .contentType("application/octet-stream")
                    .stream(stream,stream.available(),-1).build();
            minioClient.putObject(objectArgs);
            stream.close();
            fileUrl = minioUrl+newBucket+"/"+objectName;
        }catch (Exception e){
            log.error(e.getMessage(), e);
        }
        return fileUrl;
    }

    /**
     * 上传版本文件
     * @param file
     * @return
     */
    public static String uploadVersionFile(MultipartFile file, String bizPath) throws Exception {
        String fileUrl = "";
        //update-begin-author:wangshuai date:20201012 for: 过滤上传文件夹名特殊字符，防止攻击
        bizPath = StrAttackFilter.filter(bizPath);
        //update-end-author:wangshuai date:20201012 for: 过滤上传文件夹名特殊字符，防止攻击

        //update-begin-author:liusq date:20210809 for: 过滤上传文件类型
        SsrfFileTypeFilter.checkUploadFileType(file);
        //update-end-author:liusq date:20210809 for: 过滤上传文件类型

        String newBucket = bucketName;

        try {
            initMinio(minioUrl, minioName,minioPass);
            // 检查存储桶是否已经存在
            if(minioClient.bucketExists(BucketExistsArgs.builder().bucket(newBucket).build())) {
                log.info("Bucket already exists.");
            } else {
                // 创建一个名为ota的存储桶
                minioClient.makeBucket(MakeBucketArgs.builder().bucket(newBucket).build());
                log.info("create a new bucket.");
            }
            InputStream stream = file.getInputStream();
            // 获取文件名
            String orgName = file.getOriginalFilename();
            if("".equals(orgName)){
                orgName=file.getName();
            }
            orgName = CommonUtils.getFileName(orgName);
            String objectName = bizPath+"/"
                    +( orgName.indexOf(".")==-1
                    ?orgName
                    :orgName.substring(0, orgName.lastIndexOf(".")) + orgName.substring(orgName.lastIndexOf("."))
            );

            // 使用putObject上传一个本地文件到存储桶中。
            if(objectName.startsWith(SymbolConstant.SINGLE_SLASH)){
                objectName = objectName.substring(1);
            }
            PutObjectArgs objectArgs = PutObjectArgs.builder().object(objectName)
                    .bucket(newBucket)
                    .contentType("application/octet-stream")
                    .stream(stream,stream.available(),-1).build();
            minioClient.putObject(objectArgs);
            stream.close();
            fileUrl = minioUrl+newBucket+"/"+objectName;
        }catch (Exception e){
            log.error(e.getMessage(), e);
        }
        return fileUrl;
    }


    /**
     * 文件上传
     * @param file
     * @param bizPath
     * @return
     */
    public static String upload(MultipartFile file, String bizPath) throws Exception {
        return upload(file,bizPath,null);
    }

    /**
     * 获取文件流
     * @param bucketName
     * @param objectName
     * @return
     */
    public static InputStream getMinioFile(String bucketName,String objectName){
        InputStream inputStream = null;
        try {
            initMinio(minioUrl, minioName, minioPass);
            GetObjectArgs objectArgs = GetObjectArgs.builder().object(objectName)
                    .bucket(bucketName).build();
            inputStream = minioClient.getObject(objectArgs);
        } catch (Exception e) {
            log.info("文件获取失败" + e.getMessage());
        }
        return inputStream;
    }

    /**
     * 删除文件
     * @param bucketName
     * @param objectName
     * @throws Exception
     */
    public static void removeObject(String bucketName, String objectName) {
        try {
            initMinio(minioUrl, minioName,minioPass);
            RemoveObjectArgs objectArgs = RemoveObjectArgs.builder().object(objectName)
                    .bucket(bucketName).build();
            minioClient.removeObject(objectArgs);
        }catch (Exception e){
            log.info("文件删除失败" + e.getMessage());
        }
    }

    /**
     * 获取文件外链
     * @param bucketName
     * @param objectName
     * @param expires
     * @return
     */
    public static String getObjectUrl(String bucketName, String objectName, Integer expires) {
        initMinio(minioUrl, minioName,minioPass);
        try{
            //update-begin---author:liusq  Date:20220121  for：获取文件外链报错提示method不能为空，导致文件下载和预览失败----
            GetPresignedObjectUrlArgs objectArgs = GetPresignedObjectUrlArgs.builder().object(objectName)
                    .bucket(bucketName)
                    .expiry(expires).method(Method.GET).build();
            //update-begin---author:liusq  Date:20220121  for：获取文件外链报错提示method不能为空，导致文件下载和预览失败----
            String url = minioClient.getPresignedObjectUrl(objectArgs);
            return URLDecoder.decode(url,"UTF-8");
        }catch (Exception e){
            log.info("文件路径获取失败" + e.getMessage());
        }
        return null;
    }

    /**
     * 初始化客户端
     * @param minioUrl
     * @param minioName
     * @param minioPass
     * @return
     */
    private static MinioClient initMinio(String minioUrl, String minioName,String minioPass) {
        if (minioClient == null) {
            try {
                minioClient = MinioClient.builder()
                        .endpoint(minioUrl)
                        .credentials(minioName, minioPass)
                        .build();
            } catch (Exception e) {
                e.printStackTrace();
            }
        }
        return minioClient;
    }

    /**
     * 上传文件到minio
     * @param stream
     * @param relativePath
     * @return
     */
    public static String upload(InputStream stream,String relativePath) throws Exception {
        initMinio(minioUrl, minioName,minioPass);
        if(minioClient.bucketExists(BucketExistsArgs.builder().bucket(bucketName).build())) {
            log.info("Bucket already exists.");
        } else {
            // 创建一个名为ota的存储桶
            minioClient.makeBucket(MakeBucketArgs.builder().bucket(bucketName).build());
            log.info("create a new bucket.");
        }
        PutObjectArgs objectArgs = PutObjectArgs.builder().object(relativePath)
                .bucket(bucketName)
                .contentType("application/octet-stream")
                .stream(stream,stream.available(),-1).build();
        minioClient.putObject(objectArgs);
        stream.close();
        return minioUrl+bucketName+"/"+relativePath;
    }

    private static CustomMinioClient initMinioMultipart(String minioUrl, String minioName,String minioPass) {
        if (minioClientMulty == null) {
            try {
                minioClientMulty = new CustomMinioClient(MinioAsyncClient.builder()
                        .endpoint(minioUrl)
                        .credentials(minioName, minioPass)
                        .build());
            } catch (Exception e) {
                e.printStackTrace();
            }
        }
        return minioClientMulty;
    }

    /**
     * 单文件签名上传
     *
     * @param objectName 文件全路径名称
     * @return /
     */
    public static String getUploadObjectUrl(String objectName) {
        // 上传文件时携带content-type头即可
        /*if (StrUtil.isBlank(contentType)) {
            contentType = "application/octet-stream";
        }
        HashMultimap<String, String> headers = HashMultimap.create();
        headers.put("Content-Type", contentType);*/
        try {
            initMinioMultipart(minioUrl,minioName,minioPass);
            String presignedObjectUrl = minioClientMulty.getPresignedObjectUrl(
                    GetPresignedObjectUrlArgs.builder()
                            .method(Method.PUT)
                            .bucket(bucketName)
                            .object(objectName)
                            .expiry(1, TimeUnit.DAYS)
                            //.extraHeaders(headers)
                            .build()
            );
            return presignedObjectUrl;
        } catch (Exception e) {
            return null;
        }
    }
    /**
     *  创建分块任务
     *
     * @param objectName 文件全路径名称
     * @param partCount 分片数量
     * @return /
     */
    public static Map<String, Object> initMultiPartUpload(String objectName, int partCount, String contentType) {
        Map<String, Object> result = new HashMap<>();
        try {
            //如果类型使用默认流会导致无法预览
            contentType = "application/octet-stream";
            HashMultimap<String, String> headers = HashMultimap.create();
            headers.put("Content-Type", contentType);
            initMinioMultipart(minioUrl,minioName,minioPass);
            checkAsyncBucket(false,bucketName);
            String uploadId = minioClientMulty.initMultiPartUpload(bucketName, null, objectName, headers, null);
            result.put("uploadId", uploadId);
            List<String> partList = new ArrayList<>();
            Map<String, String> reqParams = new HashMap<>();
            reqParams.put("uploadId", uploadId);
            for (int i = 1; i <= partCount; i++) {
                reqParams.put("partNumber", String.valueOf(i));
                String uploadUrl = minioClientMulty.getPresignedObjectUrl(
                        GetPresignedObjectUrlArgs.builder()
                                .method(Method.PUT)
                                .bucket(bucketName)
                                .object(objectName)
                                .expiry(1, TimeUnit.DAYS)
                                .extraQueryParams(reqParams)
                                .build());
                partList.add(uploadUrl);
            }
            result.put("uploadUrls", partList);
        } catch (Exception e) {
            log.error("初始化分片上传出错",e);
            return null;
        }
        return result;
    }


    /**
     * 检查是否存在指定桶 不存在则先创建
     * @param versioning
     * @param bucket
     * @throws Exception
     */
    private static void checkAsyncBucket(boolean versioning, String bucket) throws Exception {
        CompletableFuture<Boolean> exists = minioClientMulty.bucketExists(BucketExistsArgs.builder().bucket(bucket).build());
        if (exists.isDone() && !exists.get()) {
            minioClientMulty.makeBucket(MakeBucketArgs.builder().bucket(bucket).build());
            //设置Procy属性 默认所有请求都能读取
            String config = "{ " +
                    "    \"Id\": \"Policy1\", " +
                    "    \"Version\": \"2012-10-17\", " +
                    "    \"Statement\": [ " +
                    "        { " +
                    "            \"Sid\": \"Statement1\", " +
                    "            \"Effect\": \"Allow\", " +
                    "            \"Action\": [ " +
                    "                \"s3:ListBucket\", " +
                    "                \"s3:GetObject\" " +
                    "            ], " +
                    "            \"Resource\": [ " +
                    "                \"arn:aws:s3:::"+bucket+"\", " +
                    "                \"arn:aws:s3:::"+bucket+"/*\" " +
                    "            ]," +
                    "            \"Principal\": \"*\"" +
                    "        } " +
                    "    ] " +
                    "}";
            minioClient.setBucketPolicy(
                    SetBucketPolicyArgs.builder().bucket(bucket).config(config).build());
        }
        // 版本控制
        CompletableFuture<VersioningConfiguration> configuration = minioClientMulty.getBucketVersioning(GetBucketVersioningArgs.builder().bucket(bucket).build());
        if(configuration.isDone()) {
            boolean enabled = configuration.get().status() == VersioningConfiguration.Status.ENABLED;
            if (versioning && !enabled) {
                minioClientMulty.setBucketVersioning(SetBucketVersioningArgs.builder()
                        .config(new VersioningConfiguration(VersioningConfiguration.Status.ENABLED, null)).bucket(bucket).build());
            } else if (!versioning && enabled) {
                minioClientMulty.setBucketVersioning(SetBucketVersioningArgs.builder()
                        .config(new VersioningConfiguration(VersioningConfiguration.Status.SUSPENDED, null)).bucket(bucket).build());
            }
        }
    }

    public static List<String> getExsitParts(String objectName, String uploadId) {
        List<String> parts = new ArrayList<>();
        try {
            initMinioMultipart(minioUrl,minioName,minioPass);
            /**
             *  最大分片1000
             */
            ListPartsResponse partResult = minioClientMulty.listMultipart(bucketName, null, objectName, 1024, 0, uploadId, null, null);
            for (Part part : partResult.result().partList()) {
                parts.add(part.etag());
            }
            //合并分片
        } catch (Exception e) {
            //
            log.error("查询任务分片错误");
        }
        return parts;
    }


    /**
     * 文件合并
     * @param objectName
     * @param uploadId
     * @return
     */
    public static String mergeMultipartUpload(String objectName, String uploadId) {
        try {
            Part[] parts = new Part[1000];
            /**
             *  最大分片1000
             */
            initMinioMultipart(minioUrl,minioName,minioPass);
            ListPartsResponse partResult = minioClientMulty.listMultipart(bucketName, null, objectName, 1000, 0, uploadId, null, null);
            int partNumber = 1;
            for (Part part : partResult.result().partList()) {
                parts[partNumber - 1] = new Part(partNumber, part.etag());
                partNumber++;
            }
            //合并分片
            minioClientMulty.mergeMultipartUpload(bucketName, null, objectName, uploadId, parts, null, null);
            return minioUrl+bucketName+"/"+objectName;
        } catch (Exception e) {
            log.error("合并分片出错",e);
            return null;
        }
    }

    /**
     * 删除指定分片上传任务
     * @param objectName
     * @param uploadId
     * @return
     */
    public static boolean removeMultipartUpload(String objectName, String uploadId) {
        try {
            /**
             *  最大分片1000
             */
            initMinioMultipart(minioUrl,minioName,minioPass);
            minioClientMulty.removeMultipartUpload(bucketName,null,objectName,uploadId,null,null);
        } catch (Exception e) {
            return false;
        }
        return true;
    }
}
