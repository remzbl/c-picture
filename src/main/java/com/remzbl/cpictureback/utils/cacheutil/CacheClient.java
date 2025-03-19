package com.remzbl.cpictureback.utils.cacheutil;
import cn.hutool.core.lang.TypeReference;
import cn.hutool.core.util.BooleanUtil;
import cn.hutool.core.util.RandomUtil;
import cn.hutool.core.util.StrUtil;
import cn.hutool.json.JSONObject;
import cn.hutool.json.JSONUtil;
import com.baomidou.mybatisplus.core.conditions.Wrapper;
import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.UpdateWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.github.benmanes.caffeine.cache.Cache;
import com.github.benmanes.caffeine.cache.Caffeine;
import com.remzbl.cpictureback.exception.ErrorCode;
import com.remzbl.cpictureback.exception.ThrowUtils;
import com.remzbl.cpictureback.mapper.PictureMapper;
import com.remzbl.cpictureback.model.dto.picture.PictureEditRequest;
import com.remzbl.cpictureback.model.dto.picture.PictureQueryRequest;
import com.remzbl.cpictureback.model.entity.Picture;
import com.remzbl.cpictureback.model.enums.PictureReviewStatusEnum;
import com.remzbl.cpictureback.model.vo.PictureVO;
import lombok.Data;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.ValueOperations;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.util.concurrent.*;
import java.util.function.Function;



import javax.annotation.Resource;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.function.BiFunction;
import java.util.function.Function;
import java.util.stream.Collectors;

import static com.remzbl.cpictureback.utils.cacheutil.RedisConstants.*;

@Data
@Slf4j
@Component
public class CacheClient {

    @Resource
    private PictureMapper pictureMapper;


    private final StringRedisTemplate stringRedisTemplate;

    private static final ExecutorService CACHE_REBUILD_EXECUTOR = Executors.newFixedThreadPool(10);

    public CacheClient(StringRedisTemplate stringRedisTemplate) {
        this.stringRedisTemplate = stringRedisTemplate;
    }

    // ====================== 本地缓存初始化 ==================
    private final Cache<String, String> localCache = Caffeine.newBuilder()
            .initialCapacity(1024)
            .maximumSize(10_000L)
            .expireAfterWrite(Duration.ofMinutes(5))
            .build();


    // 缓存删除工具
    public void delPrivateCache(Long ID) {
        // 某个用户的私人图库缓存前缀
        String cachedKeyPrefix = PRIVATE_PRE_KEY + ID + "*";
        Set<String> keys = stringRedisTemplate.keys(cachedKeyPrefix);
        log.info("批量删除缓存Key: {}", keys);
        if (keys != null && !keys.isEmpty()) {
            log.info("批量删除缓存Key: {}", keys);
            // redis缓存删除
            stringRedisTemplate.delete(keys);  // 批量删除
            // 本地缓存删除
            for (String key : keys) {
                localCache.invalidate(key);
            }

        } else {
            log.info("没有找到匹配的缓存Key: {}", cachedKeyPrefix);
        }

    }

    public void delPublicCache(){
        String cachedKeyPrefix = PUBLIC_PRE_KEY + "*";
        Set<String> keys = stringRedisTemplate.keys(cachedKeyPrefix);
        log.info("批量删除缓存Key: {}", keys);
        if (keys != null && !keys.isEmpty()) {
            log.info("批量删除缓存Key: {}", keys);
            // redis缓存删除
            stringRedisTemplate.delete(keys);  // 批量删除
            // 本地缓存删除
            for (String key : keys) {
                localCache.invalidate(key);
            }
        } else {
            log.info("没有找到匹配的缓存Key: {}", cachedKeyPrefix);
        }
    }

    public void delPublicMainCache(){
        String cachedKey = PUBLIC_MAIN_PAGE + ":" + 1;
        String json = stringRedisTemplate.opsForValue().get(cachedKey);
        if(json!=null){
            // redis缓存删除
            stringRedisTemplate.delete(cachedKey);
            // 本地缓存删除
            localCache.invalidate(cachedKey);
        }
    }



