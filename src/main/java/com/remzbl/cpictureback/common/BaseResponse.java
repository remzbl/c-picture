package com.remzbl.cpictureback.common;

import com.remzbl.cpictureback.exception.ErrorCode;
import lombok.Data;
import java.io.Serializable;

/*
@Data自动生成了以下内容：

        - Getter 和 Setter 方法
- toString() 方法
- equals()和 hashCode() 方法
- 默认的无参构造函数
*/
@Data

public class BaseResponse<T> implements Serializable {
    //在创建响应对象时指定该对象<T>的类型时, 就是指定了响应体中数据的类型
    private int code; //状态码

    private T data;   // 该响应数据可以为任何类型

    private String message; //响应信息

    // 标准的全参数构造方法
    public BaseResponse(int code, T data, String message) {
        this.code = code;
        this.data = data;
        this.message = message;
    }
    // 无响应信息的构造方法
    public BaseResponse(int code, T data) {
        this(code, data, "");
    }
    // 适配异常信息的响应构造方法
    public BaseResponse(ErrorCode errorCode) {
        this(errorCode.getCode(), null, errorCode.getMessage());
    }

    public BaseResponse(ErrorCode errorCode , T data) {
        this(errorCode.getCode(), data, errorCode.getMessage());
    }
}
