package com.remzbl.cpictureback.controller;

import cn.hutool.core.util.RandomUtil;
import cn.hutool.core.util.StrUtil;
import cn.hutool.json.JSONUtil;
import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.remzbl.cpictureback.annotation.AuthCheck;
import com.remzbl.cpictureback.api.aliyunai.AliYunAiApi;
import com.remzbl.cpictureback.api.aliyunai.model.CreateOutPaintingTaskResponse;
import com.remzbl.cpictureback.api.aliyunai.model.GetOutPaintingTaskResponse;
import com.remzbl.cpictureback.api.imagesearch.SoImageSearchApiFacade;
import com.remzbl.cpictureback.api.imagesearch.model.SoImageSearchResult;
import com.remzbl.cpictureback.common.BaseResponse;
import com.remzbl.cpictureback.common.DeleteRequest;
import com.remzbl.cpictureback.common.ResultUtils;
import com.remzbl.cpictureback.constant.UserConstant;
import com.remzbl.cpictureback.exception.BusinessException;
import com.remzbl.cpictureback.exception.ErrorCode;
import com.remzbl.cpictureback.exception.ThrowUtils;
import com.remzbl.cpictureback.model.dto.picture.*;
import com.remzbl.cpictureback.model.entity.Picture;
import com.remzbl.cpictureback.model.entity.Space;
import com.remzbl.cpictureback.model.entity.User;
import com.remzbl.cpictureback.model.enums.PictureReviewStatusEnum;
import com.remzbl.cpictureback.model.vo.PictureTagCategory;
import com.remzbl.cpictureback.model.vo.PictureVO;
import com.remzbl.cpictureback.service.PictureService;
import com.remzbl.cpictureback.service.SpaceService;
import com.remzbl.cpictureback.service.UserService;
import com.remzbl.cpictureback.utils.cacheutil.CacheClient;
import com.remzbl.cpictureback.utils.cacheutil.CacheUtil;
import com.remzbl.cpictureback.utils.cacheutil.RedisData;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.BeanUtils;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.ValueOperations;
import org.springframework.util.DigestUtils;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;
import java.time.LocalDateTime;
import javax.annotation.Resource;
import javax.servlet.http.HttpServletRequest;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.concurrent.TimeUnit;

import static com.remzbl.cpictureback.utils.cacheutil.RedisConstants.*;

@Slf4j
@RestController
@RequestMapping("/picture")
public class PictureController {

    @Resource
    private UserService userService;

    @Resource
    private PictureService pictureService;

    @Resource
    private StringRedisTemplate stringRedisTemplate;

    @Resource
    private SpaceService spaceService;

    @Resource
    private CacheUtil cacheUtil;

    @Resource
    private CacheClient cacheClient;

    @Resource
    private AliYunAiApi aliYunAiApi;

    /**
     * 上传图片（可重新上传）
     */
    @PostMapping("/upload")
    //@AuthCheck(mustRole = UserConstant.ADMIN_ROLE)
    public BaseResponse<PictureVO> uploadPicture(
            // @RequestPart 分别处理文件和 JSON 数据 --- 即将文件绑定为MultipartFile对象 , 将JSON数据绑定到PictureUploadRequest对象
            @RequestPart("file") MultipartFile multipartFile,
            PictureUploadRequest pictureUploadRequest) {
        User loginUser = userService.getLoginUser();
        PictureVO pictureVO = pictureService.uploadPicture(multipartFile, pictureUploadRequest, loginUser);
        return ResultUtils.success(pictureVO);
    }

    /**
     * 通过 URL 上传图片（可重新上传）
     */
    @PostMapping("/upload/url")
    public BaseResponse<PictureVO> uploadPictureByUrl(
            // @RequestBody 将整个请求体绑定到PictureUploadRequest对象。
            @RequestBody PictureUploadRequest pictureUploadRequest) {
        User loginUser = userService.getLoginUser();
        String fileUrl = pictureUploadRequest.getFileUrl();
        PictureVO pictureVO = pictureService.uploadPicture(fileUrl, pictureUploadRequest, loginUser);
        return ResultUtils.success(pictureVO);
    }