    // 公共图库同步更新缓存
    public void updateCacheAfterUploadEdit(Picture picture) {

        // 公共图库缓存前缀
        String cachedKey = PUBLIC_MAIN_PAGE + ":" + 1;
        String cachedValue = stringRedisTemplate.opsForValue().get(cachedKey);
        // 反序列化缓存数据
        RedisData cachedData = JSONUtil.toBean(cachedValue, RedisData.class);
        Object cachedPage = cachedData.getData();
        Page<PictureVO> page = JSONUtil.toBean((JSONObject) cachedPage, new TypeReference<Page<PictureVO>>() {}, false);
        // 将新图片插入到缓存数据中
        PictureVO pictureVO = PictureVO.objToVo(picture);
        log.info("热点图片的获取: {}", page.getRecords());

        page.getRecords().add(0, pictureVO);
        page.getRecords().remove(page.getRecords().size()-1);
        // 清理其他页的缓存
        delPublicCache();

        // 序列化缓存数据
        // redis主页缓存更新 (逻辑过期时间的缓存创建)
        this.setWithLogicalExpire(cachedKey, page, CACHE_MAIN_TTL, TimeUnit.MINUTES);



        // 本地缓存更新
        // 序列化page
        String updatedCacheValue = JSONUtil.toJsonStr(page);
        localCache.put(cachedKey, updatedCacheValue);

    }

    public void updateCacheAfterEdit(Picture picture , PictureEditRequest pictureEditRequest) {
        Long id = pictureEditRequest.getId();
        String name = pictureEditRequest.getName();
        String introduction = pictureEditRequest.getIntroduction();
        String category = pictureEditRequest.getCategory();
        List<String> tags = pictureEditRequest.getTags();



        // 公共图库缓存前缀
        String cachedKey = PUBLIC_MAIN_PAGE + ":" + 1;
        ValueOperations<String, String> valueOps = stringRedisTemplate.opsForValue();
        String cachedValue = valueOps.get(cachedKey);

        // 反序列化缓存数据
        RedisData cachedData = JSONUtil.toBean(cachedValue, RedisData.class);
        Object cachedPage = cachedData.getData();
        Page<PictureVO> page = JSONUtil.toBean((JSONObject) cachedPage, new TypeReference<Page<PictureVO>>() {}, false);

        List<PictureVO> pictureVOList = page.getRecords();

        for (PictureVO picturercord : pictureVOList) {
            if (picturercord.getId().equals(id)) {
                picturercord.setName(name);
                picturercord.setIntroduction(introduction);
                picturercord.setCategory(category);
                picturercord.setTags(tags);


            }
        }
        // 序列化缓存数据
        page.setRecords(pictureVOList);
        this.setWithLogicalExpire(cachedKey, page, CACHE_MAIN_TTL, TimeUnit.MINUTES);



        // 本地缓存更新
        // 序列化page
        String updatedCacheValue = JSONUtil.toJsonStr(page);
        localCache.put(cachedKey, updatedCacheValue);
    }



