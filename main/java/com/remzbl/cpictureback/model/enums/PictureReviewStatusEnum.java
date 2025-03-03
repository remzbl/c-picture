package com.remzbl.cpictureback.model.enums;

import cn.hutool.core.util.ObjUtil;
import lombok.Getter;

@Getter
public enum PictureReviewStatusEnum {
    REVIEWING("待审核", 0),
    PASS("通过", 1),       // 实际是已审核待开放 状态
    REJECT("拒绝", 2),
    // 添加 已审核待通过 状态  后续批量开放这些图片
    REVIEWEDWAITTOPASS("开放", 3);

    private final String text;
    private final int value;

    PictureReviewStatusEnum(String text, int value) {
        this.text = text;
        this.value = value;
    }

    /**
     * 根据 value 获取枚举
     */
    public static PictureReviewStatusEnum getEnumByValue(Integer value) {
        if (ObjUtil.isEmpty(value)) {
            return null;
        }
        for (PictureReviewStatusEnum pictureReviewStatusEnum : PictureReviewStatusEnum.values()) {
            if (pictureReviewStatusEnum.value == value) {
                return pictureReviewStatusEnum;
            }
        }
        return null;
    }
}
