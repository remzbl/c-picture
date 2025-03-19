package com.remzbl.cpictureback.manager.upload;

import cn.hutool.core.collection.CollUtil;
import cn.hutool.core.date.DateUtil;
import cn.hutool.core.io.FileUtil;
import cn.hutool.core.util.NumberUtil;
import cn.hutool.core.util.RandomUtil;
import com.qcloud.cos.model.PutObjectResult;
import com.qcloud.cos.model.ciModel.persistence.CIObject;
import com.qcloud.cos.model.ciModel.persistence.ImageInfo;
import com.qcloud.cos.model.ciModel.persistence.ProcessResults;
import com.remzbl.cpictureback.config.CosClientConfig;
import com.remzbl.cpictureback.exception.BusinessException;
import com.remzbl.cpictureback.exception.ErrorCode;
import com.remzbl.cpictureback.manager.CosManager;
import com.remzbl.cpictureback.model.dto.file.UploadPictureResult;
import lombok.extern.slf4j.Slf4j;

import javax.annotation.Resource;
import java.io.File;
import java.util.Date;
import java.util.List;

/**
 * 上传图片的第二步 :
 * 图片上传模板
 */
@Slf4j
public abstract class PictureUploadTemplate {

    @Resource
    private CosClientConfig cosClientConfig;

    @Resource
    private CosManager cosManager;

    /**
     * 上传图片 ---- 校验图片有效性 + 构建图片上传路径 + 调用上传图片到对象存储的方法 + 获取上传图片后处理响应 + 将响应结果封装为结果实体类
     *
     * @param inputSource      文件
     * @param uploadPathPrefix 上传路径前缀
     * @return
     */
                                        // 前缀uploadPathPrefix : 需要在业务层区分上传图片的位置 即公共图库的上传 或是 私有图库的上传
    public UploadPictureResult uploadPicture(Object inputSource, String uploadPathPrefix) {
        // 1. 校验图片的有效性
        validPicture(inputSource);                                // 模板可变处1
        // 2. 构建图片的上传地址
            // 组成1 : 随机字符串
        String uuid = RandomUtil.randomString(16);
            // 组成2 : 文件类型 --- 从原始文件名中获取
        String originalFilename = getOriginFilename(inputSource); // 模板可变处2
        String suffix = FileUtil.getSuffix(originalFilename);
            // 组成3 : 上传时间
        String uploadData = DateUtil.formatDate(new Date());
            // 拼接文件的上传路径，而不是使用原始文件名称，可以增强安全性
        String uploadFilename = String.format("%s_%s.%s", uploadData, uuid, suffix);
        String uploadPath = String.format("/%s/%s", uploadPathPrefix, uploadFilename);
        File file = null;
        try {
            // 创建临时文件，获取文件到服务器
            file = File.createTempFile(uploadPath, null);
        // 3. 处理文件来源 , 就是将来源文件上传到 我们刚刚创建的临时文件中
            processFile(inputSource, file);                       // 模板可变处3 根据参数的不同 选择不同的文件上传方式
        // 4. 将临时文件上传到对象存储
            PutObjectResult putObjectResult = cosManager.putPictureObject(uploadPath, file);

        // 5. 获取上传图片后处理的结果  : 图片信息对象, 图片处理对象(压缩对象 或 缩略对象)
            // (1). 获取图片信息对象，封装返回结果
            ImageInfo imageInfo = putObjectResult.getCiUploadResult().getOriginalInfo().getImageInfo();
            // (2). 获取图片处理对象(列表)，封装返回结果
            ProcessResults processResults = putObjectResult.getCiUploadResult().getProcessResults();
            List<CIObject> objectList = processResults.getObjectList();
            if (CollUtil.isNotEmpty(objectList)) {
                // 获取压缩对象
                CIObject compressedCiObject = objectList.get(0);
                    // 缩略图默认等于压缩图
                CIObject thumbnailCiObject = compressedCiObject;
                // 有生成缩略图，才获取缩略图对象
                if (objectList.size() > 1) {
                    thumbnailCiObject = objectList.get(1);
                }
                // 封装存在图片处理的返回结果                   压缩图              缩略图
                return buildResult(originalFilename, compressedCiObject, thumbnailCiObject);
            }
            // 不存在图片处理时 封装原图返回结果
            return buildResult(originalFilename, file, uploadPath, imageInfo); //封装图片信息获取与设置方法
        } catch (Exception e) {
            log.error("图片上传到对象存储失败", e);
            throw new BusinessException(ErrorCode.SYSTEM_ERROR, "上传失败");
        } finally {
        // 6. 临时文件清理
            this.deleteTempFile(file); // 方法提取
        }

    }

