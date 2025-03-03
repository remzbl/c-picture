package com.remzbl.cpictureback.utils.cacheutil;

import cn.hutool.core.lang.TypeReference;
import cn.hutool.json.JSONUtil;
import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.github.benmanes.caffeine.cache.Cache;
import com.github.benmanes.caffeine.cache.Caffeine;
import com.remzbl.cpictureback.model.dto.picture.PictureEditRequest;
import com.remzbl.cpictureback.model.entity.Picture;
import com.remzbl.cpictureback.model.vo.PictureVO;
import com.remzbl.cpictureback.service.impl.PictureServiceImpl;
import lombok.Data;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.ValueOperations;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import javax.annotation.Resource;
import java.time.Duration;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Set;
import java.util.concurrent.ConcurrentMap;
import java.util.concurrent.ThreadLocalRandom;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;

// 配置本地缓存Caffeine
// 实现一致性的第一种方法 (比较粗暴) : 上传图片后清理 本地缓存 与 redis缓存
// 实现一致性的第二种方法 : 上传图片后同步更新缓存

@Slf4j
@Component
@Data
public class CacheUtil {


    @Resource
    public PictureServiceImpl pictureServiceImpl;


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
     * @param spaceId
     */
    public void updateCacheAfterUpload(Picture picture, Long spaceId) {


        // 获取cpicture:PrivatePage开通的缓存key
        String pattern = "cpicture:PrivatePage*";
        String PublicPattern = "cpicture:PublicPage*";
        // 获取缓存中的key
        Set<String> privateKeys = stringRedisTemplate.keys(pattern);
        Set<String> publicKeys = stringRedisTemplate.keys(PublicPattern);
        log.info("批量修改后需更新的私人图库缓存Key: {}", privateKeys);
        log.info("批量修改后需更新的公共图库缓存Key: {}", publicKeys);


        if(spaceId != null){
            // 私人图库缓存更新
            for (String privatekey : privateKeys) {
                if (privatekey.contains(String.valueOf(spaceId))) {
                    // 获取缓存数据
                    ValueOperations<String, String> valueOps = stringRedisTemplate.opsForValue();
                    String cachedValue = valueOps.get(privatekey);

                    if (cachedValue != null) {
                        // 反序列化缓存数据
                        Page<PictureVO> cachedPage = JSONUtil.toBean(cachedValue, Page.class);

                        // 将新图片插入到缓存数据中
                        PictureVO pictureVO = PictureVO.objToVo(picture);
                        cachedPage.getRecords().add(0, pictureVO);
                        String updatedCacheValue = JSONUtil.toJsonStr(cachedPage);
                        // redis缓存更新
                        valueOps.set(privatekey, updatedCacheValue,
                                10+ ThreadLocalRandom.current().nextInt(5), TimeUnit.MINUTES); // 随机过期时间，避免缓存雪崩

                    }
                }
            }

        // 公共图库缓存更新
        }else{
            for (String publicKey : publicKeys) {
                // 获取缓存数据
                ValueOperations<String, String> valueOps = stringRedisTemplate.opsForValue();
                String cachedValue = valueOps.get(publicKey);

                if (cachedValue != null) {
                    // 反序列化缓存数据（注意处理分页泛型问题）
                    Page<PictureVO> cachedPage = JSONUtil.toBean(cachedValue, new TypeReference<Page<PictureVO>>() {}, false);

                    // 将新图片插入到缓存数据首部
                    PictureVO pictureVO = PictureVO.objToVo(picture);
                    cachedPage.getRecords().add(0, pictureVO);


                    // 序列化并更新缓存（建议设置随机过期时间防雪崩）
                    String updatedCacheValue = JSONUtil.toJsonStr(cachedPage);
                    valueOps.set(publicKey, updatedCacheValue,
                            10 + ThreadLocalRandom.current().nextInt(5), TimeUnit.MINUTES);
                }
            }


        }




    }


