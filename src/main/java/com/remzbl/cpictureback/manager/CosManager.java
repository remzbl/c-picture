package com.remzbl.cpictureback.manager;

import cn.hutool.core.io.FileUtil;
import com.qcloud.cos.COSClient;
import com.qcloud.cos.model.COSObject;
import com.qcloud.cos.model.GetObjectRequest;
import com.qcloud.cos.model.PutObjectRequest;
import com.qcloud.cos.model.PutObjectResult;
import com.qcloud.cos.model.ciModel.persistence.PicOperations;
import com.remzbl.cpictureback.config.CosClientConfig;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import javax.annotation.Resource;
import java.io.File;
import java.util.ArrayList;
import java.util.List;

@Slf4j
@Component
public class CosManager {

    @Resource
    private CosClientConfig cosClientConfig;

    @Resource
    private COSClient cosClient;

    /**
     * 上传对象 读取配置信息 , 访问对象存储(上传功能)
     * 这里涉及的对象都是cos对象存储提供的
     * @param key  唯一键(上传路径)
     * @param file 文件
     */
    public PutObjectResult putObject(String key, File file) {
        PutObjectRequest putObjectRequest = new PutObjectRequest(cosClientConfig.getBucket(), key, file); // 构造上传请求(传递访问对象存储的配置信息)
        return cosClient.putObject(putObjectRequest); // 执行上传操作(需传递上传请求)
    }

    /**
     * 下载对象
     *  这里涉及的对象都是cos对象存储提供的
     * @param key 唯一键
     */
    public COSObject getObject(String key) {
        GetObjectRequest getObjectRequest = new GetObjectRequest(cosClientConfig.getBucket(), key);
        return cosClient.getObject(getObjectRequest);
    }

    /**
     * 上传图片时添加的功能 : 图片信息解析 , 对图片添加压缩处理(缩略图处理)并存储压缩图片 ,
     * 上传图片对象 比上面的上传对象增加了利用数据万象功能解析图片信息的功能
     *  此处涉及的对象也都是cos对象存储提供
     * @param key  唯一键
     * @param file 文件
     * @return PutObjectResult 返回附带图片解析信息的结果--可以从中获取一些图片解析信息
     */
    public PutObjectResult putPictureObject(String key, File file) {
        //
        PutObjectRequest putObjectRequest = new PutObjectRequest(cosClientConfig.getBucket(), key, file); // 构造上传请求(传递访问对象存储的配置信息)

        // 对图片进行处理（获取基本信息也被视作为一种图片的处理）
            //官方提供的SDK中PicOperations 类用于记录图像操作
        //1. 创建图片操作对象
        PicOperations picOperations = new PicOperations();
            // 添加图片处理服务
            // (1). 返回原图信息 的图片处理
        picOperations.setIsPicInfo(1);


            // 构造总规则列表 可以添加一些图片处理服务  如 图片压缩处理  图片缩略图处理
        List<PicOperations.Rule> rules = new ArrayList<>();
            // (2). 图片压缩（转成 webp 格式）
                //创建压缩规则对象 (添加图片的路径 , 存储桶 , 压缩方式)
        PicOperations.Rule compressRule = new PicOperations.Rule();
                //设置压缩webp规则
        compressRule.setRule("imageMogr2/format/webp");
                //自定义图片上传主路径(手动设置图片后缀名) 并设置到规则中
        String webpKey = FileUtil.mainName(key) + ".webp";
        compressRule.setFileId(webpKey);
                //设置存储桶
        compressRule.setBucket(cosClientConfig.getBucket());
        rules.add(compressRule);//总规则添加压缩规则


            // (3). 缩略图处理，仅对 > 20 KB 的图片生成缩略图，并且缩略图大小不大于压缩图片大小
        if (file.length() > 2 * 1024 ) {
                //创建缩略图规则对象
            PicOperations.Rule thumbnailRule = new PicOperations.Rule();
                // 缩放规则 /thumbnail/<Width>x<Height>>（如果大于原图宽高，则不处理）
            thumbnailRule.setRule(String.format("imageMogr2/thumbnail/%sx%s>", 256, 256));
                //拼接缩略图的路径
            String thumbnailKey = FileUtil.mainName(key) + "_thumbnail." + FileUtil.getSuffix(key);
            thumbnailRule.setFileId(thumbnailKey);
                // 设置存储桶
            thumbnailRule.setBucket(cosClientConfig.getBucket());

            rules.add(thumbnailRule);
        }

        //2. 将所有图片处理规则添加到图片操作对象中
        picOperations.setRules(rules);
        //3. 将图片操作对象添加到上传对象请求中
        putObjectRequest.setPicOperations(picOperations);
        return cosClient.putObject(putObjectRequest);
    }

    /**
     * 删除对象
     *
     * @param key 唯一键
     */
   /* public void deleteObject(String key) {
        cosClient.deleteObject(cosClientConfig.getBucket(), key);

    }*/
    public void deleteObject(String key) {
        try {
            cosClient.deleteObject(cosClientConfig.getBucket(), key);
            log.info("COS 删除成功: {}", key);
        } catch (Exception e) {
            log.error("COS 删除失败: {}", key, e);
        }
    }

}