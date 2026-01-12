package org.jeecg.modules.upload.service.impl;

import com.alibaba.fastjson.JSON;
import com.alibaba.fastjson.JSONObject;
import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.baomidou.mybatisplus.core.toolkit.Assert;
import org.apache.commons.collections.CollectionUtils;
import org.apache.commons.lang3.StringUtils;
import org.jeecg.common.api.vo.Result;
import org.jeecg.common.constant.CommonConstant;
import org.jeecg.common.util.MinioUtil;
import org.jeecg.common.util.RedisUtil;
import org.jeecg.modules.upload.entity.UploadRecord;
import org.jeecg.modules.upload.mapper.UploadRecordMapper;
import org.jeecg.modules.upload.service.IUploadRecordService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;

import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.Map;

/**
 * @Description: 文件上传记录表
 * @Author: jeecg-boot
 * @Date:   2022-01-19
 * @Version: V1.0
 */
@Service
public class UploadRecordServiceImpl extends ServiceImpl<UploadRecordMapper, UploadRecord> implements IUploadRecordService {

    @Autowired
    RedisUtil redisUtil;


    @Override
    public Result createTask(UploadRecord condition) {
        //直接进入循环 如果没有重复文件 跳出循环
        //文件名
        String fileName = condition.getFileName();
        //文件后缀
        String[] fileSplits = fileName.split("\\.");
        String fileSuffix = fileSplits[fileSplits.length -1];
        //文件md5
        String md5 = condition.getMd5();
        condition.setSuffix(fileSuffix);
        Assert.notEmpty(md5,"文件MD5信息缺失");
        QueryWrapper<UploadRecord> queryWrapper=new QueryWrapper<>();
        queryWrapper.eq("md5",md5);
        queryWrapper.last("limit 1");
        UploadRecord record = baseMapper.selectOne(queryWrapper);
        JSONObject obj=new JSONObject();
        if(record!=null){
            QueryWrapper<UploadRecord> updateWrapper=new QueryWrapper<>();
            updateWrapper.eq("md5",md5);
            updateWrapper.ne("status",CommonConstant.STATUS_2);
            baseMapper.update(condition,updateWrapper);
            record = baseMapper.selectOne(queryWrapper);
            String status = record.getStatus();
            if(StringUtils.isEmpty(status)|| CommonConstant.STATUS_0.equals(status)){
                if(redisUtil.set("FILE_UPLOAD_"+md5,md5,1800)){
                    obj.put("code",1);
                    obj.put("id",record.getId());
                    return Result.OK(obj);
                }
                return Result.error("文件正在上传");
            }else if(CommonConstant.STATUS_1.equals(status)){
                if(redisUtil.get("FILE_UPLOAD_"+md5)==null){
                    obj.put("code",1);
                    obj.put("id",record.getId());
                    return Result.OK(obj);
                }
                return Result.error("文件正在上传");
            }else {
                obj.put("code",2);
                obj.put("url",record.getSavePath());
                return Result.OK(obj);
            }
        }
        if(redisUtil.set("FILE_UPLOAD_"+md5,1,1800)){
            condition.setStatus(CommonConstant.STATUS_0);
            baseMapper.insert(condition);
            obj.put("code",1);
            obj.put("id",condition.getId());
            return Result.OK(obj);
        }else {
            return Result.error("文件正在上传");
        }
    }

    @Override
    public Boolean cancelUpload(String md5) {
        QueryWrapper<UploadRecord> queryWrapper=new QueryWrapper<>();
        queryWrapper.eq("md5",md5);
        queryWrapper.ne("status", CommonConstant.STATUS_2);
        baseMapper.delete(queryWrapper);
        return redisUtil.expire("FILE_UPLOAD_"+md5,1);
    }

    @Override
    public Result initMultiPartUpload(Integer partCount,String objectName,String md5,String contentType) {
        JSONObject obj=new JSONObject();
        QueryWrapper<UploadRecord> queryWrapper=new QueryWrapper<>();
        queryWrapper.eq("md5",md5);
        queryWrapper.eq("status",CommonConstant.STATUS_2);
        if(baseMapper.selectCount(queryWrapper)>0){
            //上传完成了 不需要上传了 结束
            obj.put("finished",true);
        }else {
            if (partCount == 1) {
                //只有一个分片的情况下 直接返回上传地址
                String uploadObjectUrl = MinioUtil.getUploadObjectUrl(objectName);
                obj.put("uploadUrl", (new ArrayList<String>() {{
                    add(uploadObjectUrl);
                }}));
                String url=uploadObjectUrl;
                if(uploadObjectUrl.indexOf("?")>-1){
                    url=uploadObjectUrl.substring(0,uploadObjectUrl.indexOf("?"));
                }
                redisUtil.set("FILE_URL_"+md5,url,1800);
            } else {
                Map<String, Object> initRsl = MinioUtil.initMultiPartUpload(objectName, partCount, contentType);
                obj.put("uploadId", initRsl.get("uploadId").toString());
                obj.put("uploadUrl", ((List<String>) initRsl.get("uploadUrls")));
            }
            obj.put("finished",false);
            QueryWrapper<UploadRecord> updateQueryWrapper=new QueryWrapper<>();
            updateQueryWrapper.eq("md5",md5);
            UploadRecord record=new UploadRecord();
            record.setStatus(CommonConstant.STATUS_1);
            //将文件状态更新为正在上传
            baseMapper.update(record,updateQueryWrapper);
        }
        return Result.OK(obj);
    }

    @Override
    public Result mergeMultipartUpload(String objectName,String md5, String uploadId, Integer chuncks, boolean finished) {
        String url=null;
        //不是秒传的任务 先处理文件合并的操作
        if(!finished) {
            //判断分片数量  分片数量是一标识直接传完了
            if(chuncks>1) {
                //先判断文件列表是否完整
                List<String> partList = MinioUtil.getExsitParts(objectName, uploadId);
                if (CollectionUtils.isNotEmpty(partList)) {
                    //上传列表不是空 判断上传列表是否完整
                    if (chuncks.compareTo(partList.size()) < 0) {
                        //缺少分片
                        return Result.error("文件分片缺失，请重新上传");
                    } else {
                        //分片完整 整合并返回
                        url = MinioUtil.mergeMultipartUpload(objectName, uploadId);
                        if(url.indexOf("?")>-1){
                            url=url.substring(0,url.indexOf("?"));
                        }
                        if (StringUtils.isEmpty(url)) {
                            //合并失败
                            return Result.error("合并文件异常");
                        }
                    }
                } else {
                    return Result.error("文件分片缺失，请重新上传");
                }
            }else {
                url = (String) redisUtil.get("FILE_URL_" + md5);
            }
        }
        try {
            //更新位完成
            UploadRecord record = new UploadRecord();
            record.setMd5(md5);
            record.setStatus(CommonConstant.STATUS_2);
            record.setSavePath(url);
            //数据库中当前md5的所有上传任务尚未执行完成的
            QueryWrapper<UploadRecord> queryWrapper = new QueryWrapper<>();
            queryWrapper.eq("md5", md5);
            queryWrapper.and(q->{
                q.notIn("status", CommonConstant.STATUS_2);
            });
            baseMapper.update(record, queryWrapper);
        }finally {
            //释放锁
            redisUtil.expire("FILE_UPLOAD_"+md5,1);
        }
        return Result.OK(url);
    }

}