    public void updateCacheAfterDelete(Long pictureId) {

        // 公共图库缓存前缀
        String cachedKey = PUBLIC_MAIN_PAGE + ":" + 1;
        ValueOperations<String, String> valueOps = stringRedisTemplate.opsForValue();
        String cachedValue = valueOps.get(cachedKey);

        // 反序列化缓存数据
        RedisData cachedData = JSONUtil.toBean(cachedValue, RedisData.class);
        Object cachedPage = cachedData.getData();
        Page<PictureVO> page = JSONUtil.toBean((JSONObject) cachedPage, new TypeReference<Page<PictureVO>>() {}, false);

        List<PictureVO> pictureVOList = page.getRecords();

        // 流式处理删除目标图片
        List<PictureVO> remainingRecords = pictureVOList.stream()
                .filter(record -> !record.getId().equals(pictureId))
                .collect(Collectors.toList());
        // 将处理后的数据设置回Page对象中
        page.setRecords(remainingRecords);

        // 从数据库中查询一个图片插入到热点缓存中
            // 从数据库中查询最新创建的图片
        Picture latestPicture = pictureMapper.selectOne(
                new QueryWrapper<Picture>()
                        .eq("isDelete", 0) // 未删除的图片
                        .eq("reviewStatus", PictureReviewStatusEnum.REVIEWEDWAITTOPASS.getValue()) // 开放状态下的图片
                        // 查询最早创建的一个图片
                        .orderByAsc("createTime")
                        .last("LIMIT 1")
        );

        // 将新图片插入到缓存数据中
        PictureVO latestPictureVO = PictureVO.objToVo(latestPicture);
        log.info("热点图片的获取: {}", page.getRecords());

        page.getRecords().add(page.getRecords().size(), latestPictureVO);

        // 清理其他页的缓存
        //delPublicCache();

        // 序列化缓存数据
        // redis主页缓存更新 (逻辑过期时间的缓存创建)
        this.setWithLogicalExpire(cachedKey, page, CACHE_MAIN_TTL, TimeUnit.MINUTES);

        // 本地缓存更新
        // 序列化page
        String updatedCacheValue = JSONUtil.toJsonStr(page);
        localCache.put(cachedKey, updatedCacheValue);


    }



// 审核图片后定时批量获取已审核待开放的图片 将这些图片设为通过状态 ,同步删除缓存

//@Scheduled(cron = "59 59 23 * * ?")
public void releasePassPictureAndUpdateCache() {


    // 产生随机字符 , 作为此次审核的标识
    String randomString = RandomUtil.randomString(6);
    // 压缩randomNumber
    String relieseID = String.format("全部开放%s", randomString);

    UpdateWrapper<Picture> updateWrapper = new UpdateWrapper<>();
    updateWrapper.set("reviewStatus", PictureReviewStatusEnum.REVIEWEDWAITTOPASS.getValue())
            .set("reviewMessage", relieseID)
            .eq("reviewStatus", PictureReviewStatusEnum.PASS.getValue());


    int update = pictureMapper.update(null, updateWrapper);
    ThrowUtils.throwIf(update == 0, ErrorCode.OPERATION_ERROR, "开放图片失败");

    // 3. 获取所有新开放的图片数据
//    List<Picture> newPublicPictures = pictureMapper.selectList(
//            new QueryWrapper<Picture>()
//                    .eq("reviewStatus", PictureReviewStatusEnum.REVIEWEDWAITTOPASS.getValue())
//                    .eq("reviewMessage", relieseID)
//    );
//    List<PictureVO> pictureVOList = newPublicPictures.stream()
//            .map(picture -> PictureVO.objToVo(picture))
//            .collect(Collectors.toList());
//
//    log.info("刚刚开放的图片数据: {}", pictureVOList);

    // 删除公共图库所有的缓存
    this.delPublicCache();
    this.delPublicMainCache();

}

// 普通场景 : 不采用多级缓存
// 热点场景 : 采用多级缓存


    // 普通场景下
// 普通场景的缓存创建
    public void set(String key, Object value, int time, TimeUnit unit) {
        stringRedisTemplate.opsForValue().set(key, JSONUtil.toJsonStr(value), time, unit);

    }

