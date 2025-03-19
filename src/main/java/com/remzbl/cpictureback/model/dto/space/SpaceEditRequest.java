package com.remzbl.cpictureback.model.dto.space;

import lombok.Data;

import java.io.Serializable;

/**
 * 用户修改空间 -- 修改空间的名称  与  等级
 */
@Data
public class SpaceEditRequest implements Serializable {

    /**
     * 空间 id  (用来给后端获取 空间)
     */
    private Long id;

    /**
     * 空间名称 (目前只许修改名称)
     */
    private String spaceName;

    private static final long serialVersionUID = 1L;
}