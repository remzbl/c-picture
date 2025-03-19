package com.remzbl.cpictureback.exception;

import lombok.Getter;


@Getter
public enum ErrorCode {

    //规定常见的错误信息 用一个枚举类来存储
    //其中每一个错误信息都是该枚举类的对象
    //应用场景 : 当前端发起的请求存在错误时, 该错误信息可以当作异常对象(可以自己自定义异常类)的参数进行实例化

    SUCCESS(0, "ok"),
    PARAMS_ERROR(40000, "请求参数错误"),
    NOT_LOGIN_ERROR(40100, "未登录"),
    NO_AUTH_ERROR(40101, "无权限"),
    NOT_FOUND_ERROR(40400, "请求数据不存在"),
    FORBIDDEN_ERROR(40300, "禁止访问"),
    SYSTEM_ERROR(50000, "系统内部异常"),
    OPERATION_ERROR(50001, "操作失败");

    /**
     * 状态码
     */
    private final int code;    //该自定义的异常状态码 需要自己自定义的异常类来适配

    /**
     * 信息
     */
    private final String message; // 常见的异常类中都有异常信息字段

    ErrorCode(int code, String message) {
        this.code = code;
        this.message = message;
    }

}