    // 普通场景下 解决缓存穿透的缓存 查询方式
    public <T, R> Page<R> queryWithPassThrough(
            String key,
            PictureQueryRequest pictureQueryRequest,
            int time,
            TimeUnit unit,
            Function<Object, Wrapper<T>> queryWrapperBuilder,     // 查询条件构造器
            BiFunction<Page<T>, Wrapper<T>, Page<T>> pageQuery,   // 分页查询方法
            Function<Page<T>, Page<R>> resultConverter           // 结果转换器
    ){

        int current = pictureQueryRequest.getCurrent();
        int size = pictureQueryRequest.getPageSize();

        // 查询缓存
        // 0.本地缓存若存在从本地缓存中查询

        String localCache = this.localCache.getIfPresent(key);
        if(localCache != null){
            // 如果缓存命中，返回结果
             Page<R> cachedPage = JSONUtil.toBean(localCache, new TypeReference<Page<R>>() {}, false);
             return cachedPage;

        }

        // 1.从redis查询商铺缓存
        String json = stringRedisTemplate.opsForValue().get(key);
        // 2.判断是否存在
        if (StrUtil.isNotBlank(json)) {
            // 能进来的一定是命中了有效的缓存
            // 3.存在，直接返回
            Page<R> cachedPage = JSONUtil.toBean(json, new TypeReference<Page<R>>() {}, false);
            log.info("命中有效缓存");
            return cachedPage;
        }
        // 判断命中的是否是空值
        if (json != null) {
            // 能进来的一定是命中了缓存中的空值 , 所以也要返回空分页
            // 返回一个错误信息
            log.info("未命中数据库 , 命中缓存的空值 ,返回空分页");
            return new Page<>();
        }

        // 4.不存在，根据id查询数据库
        //R r = dbFallback.apply(id);
        // 缓存未命中 就 查询数据库
        // 创建原信息分页对象
        // 2. 构建查询条件
        Wrapper<T> queryWrapper = queryWrapperBuilder.apply(pictureQueryRequest);

        // 3. 执行分页查询（使用传入的查询方法）
        Page<T> entityPage = pageQuery.apply(new Page<>(current, size), queryWrapper);

        // 4. 处理空结果
        if (entityPage == null || entityPage.getRecords().isEmpty()) {
            // 将空值写入redis
            stringRedisTemplate.opsForValue().set(key, "", CACHE_NULL_TTL, TimeUnit.MINUTES);
            log.info("命中数据库 , 数据库中不存在该数据 , 返回空值");
            return new Page<>();
        }
        // 5. 转换结果类型
        Page<R> resultPage = resultConverter.apply(entityPage);


        // 6.存在就存入 Redis 缓存
            //将Page<PictureVO>脱敏分页对象序列化为json字符串
        String cacheValue = JSONUtil.toJsonStr(resultPage);
        // 6.存在，写入redis
        this.set(key, cacheValue, time, unit);
        log.info("命中数据库,数据库存在该数据");

        return resultPage;
    }


// 热点数据场景下
    //方式一 :
    // 设置逻辑过期时间的缓存创建
    public void setWithLogicalExpire(String key, Object value, int time, TimeUnit unit) {

// 封装一个RedisData实体类 , 其中包含Object data 这个是用来存放我们需要缓存的数据
        //也包含ExpireTime expireTime 逻辑时间
        // 设置逻辑过期
        RedisData redisData = new RedisData();
        redisData.setData(value);
        redisData.setExpireTime(LocalDateTime.now().plusSeconds(unit.toSeconds(time)));
        // 写入Redis
        stringRedisTemplate.opsForValue().set(key, JSONUtil.toJsonStr(redisData));
    }


