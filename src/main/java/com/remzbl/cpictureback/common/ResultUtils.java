package com.remzbl.cpictureback.common;

import com.remzbl.cpictureback.exception.ErrorCode;

public class ResultUtils {

    /**
     * 成功
     *
     * @param data 数据
     * @param <T>  数据类型
     * @return 响应
     */

    //<T> BaseResponse<T> success   第一个<T>声明这是一个泛型方法
    //                              第二个<T>意思是表明返回值类型是BaseResponse 其中数据为<T>可为任意类型
    public static <T> BaseResponse<T> success(T data) {

        return new BaseResponse<>(0, data, "ok");
    }

    /**
     * 失败
     *
     * @param errorCode 错误码
     * @return 响应
     */
    public static BaseResponse<?> error(ErrorCode errorCode) {

        return new BaseResponse<>(errorCode);
    }


    // 以下方法中未指定泛型类型 , 所以未指明是泛型类(static后无<T>)  返回值中的泛型类中<?>也用了?替代
    /**
     * 失败
     *
     * @param code    错误码
     * @param message 错误信息
     * @return 响应
     */
    public static BaseResponse<?> error(int code, String message) {

        return new BaseResponse<>(code, null, message);
    }

    /**
     * 失败
     *
     * @param errorCode 错误码
     * @return 响应
     */
    public static BaseResponse<?> error(ErrorCode errorCode, String message) {
        return new BaseResponse<>(errorCode.getCode(), null, message);
    }
}
