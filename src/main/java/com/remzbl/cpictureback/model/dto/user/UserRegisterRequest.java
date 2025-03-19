package com.remzbl.cpictureback.model.dto.user;

import lombok.Data;

import java.io.Serializable;

/**
 * 用户注册请求
 */



@Data
public class UserRegisterRequest implements Serializable {


    //用于序列化机制的常量 , 确保类的不同版本之间的兼容性
    private static final long serialVersionUID = 8735650154179439661L;

    /**
     * 账号
     */
    private String userAccount;

    /**
     * 密码
     */
    private String userPassword;

    /**
     * 确认密码
     */
    private String checkPassword;

}