    /**
     * 管理员批量抓图
     */
    @PostMapping("/upload/batch")
    @AuthCheck(mustRole = UserConstant.ADMIN_ROLE)
    public BaseResponse<Integer> uploadPictureByBatch(
            @RequestBody PictureUploadByBatchRequest pictureUploadByBatchRequest) {
        ThrowUtils.throwIf(pictureUploadByBatchRequest == null, ErrorCode.PARAMS_ERROR);
        User loginUser = userService.getLoginUser();
        int uploadCount = pictureService.uploadPictureByBatch(pictureUploadByBatchRequest, loginUser);
        return ResultUtils.success(uploadCount);
    }


    @PostMapping("/delete")
    public BaseResponse<Boolean> deletePicture(@RequestBody DeleteRequest deleteRequest) {
        if (deleteRequest == null || deleteRequest.getId() <= 0) {
            throw new BusinessException(ErrorCode.PARAMS_ERROR);
        }
        User loginUser = userService.getLoginUser();
        int current = deleteRequest.getCurrent();
        pictureService.deletePicture(deleteRequest.getId(), loginUser , current);



        return ResultUtils.success(true);
    }

    /**
     * 编辑图片（给用户使用）
     */
    @PostMapping("/edit")
    public BaseResponse<Boolean> editPicture(@RequestBody PictureEditRequest pictureEditRequest) {
        if (pictureEditRequest == null || pictureEditRequest.getId() <= 0) {
            throw new BusinessException(ErrorCode.PARAMS_ERROR);
        }

        log.info("编辑图片信息：" + pictureEditRequest);
        log.info("编辑图片信息：" + pictureEditRequest);log.info("编辑图片信息：" + pictureEditRequest);
        log.info("编辑图片信息：" + pictureEditRequest);
        log.info("编辑图片信息：" + pictureEditRequest);
        log.info("编辑图片信息：" + pictureEditRequest);

        User loginUser = userService.getLoginUser();
        pictureService.editPicture(pictureEditRequest, loginUser);
        return ResultUtils.success(true);

    }


    /**
     * 更新图片（仅管理员可用）
     */
    @PostMapping("/update")
    @AuthCheck(mustRole = UserConstant.ADMIN_ROLE)
    public BaseResponse<Boolean> updatePicture(@RequestBody PictureUpdateRequest pictureUpdateRequest) {
        if (pictureUpdateRequest == null || pictureUpdateRequest.getId() <= 0) {
            throw new BusinessException(ErrorCode.PARAMS_ERROR);
        }
        // 将实体类和 DTO 进行转换
        Picture picture = new Picture();
        BeanUtils.copyProperties(pictureUpdateRequest, picture);
        // 注意将 list 转为 string
        picture.setTags(JSONUtil.toJsonStr(pictureUpdateRequest.getTags()));
        // 数据校验
        pictureService.validPicture(picture);
        // 判断是否存在
        long id = pictureUpdateRequest.getId();
        Picture oldPicture = pictureService.getById(id);
        ThrowUtils.throwIf(oldPicture == null, ErrorCode.NOT_FOUND_ERROR);

        // 操作数据库之前 : 补充审核参数
        User loginUser = userService.getLoginUser();
        pictureService.fillReviewParams(picture, loginUser);


        // 操作数据库
        boolean result = pictureService.updateById(picture);
        ThrowUtils.throwIf(!result, ErrorCode.OPERATION_ERROR);
        return ResultUtils.success(true);
    }

    // 查

    /**
     * 根据 id 获取图片（仅管理员可用）
     */
    @GetMapping("/get")
    @AuthCheck(mustRole = UserConstant.ADMIN_ROLE)
    public BaseResponse<Picture> getPictureById(long id) {
        ThrowUtils.throwIf(id <= 0, ErrorCode.PARAMS_ERROR);
        // 查询数据库
        Picture picture = pictureService.getById(id);
        ThrowUtils.throwIf(picture == null, ErrorCode.NOT_FOUND_ERROR);
        // 空间权限校验 管理员无需校验 即 管理员有权去查看其他人私有图库
//        Long spaceId = picture.getSpaceId();
//        if (spaceId != null) {
//            User loginUser = userService.getLoginUser();
//            pictureService.checkPictureAuth(loginUser, picture);
//        }

        // 获取封装类
        return ResultUtils.success(picture);
    }