    /**
     * 更新缓存（编辑图片后清理缓存）
     * @param pictureEditRequest
     * @param spaceId
     */
    public void updateCacheAfterEdit(PictureEditRequest pictureEditRequest, Long spaceId) {

        Long id = pictureEditRequest.getId();
        String name = pictureEditRequest.getName();
        String introduction = pictureEditRequest.getIntroduction();
        String category = pictureEditRequest.getCategory();
        List<String> tags = pictureEditRequest.getTags();

        // 获取cpicture:PrivatePage开通的缓存key
        String pattern = "cpicture:PrivatePage*";
        // 获取本地缓存中的key

        Set<String> privatekeys = stringRedisTemplate.keys(pattern);
        log.info("私人图库redis所有的缓存Key:{}", privatekeys);

        for (String privatekey : privatekeys) {
            if (privatekey.contains(String.valueOf(spaceId))) {
                // 获取缓存数据
                ValueOperations<String, String> valueOps = stringRedisTemplate.opsForValue();
                String cachedValue = valueOps.get(privatekey);

                if (cachedValue != null) {
                    // 反序列化缓存数据
                    Page<PictureVO> cachedPage = JSONUtil.toBean(cachedValue, new TypeReference<Page<PictureVO>>() {}, true);
                    List<PictureVO> pictureVOList = cachedPage.getRecords();

                    // 将新图片信息插入到缓存数据中


                    for (PictureVO picturercord : pictureVOList) {
                        if (picturercord.getId().equals(id)) {
                            picturercord.setName(name);
                            picturercord.setIntroduction(introduction);
                            picturercord.setCategory(category);
                            picturercord.setTags(tags);

                        }
                    }

                    cachedPage.setRecords(pictureVOList);
                    String updatedCacheValue = JSONUtil.toJsonStr(cachedPage);
                    valueOps.set(privatekey, updatedCacheValue, 10, TimeUnit.MINUTES);// redis缓存更新

                }
            }
        }

    }

    /**
     * 批量修改图片后同步更新缓存
     * @param updatedPictures 修改后的图片信息列表（需包含ID和新字段值）
     * @param spaceId        空间ID（用于匹配缓存Key）
     */
    public void updateCacheAfterBatchEdit(List<PictureVO> updatedPictures, Long spaceId) {
        // 匹配所有私人图库的缓存Key（例如："cpicture:PrivatePage*"）
        String PrivatePattern = "cpicture:PrivatePage*";
        Set<String> privateKeys = stringRedisTemplate.keys(PrivatePattern);
        log.info("批量修改后需更新的缓存Key: {}", privateKeys);


        // 提取所有被修改的图片ID（用于快速匹配）
        Set<Long> updatedPictureIds = updatedPictures.stream()
                .map(PictureVO::getId)
                .collect(Collectors.toSet());

        for (String privateKey : privateKeys) {
            // 仅处理属于当前spaceId的缓存
            if (privateKey.contains(String.valueOf(spaceId))) {
                ValueOperations<String, String> valueOps = stringRedisTemplate.opsForValue();
                String cachedValue = valueOps.get(privateKey);

                if (cachedValue != null) {
                    try {
                        // 反序列化缓存的分页数据
                        Page<PictureVO> cachedPage = JSONUtil.toBean(cachedValue, new TypeReference<Page<PictureVO>>() {}, true);

                        // 遍历缓存中的记录，更新被修改的图片
                        List<PictureVO> updatedRecords = cachedPage.getRecords().stream()
                                .map(record -> {
                                    if (updatedPictureIds.contains(record.getId())) {
                                        // 找到对应的修改后的图片信息
                                        PictureVO updatedPicture = updatedPictures.stream()
                                                .filter(p -> p.getId().equals(record.getId()))
                                                .findFirst()
                                                .orElse(null);
                                        if (updatedPicture != null) {
                                            // 更新字段（可根据需要选择部分字段）
                                            record.setName(updatedPicture.getName());
                                            record.setCategory(updatedPicture.getCategory());
                                            record.setTags(updatedPicture.getTags());
                                            record.setIntroduction(updatedPicture.getIntroduction());
                                        }
                                    }
                                    return record;
                                })
                                .collect(Collectors.toList());

                        // 更新分页数据
                        cachedPage.setRecords(updatedRecords);

                        // 序列化并更新缓存
                        String updatedCacheValue = JSONUtil.toJsonStr(cachedPage);
                        valueOps.set(privateKey, updatedCacheValue, 10, TimeUnit.MINUTES);
                    } catch (Exception e) {
                        log.error("批量修改后更新缓存失败，Key: {}", privateKey, e);
                    }
                }
            }
        }
    }


