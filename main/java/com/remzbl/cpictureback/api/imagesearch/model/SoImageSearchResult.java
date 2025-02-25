package com.remzbl.cpictureback.api.imagesearch.model;

import lombok.Data;

/**
 * 接受图片搜索结果
 */
@Data
public class SoImageSearchResult {

    /**
     * 图片地址
     */
    private String imgUrl;

    /**
     * 标题
     */
    private String title;

    /**
     * 图片key
     */
    private String imgkey;

    /**
     * HTTP
     */
    private String http;

    /**
     * HTTPS
     */
    private String https;
}