package com.remzbl.cpictureback.utils.cacheutil;

import cn.hutool.core.collection.CollUtil;
import cn.hutool.core.lang.TypeReference;
import cn.hutool.core.util.RandomUtil;
import cn.hutool.core.util.StrUtil;
import cn.hutool.json.JSONUtil;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.github.benmanes.caffeine.cache.Cache;
import com.github.benmanes.caffeine.cache.Caffeine;
import com.remzbl.cpictureback.model.dto.picture.PictureQueryRequest;
import com.remzbl.cpictureback.model.dto.picture.PictureUploadByBatchRequest;
import com.remzbl.cpictureback.model.dto.picture.PictureUploadRequest;
import com.remzbl.cpictureback.model.entity.Picture;
import com.remzbl.cpictureback.model.entity.User;
import com.remzbl.cpictureback.model.enums.PictureReviewStatusEnum;
import com.remzbl.cpictureback.model.vo.PictureVO;
import lombok.Data;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.ValueOperations;
import org.springframework.stereotype.Component;
import org.springframework.util.DigestUtils;

import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.concurrent.ConcurrentMap;
import java.util.concurrent.TimeUnit;

// 配置本地缓存Caffeine
// 实现一致性的第一种方法 (比较粗暴) : 上传图片后清理 本地缓存 与 redis缓存
// 实现一致性的第二种方法 : 上传图片后同步更新缓存

@Slf4j
@Component
@Data
public class CacheUtil {
    // ====================== 常量配置 ======================
    private static final String REDIS_KEY_PATTERN = "cpicture:listPictureVOByPage:*"; // Redis缓存键前缀
    private static final String LOCAL_CACHE_PREFIX = "cpicture:listPictureVOByPage:"; // 本地缓存键前缀

    // ====================== 依赖注入 ======================
    private final StringRedisTemplate stringRedisTemplate;

    // ====================== 本地缓存初始化 ==================
    private final Cache<String, String> localCache = Caffeine.newBuilder()
            .initialCapacity(1024)
            .maximumSize(10_000L)
            .expireAfterWrite(Duration.ofMinutes(5))
            .build();

    // ====================== 构造函数修正 ====================
//    public CacheUtil(StringRedisTemplate stringRedisTemplate) {
//        this.stringRedisTemplate = stringRedisTemplate;
//    }

    // ====================== 缓存清理方法 ====================

    /**
     * 全量清理缓存（本地缓存 + Redis缓存 清理）
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
        Set<String> keys = stringRedisTemplate.keys(REDIS_KEY_PATTERN); // 获取所有redis前缀缓存的键
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
     * 更新缓存（上传图片后清理缓存）
     * @param picture
     * @param pictureUploadRequest
     */
    public void updateCacheAfterUpload(Picture picture, PictureUploadRequest pictureUploadRequest) {
        // 构建查询条件
        PictureQueryRequest queryRequest = new PictureQueryRequest();
        queryRequest.setSpaceId(picture.getSpaceId());
        queryRequest.setCurrent(1); // 默认更新第一页缓存
        queryRequest.setPageSize(10); // 默认每页10条


        // 生成缓存 Key
        String queryCondition = JSONUtil.toJsonStr(queryRequest);
        String hashKey = DigestUtils.md5DigestAsHex(queryCondition.getBytes());
        String cacheKey = String.format("cpicture:listPictureVOByPage:%s", hashKey);

        // 从缓存中获取数据
        ValueOperations<String, String> valueOps = stringRedisTemplate.opsForValue();
        String cachedValue = valueOps.get(cacheKey);

        if (cachedValue != null) {
            // 反序列化缓存数据
            Page<PictureVO> cachedPage = JSONUtil.toBean(cachedValue, Page.class);

            // 将新图片插入到缓存数据中
            PictureVO pictureVO = PictureVO.objToVo(picture);
            List<PictureVO> records = cachedPage.getRecords();
            records.add(0, pictureVO); // 将新图片插入到列表开头

            // 测试缓存更新是否正常
            log.info("更新缓存：{}", picture.getName());
            log.info("更新缓存：{}", picture.getName());
            log.info("更新缓存：{}", picture.getName());
            log.info("更新缓存：{}", picture.getName());
            log.info("更新缓存：{}", picture.getName());
            log.info("更新缓存：{}", picture.getName());


            // 更新缓存
            String updatedCacheValue = JSONUtil.toJsonStr(cachedPage);
            valueOps.set(cacheKey, updatedCacheValue); // redis缓存更新
            localCache.put(cacheKey, updatedCacheValue); // 本地缓存更新
        }
    }


