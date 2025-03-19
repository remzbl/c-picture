package com.remzbl.cpictureback.model.dto.picture;

import lombok.Data;

import java.io.Serializable;

@Data
public class PictureUploadRequest implements Serializable {

    // 这里的上传请求中所传递的信息很少
    // 一般此请求中会包含图片的基础信息 , 但这个工作交给数据万象来解析 UploadPictureResult来接收图片的解析信息

    /**
     * 图片 id（非空时 用于更新）
     * 为空时 用于新增
     */
    private Long id;

    /**
     * 文件地址
     *
     */
    private String fileUrl;

    /**
     * 图片名称
     */
    private String picName;

    /**
     * 空间 id
     */
    private Long spaceId;

    private static final long serialVersionUID = 1L;
}