    /**
     * 根据 id 获取图片（封装类）
     */
    @GetMapping("/get/vo")
    public BaseResponse<PictureVO> getPictureVOById(long id) {
        ThrowUtils.throwIf(id <= 0, ErrorCode.PARAMS_ERROR);
        // 查询数据库
        Picture picture = pictureService.getById(id);
        ThrowUtils.throwIf(picture == null, ErrorCode.NOT_FOUND_ERROR);
        // 空间权限校验
        Long spaceId = picture.getSpaceId();
        if (spaceId != null) {
            User loginUser = userService.getLoginUser();
            pictureService.checkPictureAuth(loginUser, picture);
        }
        // 获取封装类
        return ResultUtils.success(pictureService.getPictureVO(picture));
    }

    /**
     * 管理员分页查询图片原信息 : 获取图片列表（仅管理员可用）
     * 无需限制爬虫 管理员不做限制 想干嘛就干嘛
     */
    @PostMapping("/list/page")
    @AuthCheck(mustRole = UserConstant.ADMIN_ROLE)
    public BaseResponse<Page<Picture>> listPictureByPage(@RequestBody PictureQueryRequest pictureQueryRequest) {
        long current = pictureQueryRequest.getCurrent();
        long size = pictureQueryRequest.getPageSize();
        // 查询数据库
        Page<Picture> picturePage = pictureService.page(new Page<>(current, size),
                pictureService.getQueryWrapper(pictureQueryRequest));
        return ResultUtils.success(picturePage);
    }

    /**
     * 普通用户分页查询图片脱敏信息 : 分页获取图片列表（封装类）
     * 限制普通用户的图片获取
     */
    @PostMapping("/list/page/vo")
    public BaseResponse<Page<PictureVO>> listPictureVOByPage(@RequestBody PictureQueryRequest pictureQueryRequest) {
        long current = pictureQueryRequest.getCurrent();
        long size = pictureQueryRequest.getPageSize();
        // 限制爬虫
        ThrowUtils.throwIf(size > 30, ErrorCode.PARAMS_ERROR);

        // 空间权限校验
        Long spaceId = pictureQueryRequest.getSpaceId();
        if (spaceId == null) {
            // 公开图库
            // 普通用户默认只能看到审核通过的数据
            pictureQueryRequest.setReviewStatus(PictureReviewStatusEnum.PASS.getValue());
            pictureQueryRequest.setNullSpaceId(true);
        } else {
            // 私有空间
            User loginUser = userService.getLoginUser();
            Space space = spaceService.getById(spaceId);
            ThrowUtils.throwIf(space == null, ErrorCode.NOT_FOUND_ERROR, "空间不存在");
            if (!loginUser.getId().equals(space.getUserId())) {
                throw new BusinessException(ErrorCode.NO_AUTH_ERROR, "没有空间权限");
            }
        }
        // 查询数据库
        Page<Picture> picturePage = pictureService.page(new Page<>(current, size),
                pictureService.getQueryWrapper(pictureQueryRequest));
        // 获取封装类
        return ResultUtils.success(pictureService.getPictureVOPage(picturePage));
    }






    /**
     * 二级缓存 主页缓存获取图片
     * @param pictureQueryRequest
     * @return BaseResponse<Page<PictureVO>>
     */

