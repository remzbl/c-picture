package com.remzbl.cpictureback.common;

import lombok.Data;
import java.io.Serializable;


//管理员发来的删除请求 是一个通用请求

@Data
public class DeleteRequest implements Serializable {

    /**
     * id
     */
    private Long id;

    /**
     * 点击删除时传递的页码
     */
    private int current;

    private static final long serialVersionUID = 1L;
}
