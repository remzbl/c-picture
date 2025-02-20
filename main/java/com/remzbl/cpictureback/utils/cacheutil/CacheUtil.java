package com.remzbl.cpictureback.utils.cacheutil;

import com.github.benmanes.caffeine.cache.Cache;
import com.github.benmanes.caffeine.cache.Caffeine;
import lombok.Data;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;
import java.time.Duration;
import java.util.Set;
import java.util.concurrent.ConcurrentMap;

@Slf4j
@Component
@Data
public class CacheUtil {
    // ====================== 常量配置 ======================
    private static final String REDIS_KEY_PATTERN = "cpicture:listPictureVOByPage:*";
    private static final String LOCAL_CACHE_PREFIX = "cpicture:listPictureVOByPage:";

    // ====================== 依赖注入 ======================
    private final StringRedisTemplate stringRedisTemplate;

    // ====================== 本地缓存初始化 ==================
    private final Cache<String, String> localCache = Caffeine.newBuilder()
            .initialCapacity(1024)
            .maximumSize(10_000L)
            .expireAfterWrite(Duration.ofMinutes(5))
            .build();

    // ====================== 构造函数修正 ====================
    public CacheUtil(StringRedisTemplate stringRedisTemplate) {
        this.stringRedisTemplate = stringRedisTemplate;
    }

    // ====================== 缓存清理方法 ====================

    /**
     * 全量清理缓存（本地 + Redis）
     */
    public void cleanAllCache() {
        cleanLocalCache();
        cleanRedisCache();
        log.info("全量缓存清理完成");
    }

    /**
     * 清理Redis缓存（优化版：使用SCAN命令）
     */
    public void cleanRedisCache() {
        Set<String> keys = stringRedisTemplate.keys(REDIS_KEY_PATTERN);
        if (keys != null && !keys.isEmpty()) {
            stringRedisTemplate.delete(keys);
            log.info("清理Redis缓存，共删除 {} 条", keys.size());
        }
    }

    /**
     * 清理本地缓存
     */
    public void cleanLocalCache() {
        ConcurrentMap<String, String> cacheMap = localCache.asMap();
        int beforeSize = cacheMap.size();
        cacheMap.keySet().removeIf(key -> key.startsWith(LOCAL_CACHE_PREFIX));
        log.info("清理本地缓存，删除 {} 条", beforeSize - cacheMap.size());
    }

    /**
     * 按模式清理缓存
     * @param pattern 键前缀（如 "cpicture:listPictureVOByPage:user123"）
     */
    public void cleanByPattern(String pattern) {
        // 清理本地
        ConcurrentMap<String, String> cacheMap = localCache.asMap();
        int localCount = cacheMap.size();
        cacheMap.keySet().removeIf(key -> key.startsWith(pattern));
        localCount -= cacheMap.size();

        // 清理Redis（添加通配符）
        String redisPattern = pattern + "*";
        Set<String> redisKeys = stringRedisTemplate.keys(redisPattern);
        int redisCount = (redisKeys != null) ? redisKeys.size() : 0;
        if (redisKeys != null && !redisKeys.isEmpty()) {
            stringRedisTemplate.delete(redisKeys);
        }

        log.info("按模式清理完成：本地删除 {} 条，Redis删除 {} 条", localCount, redisCount);
    }

    // ====================== 其他工具方法 ====================
    /**
     * 获取完整的缓存键（供外部使用）
     */
    public String generateFullCacheKey(String prefix, String identifier) {
        return LOCAL_CACHE_PREFIX + prefix + ":" + identifier;
    }
}