    @PostMapping("/list/page/vo/cache")
    public BaseResponse<Page<PictureVO>> listPictureVOByPageWithCache(@RequestBody PictureQueryRequest pictureQueryRequest) {
        long current = pictureQueryRequest.getCurrent();
        long size = pictureQueryRequest.getPageSize();
        // 构建缓存 key 的参数 :
        String category = pictureQueryRequest.getCategory();
        List<String> tags = pictureQueryRequest.getTags();

        // 限制爬虫
        ThrowUtils.throwIf(size > 20, ErrorCode.PARAMS_ERROR);
        // 设置查询条件
        // 空间权限校验 即校验查询图片是属于哪个空间
        Long spaceId = pictureQueryRequest.getSpaceId();
        String cacheKey = null;



        if (spaceId == null) {
            // 公开图库
            // 普通用户默认只能看到审核通过的数据
            pictureQueryRequest.setReviewStatus(PictureReviewStatusEnum.REVIEWEDWAITTOPASS.getValue()); //审核信息
            pictureQueryRequest.setNullSpaceId(true);								      //无空间id的图片--公共图库

            log.info("category:{}", category);
            log.info("tags:{}", tags);

            // 利用上面的两个参数 生成缓存key
            if(pictureQueryRequest.getCurrent()==1 && category==null && tags.toArray().length==0){
                // 公共图库 第一页 : 热点数据     上传图片 + 修改第一页的数据进行同步更新
                // 启动无缓存时 先预热缓存
                cacheKey = PUBLIC_MAIN_PAGE + ":" + 1;
                if(stringRedisTemplate.opsForValue().get(cacheKey)==null){
                    // 第一次访问 : 缓存预热
                    log.info("第一次访问 : 缓存预热");
                    QueryWrapper<Picture> queryWrapper = pictureService.getQueryWrapper(pictureQueryRequest);
                    Page<Picture> picturePage = pictureService.page( new Page<>(current, size),queryWrapper);
                    Page<PictureVO> pictureVOPage = pictureService.getPictureVOPage(picturePage);
                    RedisData redisData = new RedisData();
                    redisData.setData(pictureVOPage);
                    //redisData.setExpireTime(LocalDateTime.now().plusSeconds(CACHE_MAINTEST_TTL));
                    redisData.setExpireTime(LocalDateTime.now().plusMinutes(CACHE_MAINTEST_TTL));
                    stringRedisTemplate.opsForValue().set(cacheKey, JSONUtil.toJsonStr(redisData));

                    // 本地缓存预热
                    cacheClient.getLocalCache().put(cacheKey, JSONUtil.toJsonStr(pictureVOPage));

                }

                // 1. 先从本地缓存中查询
                String cachedValue = cacheClient.getLocalCache().getIfPresent(cacheKey);
                if(cachedValue!=null){
                    Page<PictureVO> cachedPage = JSONUtil.toBean(cachedValue, Page.class);
                    return  ResultUtils.success(cachedPage);
                }

                // 2. 再从redis缓存中查询 , 逻辑过期的方式来查询缓存 - 可解决缓存击穿
                Page<PictureVO> pictureVOPage = cacheClient.queryWithLogicalExpire
                        (cacheKey, pictureQueryRequest, CACHE_MAIN_TTL, TimeUnit.MINUTES,
                                req -> pictureService.getQueryWrapper((PictureQueryRequest) req),
                                // 分页查询方法（兼容MyBatis-Plus的Service实现）
                                (page, wrapper) -> pictureService.page(page, wrapper),
                                page -> pictureService.getPictureVOPage(page)

                        );
                log.info("返回的分页数据:{}", pictureVOPage);
                return ResultUtils.success(pictureVOPage);

            }else {
                // 分类 + 标签缓存 : 非热点数据,用解决缓存穿透的缓存查询方式
                // searchText模糊查询 : 非热点数据,用解决缓存穿透的缓存查询方式
                // 使用全条件的缓存key
                String queryCondition = JSONUtil.toJsonStr(pictureQueryRequest);
                String hashKey = DigestUtils.md5DigestAsHex(queryCondition.getBytes());
                cacheKey= PUBLIC_PRE_KEY  + ":" + hashKey;
                // 5 - 10 分钟随机过期，防止雪崩
                int cacheExpireTime = 300 +  RandomUtil.randomInt(0, 300);

                // 解决缓存穿透的缓存查询
                Page<PictureVO> pictureVOPage = cacheClient.queryWithPassThrough
                        (cacheKey, pictureQueryRequest, cacheExpireTime, TimeUnit.MINUTES,
                                req -> pictureService.getQueryWrapper((PictureQueryRequest) req),
                                // 分页查询方法（兼容MyBatis-Plus的Service实现）
                                (page, wrapper) -> pictureService.page(page, wrapper),
                                page -> pictureService.getPictureVOPage(page)

                        );
                log.info("返回的分页数据:{}", pictureVOPage);

                return ResultUtils.success(pictureVOPage);


            }


        } else {
            // 私有空间
            User loginUser = userService.getLoginUser();
            Long userId = loginUser.getId();
            Space space = spaceService.getById(spaceId);
            ThrowUtils.throwIf(space == null, ErrorCode.NOT_FOUND_ERROR, "空间不存在");
            // 开放阶段暂时不做私有图库的权限控制 , 因为拦截器方面的问题
            // 如果拦截器对这个接口进行拦截 , 未登录用户就无法获取公共图库的预览信息
            // 如果拦截器对这个接口进行开放 , 登录用户在访问私有图库时就无法获得用户信息 ,导致没有空间权限
            // 最好的解决方案就是公共图库 与 私有图库的图片查询接口 分开
            // 现已解决空间权限问题 : 详细看登录拦截器
            if (!userId.equals(space.getUserId())) {
                throw new BusinessException(ErrorCode.NO_AUTH_ERROR, "没有空间权限");
            }

            // 使用全条件的缓存key
            String queryCondition = JSONUtil.toJsonStr(pictureQueryRequest);
            String hashKey = DigestUtils.md5DigestAsHex(queryCondition.getBytes());
            cacheKey= PRIVATE_PRE_KEY + spaceId + ":" + hashKey;
                // 5 - 10 分钟随机过期，防止雪崩
            int cacheExpireTime = 300 +  RandomUtil.randomInt(0, 300);

            // 解决缓存穿透的缓存查询
            Page<PictureVO> pictureVOPage = cacheClient.queryWithPassThrough
                    (cacheKey, pictureQueryRequest, cacheExpireTime, TimeUnit.MINUTES,
                            req -> pictureService.getQueryWrapper((PictureQueryRequest) req),
                            // 分页查询方法（兼容MyBatis-Plus的Service实现）
                            (page, wrapper) -> pictureService.page(page, wrapper),
                            page -> pictureService.getPictureVOPage(page)

                    );
            log.info("返回的分页数据:{}", pictureVOPage);

            return ResultUtils.success(pictureVOPage);

        }

    }



