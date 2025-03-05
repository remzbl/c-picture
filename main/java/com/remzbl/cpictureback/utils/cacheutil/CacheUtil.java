package com.remzbl.cpictureback.utils.cacheutil;

import cn.hutool.core.lang.TypeReference;
import cn.hutool.core.util.NumberUtil;
import cn.hutool.core.util.RandomUtil;
import cn.hutool.json.JSONUtil;
import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.UpdateWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.github.benmanes.caffeine.cache.Cache;
import com.github.benmanes.caffeine.cache.Caffeine;
import com.remzbl.cpictureback.exception.ErrorCode;
import com.remzbl.cpictureback.exception.ThrowUtils;
import com.remzbl.cpictureback.mapper.PictureMapper;
import com.remzbl.cpictureback.model.dto.file.UploadPictureResult;
import com.remzbl.cpictureback.model.dto.picture.PictureEditByBatchRequest;
import com.remzbl.cpictureback.model.dto.picture.PictureEditRequest;
import com.remzbl.cpictureback.model.entity.Picture;
import com.remzbl.cpictureback.model.enums.PictureReviewStatusEnum;
import com.remzbl.cpictureback.model.vo.PictureVO;
import lombok.Data;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.ValueOperations;
import org.springframework.stereotype.Component;
import org.springframework.util.DigestUtils;

import javax.annotation.Resource;
import java.time.Duration;
import java.util.Collections;
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



    // 注入BaseMapper
    @Resource
    private PictureMapper pictureMapper;


    // ====================== 常量配置 ======================



    public static final String MainPUBLICKEY = "cpicture:MainPublicPage"; // 公共图库主页缓存
    public static final String PUBLICKEYPRE = "cpicture:PublicCache:"; // 公共图库条件缓存键前缀

    public static final String MainPRIVATEKEY = "cpicture:MainPrivatePage"; // 私有图库主页缓存
    public static final String PRIVATEKEYPRE = "cpicture:MainPrivatePage:"; // 私有图库条件缓存键前缀


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
     * 分类 与 标签 缓存全量清理缓存（本地缓存 + Redis缓存 清理）
     */