    // 3个需要子类重写的抽象方法 : 全是和输入源相关的方法
    /**
     * 校验输入源（本地文件或 URL）
     */
    protected abstract void validPicture(Object inputSource);

    /**
     * 获取输入源的原始文件名
     */
    protected abstract String getOriginFilename(Object inputSource);

    /**
     * 处理输入源并生成本地临时文件
     */
    protected abstract void processFile(Object inputSource, File file) throws Exception;




    /**
     * 原图封装返回结果
     * @param originalFilename
     * @param file
     * @param uploadPath
     * @param imageInfo        对象存储返回的图片信息
     * @return
     */
    private UploadPictureResult buildResult(String originalFilename, File file, String uploadPath, ImageInfo imageInfo) {
        // 计算宽高
        int picWidth = imageInfo.getWidth();
        int picHeight = imageInfo.getHeight();
        double picScale = NumberUtil.round(picWidth * 1.0 / picHeight, 2).doubleValue();
        // 封装返回结果
        UploadPictureResult uploadPictureResult = new UploadPictureResult();
        uploadPictureResult.setUrl(cosClientConfig.getHost() + "/" + uploadPath);
        uploadPictureResult.setPicName(FileUtil.mainName(originalFilename));
        uploadPictureResult.setPicSize(FileUtil.size(file));
        uploadPictureResult.setPicWidth(picWidth);
        uploadPictureResult.setPicHeight(picHeight);
        uploadPictureResult.setPicScale(picScale);
        uploadPictureResult.setPicFormat(imageInfo.getFormat());
        // 返回可访问的地址
        return uploadPictureResult;
    }

    /**
     * 压缩图封装返回结果  添加缩略图参数
     *
     * @param originalFilename
     * @param compressedCiObject
     * @return UploadPictureResult
     */
                                                                    //压缩图对象
    private UploadPictureResult buildResult(String originalFilename, CIObject compressedCiObject, CIObject thumbnailCiObject) {

        int picWidth = compressedCiObject.getWidth();
        int picHeight = compressedCiObject.getHeight();
        double picScale = NumberUtil.round(picWidth * 1.0 / picHeight, 2).doubleValue();

        // 封装返回结果
        UploadPictureResult uploadPictureResult = new UploadPictureResult();

        // 设置压缩后的原图地址
        uploadPictureResult.setUrl(cosClientConfig.getHost() + "/" + compressedCiObject.getKey());
        // 设置缩略图地址
        uploadPictureResult.setThumbnailUrl(cosClientConfig.getHost() + "/" + thumbnailCiObject.getKey());

        uploadPictureResult.setPicName(FileUtil.mainName(originalFilename));
        uploadPictureResult.setPicWidth(picWidth);
        uploadPictureResult.setPicHeight(picHeight);
        uploadPictureResult.setPicScale(picScale);
        uploadPictureResult.setPicFormat(compressedCiObject.getFormat());
        uploadPictureResult.setPicSize(compressedCiObject.getSize().longValue());

        return uploadPictureResult;
    }



    /**
     * 清理临时文件
     *
     * @param file
     */
    public void deleteTempFile(File file) {
        if (file == null) {
            return;
        }
        // 删除临时文件
        boolean deleteResult = file.delete();
        if (!deleteResult) {
            log.error("file delete error, filepath = {}", file.getAbsolutePath());
        }


    }
}