    /**
     * 二级缓存 私有图库缓存获取图片
     * @param pictureQueryRequest
     * @return BaseResponse<Page<PictureVO>>
     */

    @PostMapping("/list/page/vo/homecache")
    public BaseResponse<Page<PictureVO>> listPictureVOByPageWithHomeCache(@RequestBody PictureQueryRequest pictureQueryRequest) {
        long current = pictureQueryRequest.getCurrent();
        long size = pictureQueryRequest.getPageSize();
        // 限制爬虫
        ThrowUtils.throwIf(size > 20, ErrorCode.PARAMS_ERROR);
        // 设置查询条件
        // 空间权限校验 即校验查询图片是属于哪个空间
        String cacheKey = null;
            // 公开图库
            // 普通用户默认只能看到审核通过的数据
        pictureQueryRequest.setReviewStatus(PictureReviewStatusEnum.PASS.getValue());
        pictureQueryRequest.setNullSpaceId(true);

        // 1. 先从本地缓存中查询
        String cachedValue = cacheUtil.getLocalCache().getIfPresent(cacheKey);
        // 断点日志测试
        log.info("cachedValue:{}",cachedValue);
        if (cachedValue!= null) {
            // 如果缓存命中，返回结果
            Page<PictureVO> cachedPage = JSONUtil.toBean(cachedValue, Page.class);
            return ResultUtils.success(cachedPage);
        }

        // 本地缓存未命中  查询Redis分布式缓存
        ValueOperations<String, String> valueOps = stringRedisTemplate.opsForValue(); //构造操作redis数据库的对象
        cachedValue = valueOps.get(cacheKey); //redis查询 redisKey键所对应的值
        // 断点日志测试
        log.info("cachedValue:{}",cachedValue);
        log.info("cachedValue:{}",cachedValue);
        log.info("cachedValue:{}",cachedValue);
        log.info("cachedValue:{}",cachedValue);

        if (cachedValue != null) {
            // 如果缓存命中，返回结果
            Page<PictureVO> cachedPage = JSONUtil.toBean(cachedValue, Page.class); //将json字符串反序列化转化为Java对象 此处指定了java对象为Page<PictureVO>
            return ResultUtils.success(cachedPage);
        }

        // 缓存未命中 就 查询数据库
        // 创建原信息分页对象
        Page<Picture> picturePage = pictureService.page(new Page<>(current, size),
                pictureService.getQueryWrapper(pictureQueryRequest));
        // 获取原信息分页对象
        Page<PictureVO> pictureVOPage = pictureService.getPictureVOPage(picturePage);

        // 存入 Redis 缓存
        String cacheValue = JSONUtil.toJsonStr(pictureVOPage); //将Page<PictureVO>脱敏分页对象序列化为json字符串
        // 5 - 10 分钟随机过期，防止雪崩
        int cacheExpireTime = 300 +  RandomUtil.randomInt(0, 300);
        valueOps.set(cacheKey, cacheValue, cacheExpireTime, TimeUnit.SECONDS);

        // 写入本地缓存
        cacheUtil.getLocalCache().put(cacheKey, cacheValue);
        // 返回结果
        return ResultUtils.success(pictureVOPage);

    }