    // 批量上传图片同步更新缓存

    // region

    public void updateCacheAfterBatchUpload(List<PictureVO> newPictures, Long spaceId) {
        if (CollUtil.isEmpty(newPictures)) {
            return;
        }

        // 构建可能影响的查询条件（按空间 ID 和时间倒序）
        PictureQueryRequest queryRequest = new PictureQueryRequest();
        queryRequest.setSpaceId(spaceId); // 如果是公共图库，spaceId 为 null
        queryRequest.setReviewStatus(PictureReviewStatusEnum.PASS.getValue());


        // 更新前 3 页缓存（根据业务场景调整）
        for (int page = 1; page <= 3; page++) {
            queryRequest.setCurrent(page);
            queryRequest.setPageSize(10); // 需和原分页大小一致

            // 生成缓存 Key
            String queryJson = JSONUtil.toJsonStr(queryRequest);
            String hashKey = DigestUtils.md5DigestAsHex(queryJson.getBytes());
            String cacheKey = String.format("cpicture:listPictureVOByPage:%s", hashKey);

            // 更新 Redis 缓存
            updateRedisCache(cacheKey, newPictures);

            // 更新本地缓存
            updateLocalCache(cacheKey, newPictures);
        }
    }

    public void updateRedisCache(String cacheKey, List<PictureVO> newPictures) {
        ValueOperations<String, String> valueOps = stringRedisTemplate.opsForValue();
        String cachedValue = valueOps.get(cacheKey);

        if (StrUtil.isNotBlank(cachedValue)) {
            // 反序列化分页数据
            Page<PictureVO> cachedPage = JSONUtil.toBean(cachedValue, new TypeReference<Page<PictureVO>>() {}, false);

            // 将新图片插入到列表头部（按时间倒序）
            List<PictureVO> updatedRecords = new ArrayList<>(newPictures);
            updatedRecords.addAll(cachedPage.getRecords());

            // 保持分页大小，移除超出部分
            if (updatedRecords.size() > cachedPage.getSize()) {
                updatedRecords = updatedRecords.subList(0, (int) cachedPage.getSize());
            }

            // 构建更新后的分页对象
            Page<PictureVO> updatedPage = new Page<>(
                    cachedPage.getCurrent(),
                    cachedPage.getSize(),
                    cachedPage.getTotal() + newPictures.size() // 更新总数
            );
            updatedPage.setRecords(updatedRecords);

            // 重新序列化并写入 Redis
            String updatedValue = JSONUtil.toJsonStr(updatedPage);
            valueOps.set(cacheKey, updatedValue, 300 + RandomUtil.randomInt(0, 300), TimeUnit.SECONDS);
        }
    }

    public void updateLocalCache(String cacheKey, List<PictureVO> newPictures) {
        Cache<String, String> localCache = this.getLocalCache();
        String cachedValue = localCache.getIfPresent(cacheKey);

        if (StrUtil.isNotBlank(cachedValue)) {
            // 反序列化分页数据
            Page<PictureVO> cachedPage = JSONUtil.toBean(cachedValue, new TypeReference<Page<PictureVO>>() {}, false);

            // 更新逻辑同 Redis
            List<PictureVO> updatedRecords = new ArrayList<>(newPictures);
            updatedRecords.addAll(cachedPage.getRecords());
            if (updatedRecords.size() > cachedPage.getSize()) {
                updatedRecords = updatedRecords.subList(0, (int) cachedPage.getSize());
            }

            Page<PictureVO> updatedPage = new Page<>(
                    cachedPage.getCurrent(),
                    cachedPage.getSize(),
                    cachedPage.getTotal() + newPictures.size()
            );
            updatedPage.setRecords(updatedRecords);

            // 写入本地缓存
            localCache.put(cacheKey, JSONUtil.toJsonStr(updatedPage));
        }
    }


    // endregion









}
