package com.remzbl.cpictureback.model.dto.picture;

import lombok.Data;

import java.io.Serializable;


@Data
public class PictureReviewRequest implements Serializable {

    /**
     * 图片id
     */
    private Long id;

    /**
     * 状态：0-待审核, 1-通过, 2-拒绝  3 已审核待通过
     */
    private Integer reviewStatus;

    /**
     * 审核信息
     */
    private String reviewMessage;


    private static final long serialVersionUID = 1L;
}