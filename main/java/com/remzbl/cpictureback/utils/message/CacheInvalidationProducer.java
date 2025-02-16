package com.remzbl.cpictureback.utils.message;

import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;

import javax.annotation.Resource;
import java.util.HashMap;
import java.util.Map;


@Component
public class CacheInvalidationProducer {
    // 定义Redis Stream键名常量
    private static final String PUBLIC_STREAM = "cache_pub_stream";  // 公共流名称
    private static final String PRIVATE_STREAM = "cache_pri_stream"; // 私有流名称

    @Resource
    private StringRedisTemplate redisTemplate; // Redis操作模板

    /**
     * 发送缓存失效消息到指定流
     * @param cacheKey 需要失效的缓存键模式（支持通配符）
     * @param isPublic 是否为公共图库操作
     *
     * **关键逻辑**：
     * 1. 根据类型选择对应的消息流
     * 2. 构建包含缓存键和类型的信息包
     * 3. 使用XADD命令将消息写入Stream
     */
    public void sendInvalidationMessage(String cacheKey, boolean isPublic) {
        // 确定目标Stream
        String streamKey = isPublic ? PUBLIC_STREAM : PRIVATE_STREAM;

        // 构建消息内容（HashMap会被自动序列化为键值对）
        Map<String, String> message = new HashMap<>(){{
            put("cacheKey", cacheKey);          // 缓存键模式
            put("type", isPublic ? "public" : "private"); // 消息类型
            put("timestamp", String.valueOf(System.currentTimeMillis())); // 时间戳
        }};

        // 将消息添加到Redis Stream（自动生成消息ID）
        redisTemplate.opsForStream().add(streamKey, message);
    }
}

