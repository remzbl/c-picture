package com.remzbl.cpictureback.model.dto.picture;

import lombok.Data;

import java.io.Serializable;
import java.util.List;

/**
 * 图片编辑请求
 */
@Data
public class PictureEditRequest implements Serializable {

    /**
     * id
     */
    private Long id;


    /**
     * 图片名称
     */
    private String name;

    /**
     * 简介
     */
    private String introduction;

    /**
     * 分类
     */
    private String category;

    /**
     * 标签
     */
    private List<String> tags;

    /**
     * 判断是否为上传文件后的编辑图片信息
     */
    private int uploadFile;   // 1 为上传文件后的编辑图片信息
                              // 2 为直接编辑图片信息



    private static final long serialVersionUID = 1L;
}