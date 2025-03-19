package com.remzbl.cpictureback.service.impl;

import cn.hutool.core.bean.BeanUtil;
import cn.hutool.core.collection.CollUtil;
import cn.hutool.core.util.ObjUtil;
import cn.hutool.core.util.StrUtil;
import cn.hutool.json.JSONUtil;
import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.baomidou.mybatisplus.core.conditions.update.UpdateWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.qcloud.cos.COSClient;
import com.remzbl.cpictureback.api.aliyunai.AliYunAiApi;
import com.remzbl.cpictureback.api.aliyunai.model.CreateOutPaintingTaskRequest;
import com.remzbl.cpictureback.api.aliyunai.model.CreateOutPaintingTaskResponse;
import com.remzbl.cpictureback.config.CosClientConfig;
import com.remzbl.cpictureback.constant.UserConstant;
import com.remzbl.cpictureback.exception.BusinessException;
import com.remzbl.cpictureback.exception.ErrorCode;
import com.remzbl.cpictureback.exception.ThrowUtils;
import com.remzbl.cpictureback.manager.CosManager;
import com.remzbl.cpictureback.manager.FileManager;
import com.remzbl.cpictureback.manager.upload.FilePictureUpload;
import com.remzbl.cpictureback.manager.upload.PictureUploadTemplate;
import com.remzbl.cpictureback.manager.upload.UrlPictureUpload;
import com.remzbl.cpictureback.mapper.PictureMapper;
import com.remzbl.cpictureback.model.dto.file.UploadPictureResult;
import com.remzbl.cpictureback.model.dto.picture.*;
import com.remzbl.cpictureback.model.dto.user.RedisUser;
import com.remzbl.cpictureback.model.entity.Picture;
import com.remzbl.cpictureback.model.entity.Space;
import com.remzbl.cpictureback.model.entity.User;
import com.remzbl.cpictureback.model.enums.PictureReviewStatusEnum;
import com.remzbl.cpictureback.model.vo.PictureVO;
import com.remzbl.cpictureback.model.vo.UserVO;
import com.remzbl.cpictureback.service.PictureService;
import com.remzbl.cpictureback.service.SpaceService;
import com.remzbl.cpictureback.service.UserService;
import com.remzbl.cpictureback.utils.cacheutil.CacheClient;
import com.remzbl.cpictureback.utils.cacheutil.CacheUtil;
import com.remzbl.cpictureback.utils.interceptor.UserHolder;
import lombok.extern.slf4j.Slf4j;
import org.jsoup.Jsoup;
import org.jsoup.internal.StringUtil;
import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;
import org.jsoup.select.Elements;
import org.springframework.beans.BeanUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionTemplate;

import javax.annotation.Resource;
import java.io.IOException;
import java.util.*;
import java.util.stream.Collectors;

/**
 * @author remzbl
 * @description 针对表【picture(图片)】的数据库操作Service实现
 * @createDate 2024-12-11 20:45:51
 */
