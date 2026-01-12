/**
 * MINIO 遵循 AmazonS3 规则，S3 有的方法他都有实现
 * 关于其他方法
 * 参考 MINIO 网站
 * https://minio-java.min.io/
 * 结合 亚马逊官方文档
 * https://docs.aws.amazon.com/AmazonS3/latest/API
 * 查看方法使用和效果
 */
package org.jeecg.common.util;

import com.google.common.collect.Multimap;
import io.minio.*;
import io.minio.errors.*;
import io.minio.messages.Part;

import java.io.IOException;
import java.security.InvalidKeyException;
import java.security.NoSuchAlgorithmException;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;

public class CustomMinioClient extends MinioAsyncClient {

    /**
     * 需要清理的文件时间范围
     * 一天
     */
    private static int sevenDays = 1000 * 60 * 24;

    protected CustomMinioClient(MinioAsyncClient client) {
        super(client);
    }

    //初始化分块上传任务
    public String initMultiPartUpload(String bucket, String region, String object, Multimap<String, String> headers, Multimap<String, String> extraQueryParams) throws IOException, InvalidKeyException, NoSuchAlgorithmException, InsufficientDataException, ServerException, InternalException, XmlParserException, InvalidResponseException, ErrorResponseException, ExecutionException, InterruptedException {
        CompletableFuture<CreateMultipartUploadResponse> response = this.createMultipartUploadAsync(bucket, region, object, headers, extraQueryParams);
        return response.get().result().uploadId();
    }

    //中止分块上传任务
    public String removeMultipartUpload(String bucket, String region, String object,String uploadId, Multimap<String, String> headers, Multimap<String, String> extraQueryParams) throws IOException, InvalidKeyException, NoSuchAlgorithmException, InsufficientDataException, ServerException, InternalException, XmlParserException, InvalidResponseException, ErrorResponseException, ExecutionException, InterruptedException {
        CompletableFuture<AbortMultipartUploadResponse> response = this.abortMultipartUploadAsync(bucket, region, object, uploadId,headers, extraQueryParams);
        return response.get().uploadId();
    }

    //TODO 批量清理过期上传任务
    //获取不到桶内的上传任务 ListMultipartUploadsResponse result为空 待更新
    public void clearMultipartUpload(String bucket, String region, Multimap<String, String> headers, Multimap<String, String> extraQueryParams) throws IOException, InvalidKeyException, NoSuchAlgorithmException, InsufficientDataException, ServerException, InternalException, XmlParserException, InvalidResponseException, ErrorResponseException {
        //通过MINIO接口获取桶未完成的上传任务（获取不到上传任务）
        CompletableFuture<ListMultipartUploadsResponse> multiUploads = this.listMultipartUploadsAsync(bucket, region,null,null,null,1000,null,null,headers, extraQueryParams);
//        System.out.println(multiUploads);

        //直接调用 AWS接口 清理过期上传任务（获取不到上传任务）
//        Date oneWeekAgo = new Date(System.currentTimeMillis() - sevenDays);
//        Credentials creds = this.provider == null ? null : this.provider.fetch();
//
//        AWSCredentials awsCredentials = new AWSCredentials() {
//            @Override
//            public String getAWSAccessKeyId() {
//                return creds.accessKey();
//            }
//
//            @Override
//            public String getAWSSecretKey() {
//                return creds.secretKey();
//            }
//        };
//
//        AmazonS3Client s3 = new AmazonS3Client(awsCredentials);
//        s3.setEndpoint("http://127.0.0.1:9000");
//        TransferManager tm = new TransferManager(s3);
//        try {
//            tm.abortMultipartUploads(bucket, oneWeekAgo);
//        } catch (Exception ex) {
//            throw new RuntimeException(ex.getMessage());
//        }
    }

    //合并分块文件
    public ObjectWriteResponse mergeMultipartUpload(String bucketName, String region, String objectName, String uploadId, Part[] parts, Multimap<String, String> extraHeaders, Multimap<String, String> extraQueryParams) throws IOException, InvalidKeyException, NoSuchAlgorithmException, InsufficientDataException, ServerException, InternalException, XmlParserException, InvalidResponseException, ErrorResponseException, ExecutionException, InterruptedException {

        return this.completeMultipartUploadAsync(bucketName, region, objectName, uploadId, parts, extraHeaders, extraQueryParams).get();
    }

    //列出全部分块文件
    public ListPartsResponse listMultipart(String bucketName, String region, String objectName, Integer maxParts, Integer partNumberMarker, String uploadId, Multimap<String, String> extraHeaders, Multimap<String, String> extraQueryParams) throws NoSuchAlgorithmException, InsufficientDataException, IOException, InvalidKeyException, ServerException, XmlParserException, ErrorResponseException, InternalException, InvalidResponseException, ExecutionException, InterruptedException {
        return this.listPartsAsync(bucketName, region, objectName, maxParts, partNumberMarker, uploadId, extraHeaders, extraQueryParams).get();
    }
}
