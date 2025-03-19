package com.remzbl.cpictureback.model.dto.picture;

import lombok.Data;

import java.io.Serializable;
import java.util.List;

/**
 * 图片批量编辑请求
 */
@Data
public class PictureEditByBatchRequest implements Serializable {

    /**
     * 图片 id 列表
     */
    private List<Long> pictureIdList;

    /**
     * 空间 id
     */
    private Long spaceId;

    /**
     * 修改的分类
     */
    private String category;

    /**
     * 修改的标签
     */
    private List<String> tags;


    private String oldCategory;

    private List<String> oldTags;

    /**
     * 修改图片的所在页号
     */
    private int current;

    /**
     * 修改图片的所在页面
     */
    private String  currentPage;


    /**
     * 命名规则
     */
    private String nameRule;

    private static final long serialVersionUID = 1L;
}