    // 使用下面的缓存查询方式 : 需提前确保缓存中存在有效的缓存数据
    //  配合设置逻辑过期时间的缓存创建方式 的 解决缓存击穿的缓存 查询方式
    public <T,R> Page<R> queryWithLogicalExpire(
            String key,
            PictureQueryRequest pictureQueryRequest,
            int time,
            TimeUnit unit,
            Function<Object, Wrapper<T>> queryWrapperBuilder,     // 查询条件构造器
            BiFunction<Page<T>, Wrapper<T>, Page<T>> pageQuery,   // 分页查询方法
            Function<Page<T>, Page<R>> resultConverter         // 结果转换器
            //Class<R> type                                         // 返回结果类型
    ) {
        int current = pictureQueryRequest.getCurrent();
        int size = pictureQueryRequest.getPageSize();

        // 1. 从Redis查询缓存
        String json = stringRedisTemplate.opsForValue().get(key);
        // 2. 判断是否存在
        if (StrUtil.isBlank(json)) {
            // 缓存未命中，返回空分页（表示需要重新查询数据库）
            log.info("缓存未命中，返回空分页");
            return new Page<>();
        }

        // 3. 命中缓存，反序列化为RedisData对象
        RedisData redisData = JSONUtil.toBean(json, RedisData.class);
        Page<R> cachedPage = JSONUtil.toBean((JSONObject) redisData.getData(), new TypeReference<Page<R>>() {}, false);
        LocalDateTime expireTime = redisData.getExpireTime();

        // 4. 判断缓存是否过期
        if (expireTime.isAfter(LocalDateTime.now())) {
            // 4.1 缓存未过期，直接返回缓存数据
            log.info("命中有效缓存，缓存未过期");
            return cachedPage;
        }

        // 4.2 缓存已过期，需要重建缓存
        // 5. 获取互斥锁
        String lockKey = PUBLIC_MAIN_PAGE; // 将主页热点图片数据缓存的键设为锁
        boolean isLock = tryLock(lockKey);
        if (isLock) {
            // 5.1 成功获取锁，开启独立线程重建缓存
            CACHE_REBUILD_EXECUTOR.submit(() -> {
                try {
                    // 构建查询条件
                    Wrapper<T> queryWrapper = queryWrapperBuilder.apply(pictureQueryRequest);
                    // 执行分页查询
                    Page<T> entityPage = pageQuery.apply(new Page<>(current, size), queryWrapper);
                    // 转换结果类型
                    Page<R> resultPage = resultConverter.apply(entityPage);
                    // 重建缓存
                    this.setWithLogicalExpire(key, resultPage, time, unit);

                    log.info("缓存过期,命中数据库,重建缓存");
                } catch (Exception e) {
                    log.error("缓存重建失败", e);
                } finally {
                    // 释放锁
                    unlock(lockKey);
                }
            });
        }

        // 6. 返回过期的缓存数据（即使过期，也能暂时提供服务）
        log.info("缓存已过期，返回过期数据");
        return cachedPage;
    }


    //方式二:
    // 使用互斥锁解决缓存击穿的缓存 查询方式
//    public <R, ID> R queryWithMutex(
//            String keyPrefix, ID id, Class<R> type, Function<ID, R> dbFallback, int time, TimeUnit unit) {
//        String key = keyPrefix + id;
//        // 1.从redis查询商铺缓存
//        String shopJson = stringRedisTemplate.opsForValue().get(key);
//        // 2.判断是否存在
//        if (StrUtil.isNotBlank(shopJson)) {
//            // 3.存在，直接返回
//            return JSONUtil.toBean(shopJson, type);
//        }
//        // 判断命中的是否是空值
//        if (shopJson != null) {
//            // 返回一个错误信息
//            return null;
//        }
//
//        // 4.实现缓存重建
//        // 4.1.获取互斥锁
//        String lockKey = LOCK_SHOP_KEY + id;
//        R r = null;
//        try {
//            boolean isLock = tryLock(lockKey);
//            // 4.2.判断是否获取成功
//            if (!isLock) {
//                // 4.3.获取锁失败，休眠并重试
//                Thread.sleep(50);
//                return queryWithMutex(keyPrefix, id, type, dbFallback, time, unit);
//            }
//            // 4.4.获取锁成功，根据id查询数据库
//            r = dbFallback.apply(id);
//            // 5.不存在，返回错误
//            if (r == null) {
//                // 将空值写入redis
//                stringRedisTemplate.opsForValue().set(key, "", CACHE_NULL_TTL, TimeUnit.MINUTES);
//                // 返回错误信息
//                return null;
//            }
//            // 6.存在，写入redis
//            this.set(key, r, time, unit);
//        } catch (InterruptedException e) {
//            throw new RuntimeException(e);
//        }finally {
//            // 7.释放锁
//            unlock(lockKey);
//        }
//        // 8.返回
//        return r;
//    }


    // 互斥锁 与 设置逻辑时间 的查询 缓存方式  所需要用到的锁的创建
    private boolean tryLock(String key) {
        Boolean flag = stringRedisTemplate.opsForValue().setIfAbsent(key, "1", 10, TimeUnit.SECONDS);
        return BooleanUtil.isTrue(flag);
    }

    private void unlock(String key) {
        stringRedisTemplate.delete(key);
    }
}