//    public void cleanAllCache() {
//        cleanLocalCache();
//        cleanRedisCache();
//        log.info("全量缓存清理完成");
//    }

    /**
     * 清理Redis缓存（优化版：使用SCAN命令）
     */
    public void cleanPrivateRedisCache() {
        Set<String> keys = stringRedisTemplate.keys(PRIVATEKEYPRE); // 获取所有redis前缀缓存的键
        if (keys != null && !keys.isEmpty()) {
            stringRedisTemplate.delete(keys);
            log.info("清理Redis缓存，共删除 {} 条", keys.size());
        }
    }

    public void cleanPublicRedisCache() {
        Set<String> keys = stringRedisTemplate.keys(PUBLICKEYPRE); // 获取所有redis前缀缓存的键
        if (keys != null && !keys.isEmpty()) {
            stringRedisTemplate.delete(keys);
            log.info("清理Redis缓存，共删除 {} 条", keys.size());
        }
    }

    /**
     * 清理本地缓存
     */
    public void cleanLocalCache() {
        localCache.invalidateAll();
    }





    //缓存key的更合理的设计
    public static String generateCacheKey(String category, List<String> tags) {
        // 处理空值
        String categoryPart = category != null ? category : "null";

        // 处理标签
        String tagsPart;
        if (tags != null && !tags.isEmpty()) {
            // 对标签排序，确保顺序不影响 Key
            Collections.sort(tags);
            tagsPart = String.join(",", tags);
        } else {
            tagsPart = "null";
        }
        String cacheKey = "category:" + categoryPart + ":tags:" + tagsPart;
        log.info("缓存key条件: {}", cacheKey);



        // 拼接缓存 Key
        return cacheKey;
    }

    /**
     * 更新缓存（上传图片后更新缓存）  已废弃, 在编辑后更新缓存即可
     * @param picture
     */
    public void updateCacheAfterUpload(Picture picture, UploadPictureResult uploadPictureResult) {

        Long spaceId = picture.getSpaceId();
        //uploadPictureResult.get
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
                        // 本地缓存更新
                        cleanLocalCache();
                        localCache.put(privatekey, updatedCacheValue);
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
                    // 本地缓存更新
                    cleanLocalCache();
                    localCache.put(publicKey, updatedCacheValue);
                    log.info("更新公共图库缓存成功{}" , localCache.getIfPresent(publicKey));
                    log.info("更新公共图库缓存成功{}" , localCache.getIfPresent(publicKey));
                    log.info("更新公共图库缓存成功{}" , localCache.getIfPresent(publicKey));
                    log.info("更新公共图库缓存成功{}" , localCache.getIfPresent(publicKey));
                    log.info("更新公共图库缓存成功{}" , localCache.getIfPresent(publicKey));
                }
            }


        }




    }



    /**
     * 更新缓存（编辑图片后清理缓存）
     * @param pictureEditRequest
     * @param spaceId
     */
    public void updateCacheAfterEdit(PictureEditRequest pictureEditRequest, Long spaceId , int source) {

        Long id = pictureEditRequest.getId();
        String name = pictureEditRequest.getName();
        String introduction = pictureEditRequest.getIntroduction();
        String category = pictureEditRequest.getCategory();
        List<String> tags = pictureEditRequest.getTags();




        // 获取编辑的图片对象
        Picture picture = pictureMapper.selectById(id);
        // 需要同步更新的3个缓存 : 1 主缓存 (2 分类 + 标签缓存) 这个是如果存在才更新 ,否则不更新

        //私有图库缓存
            // 主缓存
        String sCacheKey1 = MainPRIVATEKEY + spaceId;

            // 分类 + 标签缓存
        String sCacheKey2 = PRIVATEKEYPRE + generateCacheKey(category, tags)  +spaceId;

        //公共图库缓存
            // 主缓存
        String gCacheKey1 = MainPRIVATEKEY;

            // 分类 + 标签缓存
        String gCacheKey2 = PRIVATEKEYPRE + generateCacheKey(category, tags);

        ValueOperations<String, String> valueOps = stringRedisTemplate.opsForValue();

        // 私有图库缓存
        String sCachedValue1 = valueOps.get(sCacheKey1);
        String sCachedValue2 = valueOps.get(sCacheKey2);
        // 公共图库缓存
        String gCachedValue1 = valueOps.get(gCacheKey1);
        String gCachedValue2 = valueOps.get(gCacheKey2);

        // 私有图库缓存更新
        if(spaceId != null){

            if (sCachedValue1 != null) {
                updateAfterEdit(sCacheKey1 , sCachedValue1 , source , picture ,valueOps, id , name , introduction , category , tags);
            }

            if(sCachedValue2 != null){
                updateAfterEdit(sCacheKey2 , sCachedValue2 , source , picture ,valueOps, id , name , introduction , category , tags);
            }


            // 公共图库缓存更新
        }else{

            if (gCachedValue1 != null) {
                updateAfterEdit(gCacheKey1 , gCachedValue1 , source , picture ,valueOps, id , name , introduction , category , tags);
            }


            if(gCachedValue2 != null){
                updateAfterEdit(gCacheKey2 , gCachedValue2 , source , picture ,valueOps, id , name , introduction , category , tags);
            }


        }


    }

    private void updateAfterEdit(String cacheKey , String cachedValue ,int source, Picture picture, ValueOperations<String, String> valueOps, Long id, String name, String introduction, String category, List<String> tags) {
        // 来源于上传图片后的编辑同步缓存-----添加记录
        if(source == 1){
            if (cachedValue != null) {
                // 反序列化缓存数据
                Page<PictureVO> cachedPage = JSONUtil.toBean(cachedValue, Page.class);

                // 将新图片插入到缓存数据中
                PictureVO pictureVO = PictureVO.objToVo(picture);
                cachedPage.getRecords().add(0, pictureVO);
                String updatedCacheValue = JSONUtil.toJsonStr(cachedPage);

                //stringRedisTemplate.delete(MainPRIVATEKEY); 无需清理缓存,可以直接覆盖
                cleanPublicRedisCache(); // 删除所有缓存 , 主要是删除
                // redis主页缓存更新, 随机过期时间，避免缓存雪崩
                valueOps.set(cacheKey, updatedCacheValue,
                        10+ ThreadLocalRandom.current().nextInt(5), TimeUnit.MINUTES);
                // 本地缓存更新
                //cleanLocalCache();
                localCache.put(cacheKey, updatedCacheValue);
            }
        // 编辑已存在的图片
        }else{
            // 反序列化缓存数据
            Page<PictureVO> cachedPage = JSONUtil.toBean(cachedValue, new TypeReference<Page<PictureVO>>() {}, true);
            List<PictureVO> pictureVOList = cachedPage.getRecords();

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
            valueOps.set(cacheKey, updatedCacheValue, 10 + ThreadLocalRandom.current().nextInt(5), TimeUnit.MINUTES);// redis缓存更新

            // 本地缓存更新
            //cleanLocalCache();
            localCache.put(cacheKey, updatedCacheValue);
        }
    }




    /**
     * 批量修改图片后同步更新缓存
     * @param pictureEditByBatchRequest       批量修改图片的请求信息
     * @param spaceId        空间ID（用于匹配缓存Key）
     */
    public void updateCacheAfterBatchEdit(PictureEditByBatchRequest pictureEditByBatchRequest ,Long spaceId) {

        String category = pictureEditByBatchRequest.getCategory();
        List<String> tags = pictureEditByBatchRequest.getTags();


        // 需要同步更新的2个缓存 : 1 主缓存 (2 分类 + 标签缓存) 这个是如果存在才更新 ,否则不更新
        //私有图库缓存
        // 主缓存
        String sCacheKey1 = MainPRIVATEKEY + spaceId;

        // 分类 + 标签缓存
        String sCacheKey2 = PRIVATEKEYPRE + generateCacheKey(category, tags)  + spaceId;

        ValueOperations<String, String> valueOps = stringRedisTemplate.opsForValue();

        // 私有图库缓存
        String sCachedValue1 = valueOps.get(sCacheKey1);
        String sCachedValue2 = valueOps.get(sCacheKey2);
        log.info("sCachedValue1:{}",sCachedValue1);
        log.info("sCachedValue2:{}",sCachedValue2);

        if (sCachedValue1 != null) {
            updateAfterBatchEdit(sCacheKey1,sCachedValue1,category, tags, valueOps);
        }
        if(sCachedValue2 != null){
            updateAfterBatchEdit(sCacheKey2,sCachedValue2,category, tags, valueOps);
        }


    }

    private void updateAfterBatchEdit(String cacheKey,String cachedValue,String category, List<String> tags, ValueOperations<String, String> valueOps) {
        // 反序列化缓存数据
        Page<PictureVO> cachedPage = JSONUtil.toBean(cachedValue, new TypeReference<Page<PictureVO>>() {}, true);
        List<PictureVO> pictureVOList = cachedPage.getRecords();

        for (PictureVO picturercord : pictureVOList) {
            picturercord.setCategory(category);
            picturercord.setTags(tags);

        }

        cachedPage.setRecords(pictureVOList);
        String updatedCacheValue = JSONUtil.toJsonStr(cachedPage);
        valueOps.set(cacheKey, updatedCacheValue, 10 + ThreadLocalRandom.current().nextInt(5), TimeUnit.MINUTES);// redis缓存更新

        // 本地缓存更新
        localCache.put(cacheKey, updatedCacheValue);
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

                    try {
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
                                valueOps.set(privateKey, updatedCacheValue, 10 + ThreadLocalRandom.current().nextInt(5), TimeUnit.MINUTES);
                                // 本地缓存更新
                                cleanLocalCache();
                                localCache.put(privateKey, updatedCacheValue);
                            } catch (Exception e) {
                                log.error("更新缓存失败，Key: {}", privateKey, e);
                            }
                        }
                    } catch (Exception e) {
                        ThrowUtils.throwIf(true, ErrorCode.OPERATION_ERROR, "更新缓存失败");
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
                        valueOps.set(publicKey, updatedCacheValue, 10 + ThreadLocalRandom.current().nextInt(5), TimeUnit.MINUTES);
                        // 本地缓存更新
                        cleanLocalCache();
                        localCache.put(publicKey, updatedCacheValue);
                    } catch (Exception e) {
                        ThrowUtils.throwIf(true, ErrorCode.OPERATION_ERROR, "更新缓存失败");
                    }
                }
            }
        }


    }



    // 批量上传图片同步更新缓存 取消



    // 审核图片后定时批量获取已审核待开放的图片 将这些图片设为通过状态 ,同步更新缓存

    //@Scheduled(cron = "59 59 23 * * ?")
    public void releasePassPictureAndUpdateCache(){


        // 产生随机字符 , 作为此次审核的标识
        String randomString = RandomUtil.randomString(6);


        // 压缩randomNumber


        String relieseID = String.format("全部开放%s", randomString);

        UpdateWrapper<Picture> updateWrapper = new UpdateWrapper<>();
        updateWrapper.set("reviewStatus", PictureReviewStatusEnum.REVIEWEDWAITTOPASS.getValue())
                .set("reviewMessage", relieseID)
                .eq("reviewStatus", PictureReviewStatusEnum.PASS.getValue());



        int update = pictureMapper.update(null, updateWrapper);
        ThrowUtils.throwIf(update == 0, ErrorCode.OPERATION_ERROR , "开放图片失败");

        // 3. 获取所有新开放的图片数据
        List<Picture> newPublicPictures = pictureMapper.selectList(
                new QueryWrapper<Picture>()
                        .eq("reviewStatus", PictureReviewStatusEnum.REVIEWEDWAITTOPASS.getValue())
                        .eq("reviewMessage", relieseID)
        );
        List<PictureVO> pictureVOList = newPublicPictures.stream()
                .map(picture -> PictureVO.objToVo(picture))
                .collect(Collectors.toList());

        log.info("刚刚开放的图片数据: {}", pictureVOList);

        //Set<String> publickeys = stringRedisTemplate.keys("cpicture:PublicPage*");

        String mainpublickey = "cpicture:MainPublicPage";



            // 获取缓存数据
        ValueOperations<String, String> valueOps = stringRedisTemplate.opsForValue();
        String cachedValue = valueOps.get(mainpublickey);
        log.info("获取的缓存数据: {}", cachedValue);


        try {
            if (cachedValue != null) {
                // 将刚刚开放的图片加入缓存
                Page<PictureVO> cachedPage = JSONUtil.toBean(cachedValue, new TypeReference<Page<PictureVO>>() {}, true);
                //将pictureVOList添加到cachedPage.getRecords()中
                pictureVOList.stream()
                        .forEach(pictureVO -> cachedPage.getRecords().add( 0 ,pictureVO));

                //boolean result = cachedPage.getRecords().addAll(pictureVOList);
                log.info("缓存添加内容: {}", cachedPage.getRecords());

                //ThrowUtils.throwIf(!result, ErrorCode.OPERATION_ERROR , "缓存同步失败");

                String updatedCacheValue = JSONUtil.toJsonStr(cachedPage);
                valueOps.set(mainpublickey, updatedCacheValue, 10 + ThreadLocalRandom.current().nextInt(5), TimeUnit.MINUTES);
                // 本地缓存更新
                cleanLocalCache();
                localCache.put(mainpublickey, updatedCacheValue);

                log.info("缓存同步成功，Key: {}", mainpublickey);

            }
        } catch (Exception e) {
            ThrowUtils.throwIf(true, ErrorCode.OPERATION_ERROR , "缓存同步失败");
        }
    }
}
