package com.remzbl.cpictureback.exception;

import lombok.Getter;

@Getter
public class BusinessException extends RuntimeException {

    /**
     * 错误码
     */
    private final int code; // 自定义异常中的特有字段  用来表征我们自定义的异常状态码

    //下面的相同方法名 不同形参 的 重载构造方法  用来适应不同情况(传入不同参数)的构造
    //主要是重构状态码信息

    public BusinessException(int code, String message) {
        super(message);   //父类存在的字段 直接交给父类构造
        this.code = code;
    }

    public BusinessException(ErrorCode errorCode) {
        super(errorCode.getMessage());
        this.code = errorCode.getCode();
    }

    public BusinessException(ErrorCode errorCode, String message) {
        super(message);
        this.code = errorCode.getCode();
    }

}
