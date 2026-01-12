package org.jeecg.modules.upload.service;

import org.jeecg.common.api.vo.Result;
import org.jeecg.modules.upload.entity.UploadRecord;
import com.baomidou.mybatisplus.extension.service.IService;

/**
 * @Description: 文件上传记录表
 * @Author: jeecg-boot
 * @Date:   2022-01-19
 * @Version: V1.0
 */
public interface IUploadRecordService extends IService<UploadRecord> {


    /**
     * 分片上传初始化
     *
     * @param partCount   分片数量
     * @param contentType /
     * @return /
     */
    Result initMultiPartUpload(Integer partCount,String objectName, String md5, String contentType);

    /**
     * 完成分片上传
     *
     * @param uploadId 标识
     * @return /
     */
    Result mergeMultipartUpload(String objectName,String md5, String uploadId,Integer chuncks, boolean finished);

    /**
     * 创建传输任务
     * @param condition
     * @return
     */
    Result createTask(UploadRecord condition);

    Boolean cancelUpload(String md5);
}
