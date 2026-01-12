package org.jeecg.modules.system.controller;

import com.alibaba.fastjson.JSONObject;
import lombok.extern.slf4j.Slf4j;
import org.jeecg.common.api.vo.Result;
import org.jeecg.modules.upload.entity.UploadRecord;
import org.jeecg.modules.upload.service.IUploadRecordService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.util.Assert;
import org.springframework.web.bind.annotation.*;

import java.util.Optional;

@Slf4j
@RestController
@RequestMapping("/partUpload")
public class MinioPartUploadController {
    @Autowired
    IUploadRecordService service;
    /**
     * 创建传输任务
     */
    @PostMapping("/createTask")
    public Result createTask(@RequestBody UploadRecord condition){
        return service.createTask(condition);
    }

    /**
     * 分片初始化 获取上传地址
     * @param requestParam 请求参数-此处简单处理
     * @return /
     */
    @PostMapping("/init")
    public Result initMultiPartInit(@RequestBody JSONObject requestParam) {
        // md5-可进行秒传判断
        String md5 = requestParam.getString("md5");
        String objectName = requestParam.getString("objectName");
        Assert.notNull(md5,"分片文件MD5缺失");
        // 分片数量
        Integer partCount = Optional.of(requestParam.getIntValue("partCount")).orElse(1);
        // 分片数量
        String contentType = requestParam.getString("contentType");

        return service.initMultiPartUpload(partCount,objectName,md5,contentType);
    }

    /**
     * 完成上传 触发合并分片
     * @param requestParam 用户参数
     * @return /
     */
    @PostMapping("/merge")
    public Result completeMultiPartUpload(@RequestBody JSONObject requestParam) {
        String objectName = requestParam.getString("objectName");
        String md5 = requestParam.getString("md5");
        String uploadId = requestParam.getString("uploadId");
        Integer chuncks = requestParam.getInteger("chuncks");
        boolean finished = requestParam.getBoolean("finished");
        Assert.notNull(md5, "md5 must not be null");
        Assert.notNull(finished, "finished must not be null");
        return service.mergeMultipartUpload(objectName,md5, uploadId,chuncks, finished);
    }
    @GetMapping("/cancel")
    public Result completeMultiPartUpload(String md5) {
        return Result.OK(service.cancelUpload(md5));
    }
}