    /**
     * 删除图片后同步更新缓存
     * @param pictureId 被删除的图片ID
     * @param spaceId   空间ID（用于匹配缓存Key）
     */
    public void updateCacheAfterDelete(Long pictureId, Long spaceId) {
        // 匹配所有私人图库的缓存Key（例如："cpicture:PrivatePage:*"）
        String PrivatePattern = "cpicture:PrivatePage*";
        String PublicPattern = "cpicture:PublicPage*";
        Set<String> privateKeys = stringRedisTemplate.keys(PrivatePattern);
        Set<String> publicKeys = stringRedisTemplate.keys(PublicPattern);
        log.info("批量修改后需更新的缓存Key: {}", privateKeys);
        log.info("批量修改后需更新的缓存Key: {}", publicKeys);

        if( spaceId != null){
            // 私有图库删除图片同步更新缓存
            for (String privateKey : privateKeys) {
                // 仅处理属于当前spaceId的缓存
                if (privateKey.contains(String.valueOf(spaceId))) {
                    ValueOperations<String, String> valueOps = stringRedisTemplate.opsForValue();
                    String cachedValue = valueOps.get(privateKey);

                    if (cachedValue != null) {
                        try {
                            // 反序列化缓存的分页数据
                            Page<PictureVO> cachedPage = JSONUtil.toBean(cachedValue, new TypeReference<Page<PictureVO>>() {}, true);

                            // 过滤掉被删除的图片
                            List<PictureVO> remainingRecords = cachedPage.getRecords().stream()
                                    .filter(record -> !record.getId().equals(pictureId))
                                    .collect(Collectors.toList());

                            // 更新分页信息（总数减少）
                            cachedPage.setRecords(remainingRecords);
                            cachedPage.setTotal(cachedPage.getTotal() - 1);  // 总数减1

                            // 序列化并更新缓存
                            String updatedCacheValue = JSONUtil.toJsonStr(cachedPage);
                            valueOps.set(privateKey, updatedCacheValue, 10, TimeUnit.MINUTES);
                        } catch (Exception e) {
                            log.error("更新缓存失败，Key: {}", privateKey, e);
                        }
                    }
                }
            }
            // 管理员公共图库删除图片同步更新缓存
        }else{
            for (String publicKey : publicKeys) {
                ValueOperations<String, String> valueOps = stringRedisTemplate.opsForValue();
                String cachedValue = valueOps.get(publicKey);

                if (cachedValue != null) {
                    try {
                        // 反序列化带泛型的分页数据
                        Page<PictureVO> cachedPage = JSONUtil.toBean(cachedValue, new TypeReference<Page<PictureVO>>() {}, true);

                        // 流式处理删除目标图片（深拷贝避免污染原集合）
                        List<PictureVO> remainingRecords = cachedPage.getRecords().stream()
                                .filter(record -> !record.getId().equals(pictureId))
                                .collect(Collectors.toList());

                        // 更新分页信息（总数减少）
                        cachedPage.setRecords(remainingRecords);
                        cachedPage.setTotal(cachedPage.getTotal() - 1);  // 总数减1

                        // 序列化并更新缓存
                        String updatedCacheValue = JSONUtil.toJsonStr(cachedPage);
                        valueOps.set(publicKey, updatedCacheValue, 10, TimeUnit.MINUTES);
                    } catch (Exception e) {
                        log.error("更新公共图库缓存失败，Key: {}", publicKey, e);
                    }
                }
            }
        }


    }



    // 批量上传图片同步更新缓存 取消



    // 审核图片后定时批量获取已审核待开放的图片 将这些图片设为通过状态 ,同步更新缓存

    //@Scheduled(cron = "59 59 23 * * ?")
    public void releasePassPictureAndUpdateCache(){

        QueryWrapper<Picture> queryWrapper = new QueryWrapper<>();
        // 查询今日已审核的图片
        queryWrapper.eq("review_status", 1)
                .eq("review_message", "通过")
                .ge("create_time", LocalDateTime.now().minusDays(1));

        List<Picture> pictureList = pictureServiceImpl.list(queryWrapper);

        // 遍历图片列表，更新状态为开放
        for (Picture picture : pictureList) {
            picture.setReviewStatus(3);
            pictureServiceImpl.updateById(picture);
        }


    }











}