    @GetMapping("/tag_category")
    public BaseResponse<PictureTagCategory> listPictureTagCategory() {
        PictureTagCategory pictureTagCategory = new PictureTagCategory();
        List<String> tagList = Arrays.asList("热门", "生活",  "背景", "动漫", "创意" );
        List<String> categoryList = Arrays.asList("模板", "电商", "表情包", "素材", "海报", "游戏");
        pictureTagCategory.setTagList(tagList);
        pictureTagCategory.setCategoryList(categoryList);
        return ResultUtils.success(pictureTagCategory);
    }



    /**
     * 审核图片
     */
    @PostMapping("/review")
    @AuthCheck(mustRole = UserConstant.ADMIN_ROLE)
    public BaseResponse<Boolean> doPictureReview(@RequestBody PictureReviewRequest pictureReviewRequest) {
        ThrowUtils.throwIf(pictureReviewRequest == null, ErrorCode.PARAMS_ERROR);
        User loginUser = userService.getLoginUser();
        pictureService.doPictureReview(pictureReviewRequest, loginUser);
        return ResultUtils.success(true);
    }

    /**
     * 点击触发已审核待开放的图片 开放
     */
    @PostMapping("/open")
    @AuthCheck(mustRole = UserConstant.ADMIN_ROLE)
    public BaseResponse<Boolean> openPicture() {

        cacheClient.releasePassPictureAndUpdateCache();
        return ResultUtils.success(true);

    }




    /**
     * 以图搜图
     */
    @PostMapping("/search/picture")
    public BaseResponse<List<SoImageSearchResult>> searchPictureByPictureIsSo(@RequestBody SearchPictureByPictureRequest searchPictureByPictureRequest) {
        ThrowUtils.throwIf(searchPictureByPictureRequest == null, ErrorCode.PARAMS_ERROR);
        Long pictureId = searchPictureByPictureRequest.getPictureId();
        ThrowUtils.throwIf(pictureId == null || pictureId <= 0, ErrorCode.PARAMS_ERROR);
        Picture oldPicture = pictureService.getById(pictureId);
        ThrowUtils.throwIf(oldPicture == null, ErrorCode.NOT_FOUND_ERROR);
        List<SoImageSearchResult> resultList = new ArrayList<>();
        // 这个 start 是控制查询多少页, 每页是 20 条
        int start = 0;
        while (resultList.size() <= 50) {
            List<SoImageSearchResult> tempList = SoImageSearchApiFacade.searchImage(oldPicture.getUrl(), start);
            if (tempList.isEmpty()) {
                break;
            }
            resultList.addAll(tempList);
            start += tempList.size();
        }
        return ResultUtils.success(resultList);
    }

    // ai扩图
    /**
     * 创建 AI 扩图任务
     */
    @PostMapping("/out_painting/create_task")
    public BaseResponse<CreateOutPaintingTaskResponse> createPictureOutPaintingTask(@RequestBody CreatePictureOutPaintingTaskRequest createPictureOutPaintingTaskRequest) {
        if (createPictureOutPaintingTaskRequest == null || createPictureOutPaintingTaskRequest.getPictureId() == null) {
            throw new BusinessException(ErrorCode.PARAMS_ERROR);
        }
        User loginUser = userService.getLoginUser();
        CreateOutPaintingTaskResponse response = pictureService.createPictureOutPaintingTask(createPictureOutPaintingTaskRequest, loginUser);
        return ResultUtils.success(response);
    }

    /**
     * 查询 AI 扩图任务
     */
    @GetMapping("/out_painting/get_task")
    public BaseResponse<GetOutPaintingTaskResponse> getPictureOutPaintingTask(String taskId) {
        ThrowUtils.throwIf(StrUtil.isBlank(taskId), ErrorCode.PARAMS_ERROR);
        GetOutPaintingTaskResponse task = aliYunAiApi.getOutPaintingTask(taskId);
        return ResultUtils.success(task);
    }


}
