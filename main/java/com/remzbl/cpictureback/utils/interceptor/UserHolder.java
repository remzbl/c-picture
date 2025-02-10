package com.remzbl.cpictureback.utils.interceptor;

import com.remzbl.cpictureback.model.dto.user.RedisUser;

public class UserHolder {
    private static final ThreadLocal<RedisUser> tl = new ThreadLocal<>();

    public static void saveUser(RedisUser redisuser) {
        tl.set(redisuser);
    }

    public static RedisUser getUser() {
        return tl.get();
    }

    public static void removeUser() {
        tl.remove();  // 防止内存泄漏
    }
}