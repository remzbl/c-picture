package com.remzbl.cpictureback.model.dto.user;

import lombok.Data;



//用于存放一些简单的用户信息 , 以存放到redis中


@Data
public class RedisUser {


    /**
     * id
     */
    private Long id;

    /**
     * 用户昵称
     */
    private String userName;



}