@Slf4j
@Service
public class PictureServiceImpl extends ServiceImpl<PictureMapper, Picture>
        implements PictureService {

    @Resource
    private FileManager fileManager;

    @Resource
    private UserService userService;

    @Resource
    private FilePictureUpload filePictureUpload;

    @Resource
    private UrlPictureUpload urlPictureUpload;

    @Resource
    private CosManager cosManager;

    @Resource
    private SpaceService spaceService;

    @Resource
    private TransactionTemplate transactionTemplate;

    @Resource
    private CacheUtil cacheService;

    @Resource
    private CacheClient cacheClient;

    @Resource
    private AliYunAiApi aliYunAiApi;
    @Autowired
    private COSClient cosClient;

    @Resource
    private CosClientConfig cosClientConfig;


    // 以下为上传的校验工具




    // 审核功能 --- 填充图片审核参数
    @Override
    public void fillReviewParams(Picture picture, User loginUser) {
        if (userService.isAdmin(loginUser)) {
            // 管理员自动过审且开放图片
            picture.setReviewStatus(PictureReviewStatusEnum.REVIEWEDWAITTOPASS.getValue());
            picture.setReviewerId(loginUser.getId());
            picture.setReviewMessage("管理员自动开放");
            picture.setReviewTime(new Date());
        } else {
            // 非管理员，无论是编辑还是创建默认都是待审核
            picture.setReviewStatus(PictureReviewStatusEnum.REVIEWING.getValue());
        }
    }


    // 私人空间功能 --- 获取图片的所属空间id 并进行空间权限校验 空间额度校验
    // 上传的图片参数中若存在所属空间参数 则此次是空间上传 需要校验空间信息
    @Override
    public Long getSpaceId(PictureUploadRequest pictureUploadRequest, User loginUser) {
        Long spaceId = pictureUploadRequest.getSpaceId();
        if (spaceId != null) {
            Space space = spaceService.getById(spaceId);
            ThrowUtils.throwIf(space == null, ErrorCode.NOT_FOUND_ERROR, "空间不存在");
            // 校验是否有空间的权限，仅空间管理员才能上传
            if (!loginUser.getId().equals(space.getUserId())) {
                throw new BusinessException(ErrorCode.NO_AUTH_ERROR, "没有空间权限");
            }
            // 校验额度
            if (space.getTotalCount() >= space.getMaxCount()) {
                throw new BusinessException(ErrorCode.OPERATION_ERROR, "空间条数不足");
            }
            if (space.getTotalSize() >= space.getMaxSize()) {
                throw new BusinessException(ErrorCode.OPERATION_ERROR, "空间大小不足");
            }
        }
        return spaceId;
    }






    /**
     * 上传图片(本地 与 url)
     */

    @Override
    public PictureVO uploadPicture(Object inputSource, PictureUploadRequest pictureUploadRequest, User loginUser) {
        // 1. 参数校验
            // (1)登录信息参数校验
        ThrowUtils.throwIf(loginUser == null, ErrorCode.NO_AUTH_ERROR);
            // 新功能 : 添加私人图库 , 则需要对空间权限进行校验
            // (2)空间参数获取并校验 : 若存在空间参数 则校验身份权限与额度 并 获取空间id
        Long spaceId = getSpaceId(pictureUploadRequest, loginUser);


        // 2.1. 业务功能实现 : 允许在上传图片后的编辑图片页面 , 更换图片再进行上传
            // (1)判断是新增还是更换图片 pictureUploadRequest为空时表示新增 pictureUploadRequest存在图片id时表示更换 , 则复用id
        Long pictureId = null;
        if (pictureUploadRequest != null) {   //这里表示 : 如果pictureUploadRequest不为空
            pictureId = pictureUploadRequest.getId();  //则表示更新图片 , 此时应复用原图片的ID 而不是再创建一个ID
        }

            // (2)如果是更换图片，需要旧图片存在校验及其所属权限校验 防止他人而且修改图片信息
            //                    以及更换图片 与 旧图片的空间一致性校验

        if (pictureId != null) {
            Picture oldPicture = this.getById(pictureId);
            ThrowUtils.throwIf(oldPicture == null, ErrorCode.NOT_FOUND_ERROR, "图片不存在");
                // 图片归属权限校验 : 仅本人或管理员可编辑
            if (!oldPicture.getUserId().equals(loginUser.getId()) && !userService.isAdmin(loginUser)) {
                throw new BusinessException(ErrorCode.NO_AUTH_ERROR);
            }
                // 校验空间是否一致
            if (spaceId == null) {
                // 如果丢失未传 spaceId，则复用原有图片的 spaceId
                if (oldPicture.getSpaceId() != null) {
                    spaceId = oldPicture.getSpaceId();
                }
            } else {
                // 传了 spaceId，必须和原图片的空间 id 一致
                if (ObjUtil.notEqual(spaceId, oldPicture.getSpaceId())) {
                    throw new BusinessException(ErrorCode.PARAMS_ERROR, "空间 id 不一致");
                }
            }
        }


        // 基于公共图片之下 按照上传用户 id 划分目录
        // 优化 : 添加私有空间 String uploadPathPrefix = String.format("public/%s", loginUser.getId());

        // 新功能 : 添加私人空间后 , 需要区分公共图片与私人图片上传路径前缀
        // 2.2. 业务功能实现 : 基于公共图片 与 私人空间 划分目录
        String uploadPathPrefix;
        if (spaceId == null) {
            // 公共图库上传前缀
            uploadPathPrefix = String.format("public/%s", loginUser.getId());
        } else {
            // 私有空间上传前缀
            uploadPathPrefix = String.format("space/%s", spaceId);
        }


        //原上传方式 (只能本地图片上传)UploadPictureResult uploadPictureResult = fileManager.uploadPicture(multipartFile, uploadPathPrefix);

        //2.3 业务功能实现 : 按上传图片方式自适应选择上传方式(向上转型) : 根据 inputSource 的类型区分上传方式
        PictureUploadTemplate pictureUploadTemplate = filePictureUpload; //默认使用本地上传方式
        if (inputSource instanceof String) { // 如果是 String 类型，则使用 url 上传方式
            pictureUploadTemplate = urlPictureUpload;
        }
        UploadPictureResult uploadPictureResult = pictureUploadTemplate.uploadPicture(inputSource, uploadPathPrefix);
        // 使用返回的封装图片结果 : 构造要入库的图片信息
        Picture picture = new Picture();
        picture.setSpaceId(spaceId); // 指定空间 id  公共图库默认为null 私 人图库若存在则为空间id
        picture.setUrl(uploadPictureResult.getUrl());
        // 新功能 : 缩略图   补充缩略图地址
        picture.setThumbnailUrl(uploadPictureResult.getThumbnailUrl());


        //picture.setName(uploadPictureResult.getPicName());//这里会进行优化
        // 支持外层传递图片名称
            // 默认使用原图上传解析后返回的名称
        String picName = uploadPictureResult.getPicName(); //默认使用解析后的图片名称
            // 如果上传时进行了图片名称的传递，则使用传递的图片名称 这里主要用于批量抓图时规定图片名称
        if (pictureUploadRequest != null && StrUtil.isNotBlank(pictureUploadRequest.getPicName())) {
            picName = pictureUploadRequest.getPicName(); //如果传递了图片名称，则使用传递的图片名称
        }

        picture.setName(picName);
        picture.setPicSize(uploadPictureResult.getPicSize());
        picture.setPicWidth(uploadPictureResult.getPicWidth());
        picture.setPicHeight(uploadPictureResult.getPicHeight());
        picture.setPicScale(uploadPictureResult.getPicScale());
        picture.setPicFormat(uploadPictureResult.getPicFormat());
        picture.setUserId(loginUser.getId());

        // 上传到数据库之前 补全图片信息
        //新功能 : 审核     补充审核参数
        if(pictureUploadRequest.getSpaceId() == null){
            this.fillReviewParams(picture, loginUser);
        }

        // 如果 pictureId 不为空，表示更新，否则是新增
        if (pictureId != null) {
            // 如果是更新，需要补充 id 和编辑时间
            picture.setId(pictureId);
            picture.setEditTime(new Date());
        }


        // 图片的数据库存储 待优化
//        boolean result = this.saveOrUpdate(picture);
//        ThrowUtils.throwIf(!result, ErrorCode.OPERATION_ERROR, "图片上传失败，数据库操作失败");


        Long finalSpaceId = spaceId;
        if(finalSpaceId == null){
            // 管理员 公共图库上传图片
            boolean result = this.saveOrUpdate(picture);
            ThrowUtils.throwIf(!result, ErrorCode.OPERATION_ERROR, "图片上传失败，数据库操作失败");

            return PictureVO.objToVo(picture);
        }else{

            // 最合理的方案 : 只用事务 , 不用锁 增加并发度 (后面无论私有图库还是公共图库所有加锁的都要去掉 )
            transactionTemplate.execute(status -> {
                // 数据库插入数据
                boolean result = this.saveOrUpdate(picture);
                ThrowUtils.throwIf(!result, ErrorCode.OPERATION_ERROR, "图片上传失败，数据库操作失败");
                // 同步更新空间使用额度
                if (finalSpaceId != null) {
                    // 更新空间的使用额度
                        // 使用lambda更新接口来操作数据库
                    boolean update = spaceService.lambdaUpdate()
                            .eq(Space::getId, finalSpaceId)
                            .setSql("totalSize = totalSize + " + picture.getPicSize())
                            .setSql("totalCount = totalCount + 1")
                            .update();
                    // 其他操作数据库的MyBatisPlus 方法
                    // 使用 更新构造器 + 空间业务层中的接口  来操作数据库
//                    UpdateWrapper<Space> updateWrapper = new UpdateWrapper<>();
//                    updateWrapper.setSql("totalSize = totalSize + " + picture.getPicSize())
//                                .setSql("totalCount = totalCount + 1")
//                                .eq("id", finalSpaceId);
//                    spaceService.update(updateWrapper);
                    // 使用 lambda更新构造器 + 空间业务层中的接口  来操作数据库
//                    LambdaUpdateWrapper<Space> lambdaUpdateWrapper = new LambdaUpdateWrapper<>();
//                    lambdaUpdateWrapper.setSql("totalSize = totalSize + " + picture.getPicSize())
//                                        .setSql("totalCount = totalCount + 1")
//                                        .eq(Space::getId, finalSpaceId);
//                    spaceService.update(lambdaUpdateWrapper);

                    ThrowUtils.throwIf(!update, ErrorCode.OPERATION_ERROR, "额度更新失败");
                }
                return picture;
            });

            // 误区 : 认为锁是实现事务原子性的 ,( 事务本身是原子性的 )
            //优化方向 : 开启事务
//            synchronized (uploadLock){
//                transactionTemplate.execute(status -> {
//                    // 数据库插入数据
//                    boolean result = this.saveOrUpdate(picture);
//                    ThrowUtils.throwIf(!result, ErrorCode.OPERATION_ERROR, "图片上传失败，数据库操作失败");
//                    // 同步更新空间使用额度
//                    if (finalSpaceId != null) {
//                        // 更新空间的使用额度
//                        boolean update = spaceService.lambdaUpdate()
//                                .eq(Space::getId, finalSpaceId)
//                                .setSql("totalSize = totalSize + " + picture.getPicSize())
//                                .setSql("totalCount = totalCount + 1")
//                                .update();
//                        ThrowUtils.throwIf(!update, ErrorCode.OPERATION_ERROR, "额度更新失败");
//                    }
//                    return picture;
//                });
//            }
                // 同步删除缓存 其实并不需要 , 可以在上传图片后的编辑操作后删除缓存
                // 删除私有图库的缓存
//                if(finalSpaceId!=null){
//                    cacheClient.delPrivateCache(finalSpaceId);
//                }


        }
        return PictureVO.objToVo(picture);
    }




    /**
     * 批量上传图片
     */

    @Override
    public Integer uploadPictureByBatch(PictureUploadByBatchRequest pictureUploadByBatchRequest, User loginUser) {
        // 校验参数
        String searchText = pictureUploadByBatchRequest.getSearchText();
        Integer count = pictureUploadByBatchRequest.getCount();
        ThrowUtils.throwIf(count > 30, ErrorCode.PARAMS_ERROR, "最多 30 条");
        // 名称前缀默认等于搜索关键词
        String namePrefix = pictureUploadByBatchRequest.getNamePrefix();
        if (StrUtil.isBlank(namePrefix)) {
            namePrefix = searchText;
        }
        // 抓取内容
        String fetchUrl = String.format("https://cn.bing.com/images/async?q=%s&mmasync=1", searchText);
        Document document;
        try {
            document = Jsoup.connect(fetchUrl).get();
        } catch (IOException e) {
            log.error("获取页面失败", e);
            throw new BusinessException(ErrorCode.OPERATION_ERROR, "获取页面失败");
        }
        // 解析内容
        Element div = document.getElementsByClass("dgControl").first();
        if (ObjUtil.isEmpty(div)) {
            throw new BusinessException(ErrorCode.OPERATION_ERROR, "获取元素失败");
        }
        // 就是将所有的img标签中的class为mimg的元素，封装成一个集合
        Elements imgElementList = div.select("img.mimg"); // 指的是img标签 中的 class为mimg

        // 遍历元素，依次处理上传图片
        int uploadCount = 0;
        for (Element imgElement : imgElementList) {
            // 获取键为src的属性值
            String fileUrl = imgElement.attr("src");
            if (StrUtil.isBlank(fileUrl)) {
                log.info("当前链接为空，已跳过：{}", fileUrl);
                continue;
            }
            // 处理图片的地址，防止转义或者和对象存储冲突的问题
            // xxxxx.png?xxxxxxxxxxx，应该只保留 xxxxx.png
            int questionMarkIndex = fileUrl.indexOf("?");
            if (questionMarkIndex > -1) {
                fileUrl = fileUrl.substring(0, questionMarkIndex);
            }
            // 上传图片
            PictureUploadRequest pictureUploadRequest = new PictureUploadRequest();
            pictureUploadRequest.setFileUrl(fileUrl);

            if (StrUtil.isNotBlank(namePrefix)) {
                // 设置图片名称，序号连续递增
                pictureUploadRequest.setPicName(namePrefix + (uploadCount + 1));
            }
            try {
                PictureVO pictureVO = this.uploadPicture(fileUrl, pictureUploadRequest, loginUser);
                log.info("图片上传成功，id = {}", pictureVO.getId());
                uploadCount++;
            } catch (Exception e) {
                log.error("图片上传失败", e);
                continue;
            }
            if (uploadCount >= count) {
                break;
            }
        }
        // 清理缓存 : 删除所有缓存 包括热点缓存
        // 清除除热点图片外的使用缓存
        cacheClient.delPublicCache();
        // 清理热点缓存
        cacheClient.delPublicMainCache();

        //cacheService.cleanAllCache();
        return uploadCount;
    }


    // 删除工具 对象存储异步清理图片文件()
    @Async
    @Override
    public void clearPictureFile(Picture oldPicture) {
        // 判断改图片是否被多条记录使用
        String pictureUrl = oldPicture.getUrl();

        long count = this.lambdaQuery()
                .eq(Picture::getUrl, pictureUrl)
                .count();
        // 有不止一条记录用到了该图片，不清理
        if (count > 1) {
            return;
        }

        //String BUCKET = "remzbl-1340049053";
        //String REGION = "ap-shanghai";
        String BUCKET = cosClientConfig.getBucket();
        String REGION = cosClientConfig.getRegion();
        String urlprefix = "https://" + BUCKET + ".cos." + REGION + ".myqcloud.com/";

        String urlKey = pictureUrl.replace(urlprefix, "");


        // 删除压缩图片 (数据库中默认存储的是压缩图 .webp)
        cosManager.deleteObject(urlKey);

        // 删除原图片
        String originKey1 = urlKey.replace(".webp", ".png");
        String originKey2 = urlKey.replace(".webp", ".jpg");
        cosManager.deleteObject(originKey1);
        cosManager.deleteObject(originKey2);


        // 删除缩略图
        String thumbnailUrl = oldPicture.getThumbnailUrl();
        String thumbnailurlkey = thumbnailUrl.replace(urlprefix, "");
        if (StrUtil.isNotBlank(thumbnailurlkey)) {
            cosManager.deleteObject(thumbnailurlkey);
        }
    }

    // 删除 与 编辑 的 权限验证
    @Override
    public void checkPictureAuth(User loginUser, Picture picture) {
        Long spaceId = picture.getSpaceId();
        if (spaceId == null) {
            // 公共图库，仅本人或管理员可操作
            if (!picture.getUserId().equals(loginUser.getId()) && !userService.isAdmin(loginUser)) {
                throw new BusinessException(ErrorCode.NO_AUTH_ERROR);
            }
        } else {
            // 私有空间，仅空间管理员可操作
            if (!picture.getUserId().equals(loginUser.getId())) {
                throw new BusinessException(ErrorCode.NO_AUTH_ERROR);
            }
        }
    }

    /**
     * 删除图片
     */
    @Override
    public void deletePicture(Long pictureId, User loginUser , int current) {

        //Long userId = loginUser.getId();
        //String deleteLock = String.valueOf(userId).intern();

        ThrowUtils.throwIf(pictureId <= 0, ErrorCode.PARAMS_ERROR);
        ThrowUtils.throwIf(loginUser == null, ErrorCode.NO_AUTH_ERROR);
        // 判断是否存在
        Picture oldPicture = this.getById(pictureId);
        ThrowUtils.throwIf(oldPicture == null, ErrorCode.NOT_FOUND_ERROR);
        // 校验权限
        checkPictureAuth(loginUser, oldPicture);

        // 操作数据库
        Long spaceId = oldPicture.getSpaceId();
        if (spaceId == null){
            // 公共图库处理
            if(current==1){
                // 第一页已有图片的编辑图片 : 需要同步更新第一页缓存
                log.info("已有第一页的热点图片时的编辑操作, 同步更新");
                // 操作数据库
                boolean result = this.removeById(pictureId);
                ThrowUtils.throwIf(!result, ErrorCode.OPERATION_ERROR);
                cacheClient.updateCacheAfterDelete(pictureId);
            }else{
                // 其他方面的删除图片 : 清理缓存
                log.info("非热点图片的编辑操作");
                // 操作数据库
                boolean result = this.removeById(pictureId);
                ThrowUtils.throwIf(!result, ErrorCode.OPERATION_ERROR);
                cacheClient.delPublicCache();
            }
        }else{
            // 私有图库处理
            // 操作数据库的优化方法  事务
            // 开启事务
            transactionTemplate.execute(status -> {
                // 操作数据库
                boolean result = this.removeById(pictureId);
                ThrowUtils.throwIf(!result, ErrorCode.OPERATION_ERROR);
                // 更新空间的使用额度，释放额度
                boolean update = spaceService.lambdaUpdate()
                        .eq(Space::getId, oldPicture.getSpaceId())
                        .setSql("totalSize = totalSize - " + oldPicture.getPicSize())
                        .setSql("totalCount = totalCount - 1")
                        .update();
                ThrowUtils.throwIf(!update, ErrorCode.OPERATION_ERROR, "额度更新失败");
                return true;
            });
            // 事务执行完成后清理缓存 , 保证数据库与缓存的一致性
            cacheClient.delPrivateCache(spaceId);

        }
        // 异步清理文件
        // todo 消息队列实现异步清理
        this.clearPictureFile(oldPicture);
    }



    // 编辑图片时的初步校验 这里的图片文件校验是为了给更新图片(用户以及管理员进行图片的更新)进行内容合理性校验
    @Override
    public void validPicture(Picture picture) {
        ThrowUtils.throwIf(picture == null, ErrorCode.PARAMS_ERROR);
        // 从对象中取值
        Long id = picture.getId();
        String url = picture.getUrl();
        String introduction = picture.getIntroduction();
        // 修改数据时，id 不能为空，有参数则校验
        ThrowUtils.throwIf(ObjUtil.isNull(id), ErrorCode.PARAMS_ERROR, "id 不能为空");
        // 如果传递了 url，才校验
        if (StrUtil.isNotBlank(url)) {
            ThrowUtils.throwIf(url.length() > 1024, ErrorCode.PARAMS_ERROR, "url 过长");
        }
        if (StrUtil.isNotBlank(introduction)) {
            ThrowUtils.throwIf(introduction.length() > 800, ErrorCode.PARAMS_ERROR, "简介过长");
        }
    }



    //改
    @Override
    public void editPicture(PictureEditRequest pictureEditRequest, User loginUser) {
        Long userId = loginUser.getId();
        String editLock = String.valueOf(userId).intern();
        // 上传后编辑图片的标志
        int uploadFile = pictureEditRequest.getUploadFile();
        int current = pictureEditRequest.getCurrent();
        // 在此处将实体类和 DTO 进行转换
        Picture picture = null;
        if(uploadFile==1){
            //上传编辑
            picture = this.getById(pictureEditRequest.getId());
        }else{
            // 已有图片编辑
            picture = new Picture();
        }
        BeanUtils.copyProperties(pictureEditRequest, picture);
        // 注意将 list 转为 string
        picture.setTags(JSONUtil.toJsonStr(pictureEditRequest.getTags()));
        // 设置编辑时间
        picture.setEditTime(new Date());
        // 数据校验
        this.validPicture(picture);

        // 判断是否存在
        long id = pictureEditRequest.getId();
        Picture oldPicture = this.getById(id);
        ThrowUtils.throwIf(oldPicture == null, ErrorCode.NOT_FOUND_ERROR);
        // 仅本人或管理员可编辑  优化
//        if (!oldPicture.getUserId().equals(loginUser.getId()) && !userService.isAdmin(loginUser)) {
//            throw new BusinessException(ErrorCode.NO_AUTH_ERROR);
//        }
        //添加空间权限验证
        checkPictureAuth(loginUser, oldPicture);

        // 补充审核参数
        if(picture.getSpaceId() == null){
            this.fillReviewParams(picture, loginUser);
        }

        if(oldPicture.getSpaceId() != null){
            // 私有图库直接清理缓存   同一个用户的多次请求也是多线程环境
            // 操作数据库
            boolean result = this.updateById(picture);
            ThrowUtils.throwIf(!result, ErrorCode.OPERATION_ERROR);
            // 同步清理缓存
            cacheClient.delPrivateCache(oldPicture.getSpaceId());
        }else if(loginUser.getUserRole().equals(UserConstant.ADMIN_ROLE)){
            // 公共图库的编辑操作
            // 公共图库的缓存更新需要加锁 , 因为公共图库的缓存是共享的 ,
            // 当多个管理员进行修改图片时 会出现缓存不一致的情况 , 所以需要串行执行
            if (uploadFile==1){
                // 上传图片时的编辑图片 需要同步更新第一页缓存
                log.info("上传图片时的编辑图片, 同步更新");
                synchronized (editLock){
                    // 操作数据库
                    boolean result = this.updateById(picture);
                    ThrowUtils.throwIf(!result, ErrorCode.OPERATION_ERROR);
                    cacheClient.updateCacheAfterUploadEdit(picture);
                }


            }else if(current==1){
                // 非上传的 对第一页已有图片的编辑 : 需要同步更新第一页缓存
                log.info("已有第一页的热点图片时的编辑操作, 同步更新");
                synchronized (editLock){
                    // 操作数据库
                    boolean result = this.updateById(picture);
                    ThrowUtils.throwIf(!result, ErrorCode.OPERATION_ERROR);
                    cacheClient.updateCacheAfterEdit(picture , pictureEditRequest);
                }

            }else{
                // 其他方面的编辑图片 : 删除缓存
                log.info("非热点图片的编辑操作");
                synchronized (editLock){
                    // 操作数据库
                    boolean result = this.updateById(picture);
                    ThrowUtils.throwIf(!result, ErrorCode.OPERATION_ERROR);
                    cacheClient.delPublicCache();
                }
            }
        }else{
            log.info("普通用户上传图片时的编辑操作, 待审核");
        }




    }

    //批量改1 : 循环遍历需要修改的图片 再进行修改
    // todo 优化方向 : 线程池
    @Override
    public void editPictureByBatch(PictureEditByBatchRequest pictureEditByBatchRequest, User loginUser) {

        log.info("开始执行批量修改图片操作{}" , pictureEditByBatchRequest);
        // 1. 获取和校验参数
        List<Long> pictureIdList = pictureEditByBatchRequest.getPictureIdList();
        Long spaceId = pictureEditByBatchRequest.getSpaceId();
        String category = pictureEditByBatchRequest.getCategory();
        List<String> tags = pictureEditByBatchRequest.getTags();
        Long userId = loginUser.getId();
        //String batchEditLock = String.valueOf(userId).intern();

        ThrowUtils.throwIf(CollUtil.isEmpty(pictureIdList), ErrorCode.PARAMS_ERROR);
        ThrowUtils.throwIf(spaceId == null, ErrorCode.PARAMS_ERROR);
        ThrowUtils.throwIf(loginUser == null, ErrorCode.NO_AUTH_ERROR);
        // 2. 校验空间权限
        Space space = spaceService.getById(spaceId);
        ThrowUtils.throwIf(space == null, ErrorCode.NOT_FOUND_ERROR, "空间不存在");
        if (!space.getUserId().equals(loginUser.getId())) {
            throw new BusinessException(ErrorCode.NO_AUTH_ERROR, "没有空间访问权限");
        }
        // 3. 查询指定图片（仅选择需要的字段）
        List<Picture> pictureList = this.lambdaQuery()
                .select(Picture::getId, Picture::getSpaceId)    //
                .eq(Picture::getSpaceId, spaceId)               //查询条件: 该空间中的图片
                .in(Picture::getId, pictureIdList)              //查询范围: 批量修改请求中的的图片id集合
                .list();
        if (pictureList.isEmpty()) {
            return;
        }
        // 4. 更新分类和标签
        pictureList.forEach(picture -> {
            if (StrUtil.isNotBlank(category)) {
                picture.setCategory(category);
            }
            if (CollUtil.isNotEmpty(tags)) {
                picture.setTags(JSONUtil.toJsonStr(tags));
            }
        });
        // 批量重命名
        String nameRule = pictureEditByBatchRequest.getNameRule();
        fillPictureWithNameRule(pictureList, nameRule);
        // 私有图库的功能
        // 5. 操作数据库进行批量更新
        boolean result = this.updateBatchById(pictureList);
        ThrowUtils.throwIf(!result, ErrorCode.OPERATION_ERROR, "批量编辑失败");
        cacheClient.delPrivateCache(spaceId);

//同步更新废弃
//        // 批量改同步更新 完成
//        // cacheCleanService.cleanAllCache();
//
//
//
//        // 构建修改后的图片信息列表（用于更新缓存）
//        List<PictureVO> updatedPictures = pictureList.stream()
//                .map(picture -> {
//                    PictureVO vo = new PictureVO();
//                    BeanUtil.copyProperties(picture, vo);
//                    vo.setTags(JSONUtil.toList(picture.getTags(), String.class));  // 转换tags字段
//                    return vo;
//                })
//                .collect(Collectors.toList());
//
//        // 同步更新缓存
//        if (spaceId != null) {
//            cacheService.updateCacheAfterBatchEdit(pictureEditByBatchRequest, spaceId);
//        }


    }

    //批量改工具 - 名称批量修改
    /**
     * nameRule 格式：图片{序号}
     */
    private void fillPictureWithNameRule(List<Picture> pictureList, String nameRule) {
        if (StrUtil.isBlank(nameRule) || CollUtil.isEmpty(pictureList)) {
            return;
        }
        long count = 1;
        try {
            for (Picture picture : pictureList) {
                String pictureName = nameRule.replaceAll("\\{序号}", String.valueOf(count++));
                picture.setName(pictureName);
            }
        } catch (Exception e) {
            log.error("名称解析错误", e);
            throw new BusinessException(ErrorCode.OPERATION_ERROR, "名称解析错误");
        }
    }



    // 查

    // 这里是页面分页显示图片信息(脱敏信息)第一步 : 原图片信息脱敏转化 + 关联图片上传用户的脱敏信息
    @Override
    public PictureVO getPictureVO(Picture picture) {
        // 对象转封装类
        PictureVO pictureVO = PictureVO.objToVo(picture);
        // 关联查询用户信息
        Long userId = picture.getUserId();
        if (userId != null && userId > 0) {
            User user = userService.getById(userId);
            UserVO userVO = userService.getUserVO(user);
            pictureVO.setUser(userVO);
        }
        return pictureVO;
    }

    // 分页查询的返回信息就是Page对象
    // 图片管理页面的图片查询也需要用到这里的方法
    //这里是页面分页显示图片信息(脱敏信息)第二步 : 构造查询构造器 --- 涉及大量的查询条件 构造这些查询条件比较复杂 所以在业务层封装一个方法提供给控制层使用
    @Override
    public QueryWrapper<Picture> getQueryWrapper(PictureQueryRequest pictureQueryRequest) {
        QueryWrapper<Picture> queryWrapper = new QueryWrapper<>();
        if (pictureQueryRequest == null) {
            return queryWrapper;
        }
        // 基础图片信息
        Long id = pictureQueryRequest.getId();
        String name = pictureQueryRequest.getName();
        String introduction = pictureQueryRequest.getIntroduction();
        String category = pictureQueryRequest.getCategory();
        List<String> tags = pictureQueryRequest.getTags();
        Long picSize = pictureQueryRequest.getPicSize();
        Integer picWidth = pictureQueryRequest.getPicWidth();
        Integer picHeight = pictureQueryRequest.getPicHeight();
        Double picScale = pictureQueryRequest.getPicScale();
        String picFormat = pictureQueryRequest.getPicFormat();
        String searchText = pictureQueryRequest.getSearchText();
        Long userId = pictureQueryRequest.getUserId();
        // 排序
        String sortField = pictureQueryRequest.getSortField();
        String sortOrder = pictureQueryRequest.getSortOrder();
        //审核信息提取
        Integer reviewStatus = pictureQueryRequest.getReviewStatus();
        String reviewMessage = pictureQueryRequest.getReviewMessage();
        Long reviewerId = pictureQueryRequest.getReviewerId();
        //空间信息提取
        Long spaceId = pictureQueryRequest.getSpaceId();
        boolean nullSpaceId = pictureQueryRequest.isNullSpaceId();
        //时间查询条件
        Date startEditTime = pictureQueryRequest.getStartEditTime();
        Date endEditTime = pictureQueryRequest.getEndEditTime();




        queryWrapper.eq(ObjUtil.isNotEmpty(id), "id", id);
        queryWrapper.eq(ObjUtil.isNotEmpty(userId), "userId", userId);
        queryWrapper.like(StrUtil.isNotBlank(name), "name", name);
        queryWrapper.like(StrUtil.isNotBlank(introduction), "introduction", introduction);
        queryWrapper.like(StrUtil.isNotBlank(picFormat), "picFormat", picFormat);
        queryWrapper.eq(StrUtil.isNotBlank(category), "category", category);
        queryWrapper.eq(ObjUtil.isNotEmpty(picWidth), "picWidth", picWidth);
        queryWrapper.eq(ObjUtil.isNotEmpty(picHeight), "picHeight", picHeight);
        queryWrapper.eq(ObjUtil.isNotEmpty(picSize), "picSize", picSize);
        queryWrapper.eq(ObjUtil.isNotEmpty(picScale), "picScale", picScale);
        //查询构造器添加审核信息
        queryWrapper.eq(ObjUtil.isNotEmpty(reviewStatus), "reviewStatus", reviewStatus);
        queryWrapper.like(StrUtil.isNotBlank(reviewMessage), "reviewMessage", reviewMessage);
        queryWrapper.eq(ObjUtil.isNotEmpty(reviewerId), "reviewerId", reviewerId);
        //查询构造器添加空间信息信息
        queryWrapper.eq(ObjUtil.isNotEmpty(spaceId), "spaceId", spaceId);
        queryWrapper.isNull(nullSpaceId, "spaceId");
        //时间查询条件
        queryWrapper.ge(ObjUtil.isNotEmpty(startEditTime), "editTime", startEditTime);
        queryWrapper.lt(ObjUtil.isNotEmpty(endEditTime), "editTime", endEditTime);
        // 排序
        queryWrapper.orderBy(StrUtil.isNotEmpty(sortField), sortOrder.equals("ascend"), sortField);

        // 根据用户输入的 searchText 在多个字段（如 name 和 introduction）中进行模糊搜索，并将查询条件动态拼接到 QueryWrapper 中
        // 从多字段中搜索
        if (StrUtil.isNotBlank(searchText)) {
            // 需要拼接查询条件
            // and (name like "%xxx%" or introduction like "%xxx%")
            queryWrapper.and(
                    qw -> qw.like("name", searchText)
                            .or()
                            .like("introduction", searchText)
            );
        }

        // 根据用户传入的 tags 列表，在 JSON 数组字段 tags 中进行模糊查询，并将查询条件动态拼接到 QueryWrapper 中
        // JSON 数组查询
        if (CollUtil.isNotEmpty(tags)) {
            /* and (tag like "%\"Java\"%" and like "%\"Python\"%") */
            for (String tag : tags) {
                queryWrapper.like("tags", "\"" + tag + "\"");
            }
        }

        return queryWrapper;
    }




    //这里是页面分页显示图片信息(脱敏信息)第三步 : 将原图片分页对象转化为脱敏分页对象
    /**
     * 普通用户 : 获取脱敏分页对象
     * 其中处理了原信息分页对象中的数据picturePage.getxxx 1 基本分页显示信息与查询到的用户记录数--------创建了脱敏分页对象
     *                                               2 脱敏用户信息------获取并脱敏处理了查询到的用户信息
     */
    @Override
    public Page<PictureVO> getPictureVOPage(Page<Picture> picturePage) {
        // 1创建
        Page<PictureVO> pictureVOPage = new Page<>(picturePage.getCurrent(), picturePage.getSize(), picturePage.getTotal());
        // 2查询到数据进行脱敏处理
        List<Picture> pictureList = picturePage.getRecords();
        if (CollUtil.isEmpty(pictureList)) {
            return pictureVOPage;
        }
        // 对象列表 => 封装对象列表
        List<PictureVO> pictureVOList = pictureList.stream()
                .map(PictureVO::objToVo)  // 使用已有类 PictureVO 中的 objToVo方法
                .collect(Collectors.toList());

        // 	2.1特殊的 : 关联查询用户信息
        // 获取图片所对应的用户唯一的id集合(Set不允许重复元素)
        Set<Long> userIdSet = pictureList.stream().map(Picture::getUserId).collect(Collectors.toSet());
        // 1 => user1, 2 => user2
        // 虽然我们的业务中一个ID对应一个User对象 , 但是groupingBy设计的方式就是一个ID可能对应多个User对象
        // 所以我们后面需要.get(0)获取User对象
        Map<Long, List<User>> userIdUserListMap = userService.listByIds(userIdSet).stream()
                .collect(Collectors.groupingBy(User::getId));

        //  2.2 遍历脱敏图片填充用户信息
        pictureVOList.forEach(pictureVO -> {
            Long userId = pictureVO.getUserId();//图片所属id
            User user = null;
            //map中存储了id 与 User信息 的键值对 对比每一个图片id与map中的图片
            if (userIdUserListMap.containsKey(userId)) { // 若存在
                user = userIdUserListMap.get(userId).get(0); // 就将其值(User)
            }
            pictureVO.setUser(userService.getUserVO(user)); // 设置到脱敏图片信息中
        });
        // 3 将脱敏完整的图片信息填充到脱敏分页对象中
        pictureVOPage.setRecords(pictureVOList);
        return pictureVOPage;
    }



    /**
     * 审核服务
     */
    @Override
    public void doPictureReview(PictureReviewRequest pictureReviewRequest, User loginUser) {
        // 1 参数逻辑校验 : 一般第一步是参数的校验 经常进行参数逻辑的校验  审核简介 审核时间无需校验
        // (1)获取参数信息
        Long id = pictureReviewRequest.getId();// 图片id
        Integer reviewStatus = pictureReviewRequest.getReviewStatus();// 图片的审核状态
        PictureReviewStatusEnum reviewStatusEnum = PictureReviewStatusEnum.getEnumByValue(reviewStatus);// 审核状态枚举
        // (2)参数逻辑判断
                                                        // 前端传来的审核状态应是通过、拒绝，若为待审核，则抛出异常
        if (id == null || reviewStatusEnum == null || PictureReviewStatusEnum.REVIEWING.equals(reviewStatusEnum)) {
            throw new BusinessException(ErrorCode.PARAMS_ERROR);
        }

        // 2 审核图片的业务逻辑
        // (1)判断图片是否存在
        Picture oldPicture = this.getById(id);
        ThrowUtils.throwIf(oldPicture == null, ErrorCode.NOT_FOUND_ERROR);
        // (2)新的审核状态传来 还是旧状态
        if (oldPicture.getReviewStatus().equals(reviewStatus)) {
            throw new BusinessException(ErrorCode.PARAMS_ERROR, "请勿重复审核");
        }
        // (3)数据库层面更新审核图片信息
        // 更新审核状态
        Picture updatePicture = new Picture();
        BeanUtils.copyProperties(pictureReviewRequest, updatePicture);
        //审核基本信息添加 : 审核人id 与 审核时间
        updatePicture.setReviewerId(loginUser.getId());
        updatePicture.setReviewTime(new Date());

        boolean result = this.updateById(updatePicture);
        ThrowUtils.throwIf(!result, ErrorCode.OPERATION_ERROR);

    }



    // ai扩图任务创建的参数填充
    @Override
    public CreateOutPaintingTaskResponse createPictureOutPaintingTask(CreatePictureOutPaintingTaskRequest createPictureOutPaintingTaskRequest, User loginUser) {

        // 1. 获取前端基本的请求参数
            // 获取扩图的处理方式参数
        CreateOutPaintingTaskRequest.Parameters parameters = createPictureOutPaintingTaskRequest.getParameters();
            // 前端传来了图片ID , 根据图片ID获取图片信息
        Long pictureId = createPictureOutPaintingTaskRequest.getPictureId();

        // 2. 权限校验
            //图片存在校验
        Picture picture = this.getById(pictureId);
        ThrowUtils.throwIf(picture == null, ErrorCode.NOT_FOUND_ERROR, "图片不存在");
//        Picture picture = Optional.ofNullable(this.getById(pictureId))
//                .orElseThrow(() -> new BusinessException(ErrorCode.NOT_FOUND_ERROR, "图片不存在"));

            //图片权限校验
        checkPictureAuth(loginUser, picture);

        // 3. 填充向扩图api接口发送请求的参数
            // 整合前端参数
        CreateOutPaintingTaskRequest createOutPaintingTaskRequest = new CreateOutPaintingTaskRequest();
        createOutPaintingTaskRequest.setParameters(createPictureOutPaintingTaskRequest.getParameters());
            // 额外填充图片的url 即input输入参数
        CreateOutPaintingTaskRequest.Input input = new CreateOutPaintingTaskRequest.Input();
        input.setImageUrl(picture.getUrl());
        createOutPaintingTaskRequest.setInput(input);


        // 4. 向扩图创建任务提供请求参数
        return aliYunAiApi.createOutPaintingTask(createOutPaintingTaskRequest);
    }



}




