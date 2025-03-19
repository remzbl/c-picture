package com.remzbl.cpictureback.service.impl;

import cn.hutool.Hutool;
import cn.hutool.core.util.NumberUtil;
import cn.hutool.core.util.ObjUtil;
import cn.hutool.json.JSONUtil;
import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.baomidou.mybatisplus.extension.conditions.query.LambdaQueryChainWrapper;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.remzbl.cpictureback.exception.BusinessException;
import com.remzbl.cpictureback.exception.ErrorCode;
import com.remzbl.cpictureback.exception.ThrowUtils;
import com.remzbl.cpictureback.mapper.SpaceMapper;
import com.remzbl.cpictureback.model.dto.space.analyze.*;
import com.remzbl.cpictureback.model.entity.Picture;
import com.remzbl.cpictureback.model.entity.Space;
import com.remzbl.cpictureback.model.entity.User;
import com.remzbl.cpictureback.model.vo.space.analyze.*;
import com.remzbl.cpictureback.service.PictureService;
import com.remzbl.cpictureback.service.SpaceAnalyzeService;
import com.remzbl.cpictureback.service.SpaceService;
import com.remzbl.cpictureback.service.UserService;
import jdk.jshell.execution.Util;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import javax.annotation.Resource;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@Slf4j
@Service
public class SpaceAnalyzeServiceImpl extends ServiceImpl<SpaceMapper, Space>
        implements SpaceAnalyzeService {

    @Resource
    private UserService userService;

    @Resource
    private SpaceService spaceService;

    @Resource
    private PictureService pictureService;


    // 空间分析工具方法
    // region

    /**
     * 校验空间分析权限
     * @param spaceAnalyzeRequest
     * @param loginUser
     */
    private void checkSpaceAnalyzeAuth(SpaceAnalyzeRequest spaceAnalyzeRequest, User loginUser) {
        boolean queryPublic = spaceAnalyzeRequest.isQueryPublic();
        boolean queryAll = spaceAnalyzeRequest.isQueryAll();
        // 全空间分析或者公共图库权限校验：仅管理员可访问
        if (queryAll || queryPublic) {
            ThrowUtils.throwIf(!userService.isAdmin(loginUser), ErrorCode.NO_AUTH_ERROR);
        } else {
            // 分析特定空间，仅本人或管理员可以访问
            Long spaceId = spaceAnalyzeRequest.getSpaceId();
            ThrowUtils.throwIf(spaceId == null, ErrorCode.PARAMS_ERROR);
            Space space = spaceService.getById(spaceId);
            ThrowUtils.throwIf(space == null, ErrorCode.NOT_FOUND_ERROR, "空间不存在");
            spaceService.checkSpaceAuth(loginUser, space);
        }
    }



    /**
     * 根据请求对象封装查询条件
     *
     * @param spaceAnalyzeRequest
     * @param queryWrapper
     */
    private void fillAnalyzeQueryWrapper(SpaceAnalyzeRequest spaceAnalyzeRequest, QueryWrapper<Picture> queryWrapper) {
        // 全空间分析
        boolean queryAll = spaceAnalyzeRequest.isQueryAll();
        if (queryAll) {
            return;
        }
        // 公共图库
        // 公共图库的查询条件 : spaceId is null 空间id为空   全空间的话 则无需添加这个字段条件 (在上面直接返回了)
        boolean queryPublic = spaceAnalyzeRequest.isQueryPublic();
        if (queryPublic) {
            queryWrapper.isNull("spaceId");
            return;
        }
        // 分析特定空间
        Long spaceId = spaceAnalyzeRequest.getSpaceId();
        if (spaceId != null) {
            queryWrapper.eq("spaceId", spaceId);
            return;
        }
        throw new BusinessException(ErrorCode.PARAMS_ERROR, "未指定查询范围");
    }



    // endregion



    /**
     * 空间资源使用分析
     * @param spaceUsageAnalyzeRequest
     * @param loginUser
     * @return
     */
    @Override
    public SpaceUsageAnalyzeResponse getSpaceUsageAnalyze(SpaceUsageAnalyzeRequest spaceUsageAnalyzeRequest, User loginUser) {
        // 校验请求信息的内容自适应 跳转分析的方向
            //QueryAll或QueryPublic字段不空则 是 全空间或公共图库，需要从 Picture 表查询

        if (spaceUsageAnalyzeRequest.isQueryAll() || spaceUsageAnalyzeRequest.isQueryPublic()) {
            // 权限校验，仅管理员可以访问
            checkSpaceAnalyzeAuth(spaceUsageAnalyzeRequest, loginUser);
            // 统计图库的使用空间
                // 使用service接口中的lambda方法来创建构造器
            /*if(spaceUsageAnalyzeRequest.getSpaceId()!=null){
                // 全图库空间
                LambdaQueryChainWrapper<Picture> pictureLambdaQueryChainWrapper = pictureService.lambdaQuery();
                pictureLambdaQueryChainWrapper.select(Picture::getPicSize);
                List<Object> pictureObjList = pictureService.listObjs(pictureLambdaQueryChainWrapper);
            }else{

                // 公共图库
                LambdaQueryChainWrapper<Picture> pictureLambdaQueryChainWrapper1 = pictureService.lambdaQuery().isNull(Picture::getSpaceId);
                pictureLambdaQueryChainWrapper1.select(Picture::getPicSize);
                List<Object> pictureObjList = pictureService.listObjs(pictureLambdaQueryChainWrapper1);
            }*/
                // 构造查询构造器
//            QueryWrapper<Picture> queryWrapper = new QueryWrapper<>();
//            queryWrapper.select("picSize");// 在构造条件时 只查询picSize字段  如果不加这个条件，则会查询一个图片对象
//
//
//                // 补充查询范围
//            fillAnalyzeQueryWrapper(spaceUsageAnalyzeRequest, queryWrapper);
//            List<Object> pictureObjList = pictureService.listObjs(queryWrapper); //使用service接口方法
//            //List<Object> pictureObjList = pictureService.getBaseMapper().selectObjs(queryWrapper);  使用BaseMapper中的方法
//
//
//
//            // 将列表中每一个Object类型数据 映射(转化) 为long类型 并进行累加
//            long usedSize = pictureObjList.stream().mapToLong(obj -> (Long) obj).sum();
//            long usedCount = pictureObjList.size();


            QueryWrapper<Picture> queryWrapper = new QueryWrapper<>();
            queryWrapper.select("sum(picSize) as usedSize , count(*) as count");// 在构造条件时 只查询picSize字段  如果不加这个条件，则会查询一个图片对象

            // 补充查询范围
            fillAnalyzeQueryWrapper(spaceUsageAnalyzeRequest, queryWrapper);
            Map<String, Object> objects = pictureService.getMap(queryWrapper);
            //测试
            /*log.info("objects = {}" , objects);
            log.info("objects = {}" , objects);
            log.info("objects = {}" , objects);
            log.info("objects = {}" , objects);*/

            Object usedSize1 = objects.get("usedSize");
            Object usedCount1 = objects.get("count");

            // 将Object usedSize1转化为long类型
            long usedSize = NumberUtil.parseLong(usedSize1.toString());
            long usedCount = NumberUtil.parseLong(usedCount1.toString());
            //测试
            /*log.info("usedSize = {}" , usedSize);
            log.info("usedSize = {}" , usedSize);
            log.info("usedSize = {}" , usedSize);
            log.info("usedSize = {}" , usedSize);
            log.info("usedCount = {}" , usedCount);
            log.info("usedCount = {}" , usedCount);
            log.info("usedCount = {}" , usedCount);
            log.info("usedCount = {}" , usedCount);
            log.info("usedCount = {}" , usedCount);*/




            Double maxSize = 10.0 * 1024 * 1024 * 1024;
            long maxSize1 = 10 * 1024 * 1024 * 1024;

            Double sizeUsageRatio = NumberUtil.round((Double) (usedSize * 100 / maxSize) , 5).doubleValue();

            // 封装返回结果
            SpaceUsageAnalyzeResponse spaceUsageAnalyzeResponse = new SpaceUsageAnalyzeResponse();
            spaceUsageAnalyzeResponse.setUsedSize(usedSize);
            spaceUsageAnalyzeResponse.setUsedCount(usedCount);
            // 公共图库（或者全部空间）无数量和容量限制、也没有比例
            spaceUsageAnalyzeResponse.setMaxSize(maxSize1);
            spaceUsageAnalyzeResponse.setSizeUsageRatio(sizeUsageRatio);
            spaceUsageAnalyzeResponse.setMaxCount(null);
            spaceUsageAnalyzeResponse.setCountUsageRatio(null);
            return spaceUsageAnalyzeResponse;
        } else {
            // 特定空间可以直接从 Space 表查询
            Long spaceId = spaceUsageAnalyzeRequest.getSpaceId();
            ThrowUtils.throwIf(spaceId == null || spaceId <= 0, ErrorCode.PARAMS_ERROR);
            // 获取空间信息
            Space space = spaceService.getById(spaceId);
            ThrowUtils.throwIf(space == null, ErrorCode.NOT_FOUND_ERROR, "空间不存在");
            // 权限校验，仅管理员可以访问
            checkSpaceAnalyzeAuth(spaceUsageAnalyzeRequest, loginUser);
            // 封装返回结果
            SpaceUsageAnalyzeResponse spaceUsageAnalyzeResponse = new SpaceUsageAnalyzeResponse();
            spaceUsageAnalyzeResponse.setUsedSize(space.getTotalSize());
            spaceUsageAnalyzeResponse.setUsedCount(space.getTotalCount());
            spaceUsageAnalyzeResponse.setMaxSize(space.getMaxSize());
            spaceUsageAnalyzeResponse.setMaxCount(space.getMaxCount());
            // 计算比例                 // 四舍五入                                                         //保留的小数位数
            double sizeUsageRatio = NumberUtil.round(space.getTotalSize() * 100.0 / space.getMaxSize(), 2).doubleValue();
            double countUsageRatio = NumberUtil.round(space.getTotalCount() * 100.0 / space.getMaxCount(), 2).doubleValue();
            spaceUsageAnalyzeResponse.setSizeUsageRatio(sizeUsageRatio);
            spaceUsageAnalyzeResponse.setCountUsageRatio(countUsageRatio);
            return spaceUsageAnalyzeResponse;
        }
    }

    /**
     * 空间分类信息分析
     * @param spaceCategoryAnalyzeRequest
     * @param loginUser
     * @return
     */

    @Override
    public List<SpaceCategoryAnalyzeResponse> getSpaceCategoryAnalyze(SpaceCategoryAnalyzeRequest spaceCategoryAnalyzeRequest, User loginUser) {
        ThrowUtils.throwIf(spaceCategoryAnalyzeRequest == null, ErrorCode.PARAMS_ERROR);
        // 检查权限
        checkSpaceAnalyzeAuth(spaceCategoryAnalyzeRequest, loginUser);

        // 构造查询条件
        QueryWrapper<Picture> queryWrapper = new QueryWrapper<>();
        fillAnalyzeQueryWrapper(spaceCategoryAnalyzeRequest, queryWrapper);

        // 使用 MyBatis Plus 分组查询
        queryWrapper.select("category", "count(*) as count", "sum(picSize) as totalSize")
                .groupBy("category");
            // 使用Map的作用是 : 将字段名 与 值 同时查询出来并进行存储
        List<Map<String, Object>> maps = pictureService.listMaps(queryWrapper);
        // maps的内容为  maps = [{totalSize=646329, count=40}, {totalSize=22968, count=1, category=游戏}, {totalSize=149940, count=5, category=表情包}]

        List<SpaceCategoryAnalyzeResponse> collect = maps.stream().map(result -> {
                    String category = (String) result.get("category");
                    Long count = ((Number) result.get("count")).longValue();
                    Long totalSize = ((Number) result.get("totalSize")).longValue();
                    // 就是将流中的每一个元素 转化为 SpaceCategoryAnalyzeResponse对象
                    return new SpaceCategoryAnalyzeResponse(category, count, totalSize);
                    // 将每一个SpaceCategoryAnalyzeResponse对象存储到Arraylist数组中
                }).collect(Collectors.toList());

        return collect;

        // 查询并转换结果
//        return pictureService.getBaseMapper().selectMaps(queryWrapper)
//                .stream()
//                .map(result -> {
//                    String category = (String) result.get("category");
//                    Long count = ((Number) result.get("count")).longValue();
//                    Long totalSize = ((Number) result.get("totalSize")).longValue();
//                    return new SpaceCategoryAnalyzeResponse(category, count, totalSize);
//                })
//                .collect(Collectors.toList());
    }

    /**
     * 空间标签信息分析
     * @param spaceTagAnalyzeRequest
     * @param loginUser
     * @return
     */
    @Override
    public List<SpaceTagAnalyzeResponse> getSpaceTagAnalyze(SpaceTagAnalyzeRequest spaceTagAnalyzeRequest, User loginUser) {
        ThrowUtils.throwIf(spaceTagAnalyzeRequest == null, ErrorCode.PARAMS_ERROR);
        // 检查权限
        checkSpaceAnalyzeAuth(spaceTagAnalyzeRequest, loginUser);

        // 构造查询条件
        QueryWrapper<Picture> queryWrapper = new QueryWrapper<>();
        fillAnalyzeQueryWrapper(spaceTagAnalyzeRequest, queryWrapper);
        // 查询所有符合条件的标签

        queryWrapper.select("tags")
                .isNotNull("tags");


        // 列表数组中一个个元素 : 为string类型的json字符串
        List<String> tagsJsonList = pictureService.listObjs(queryWrapper)
                .stream()
                //.filter(ObjUtil::isNotNull) // 另一种方式 .filter(tagjson->ObjUtil.isNotNull(tagjson))
                .map(Object::toString) // 将查询到的json类型数据转化 为 json字符串 就是为了后面使用JSONUtil.toList将字符串直接转化为数组
                .collect(Collectors.toList());

        // 得到数据 为 动态数组 中 一个个json形式的字符串
        // 数据变化流程如下
        /*
                    每个子列表元素第一个双引号 " 中存放的是从数据库中查询到的数据 即json类型数据  "
                                第一个双引号就是map(Object::toString)的作用
                [
                    [\"动漫\", \"landscape\"],
                    [\"city\", \"architecture\"],
                    [\"people\", \"portrait\"],
                    [\"nature\", \"wildlife\"]
                ]
                                |
                                |
                                |
                                v

                [
                    "[\"动漫\", \"landscape\"]",
                    "[\"city\", \"architecture\"]",
                    "[\"people\", \"portrait\"]",
                    "[\"nature\", \"wildlife\"]"
                ]


        */


        // 解析标签并统计 : 数据处理如下
        Map<String, Long> tagCountMap = tagsJsonList.stream()
                // 将一个个json字符串列表 合并为一个 列表 , 提取为["Java", "Python"], ["Java", "PHP"] => "Java", "Python", "Java", "PHP"
                    // tagsJson -> JSONUtil.toList(tagsJson, String.class) 将 json字符串(如"[/"动漫/"]") 解析为 字符串["动漫"])
                    // 将["动漫"]继续.stream()流式处理一下----- 转化为----> "动漫"
                    //最后flatMap将所有的 "xxx" 合并为一个流 ( 即 "动漫","xxx","xxx".....  )
                .flatMap(tagsJson -> JSONUtil.toList(tagsJson, String.class).stream())
                    // groupingBy 将相同标签分组存储 , 存储为 ( 标签 , 使用次数 ) 的形式
                                                // 将相同的标签分到同一组 , 同时累加统计出现次数
                .collect(Collectors.groupingBy(tag -> tag, Collectors.counting()));


        List<SpaceTagAnalyzeResponse> collect = tagCountMap.entrySet().stream()
                .sorted((e1, e2) -> Long.compare(e2.getValue(), e1.getValue())) // 降序排序
                .map(entry -> new SpaceTagAnalyzeResponse(entry.getKey(), entry.getValue()))
                .collect(Collectors.toList());

        // 转换为响应对象，按照使用次数进行排序
        return collect;
    }


    /**
     * 空间大小信息分析
     * @param spaceSizeAnalyzeRequest
     * @param loginUser
     * @return
     */
    @Override
    public List<SpaceSizeAnalyzeResponse> getSpaceSizeAnalyze(SpaceSizeAnalyzeRequest spaceSizeAnalyzeRequest, User loginUser) {
        ThrowUtils.throwIf(spaceSizeAnalyzeRequest == null, ErrorCode.PARAMS_ERROR);
        // 检查权限
        checkSpaceAnalyzeAuth(spaceSizeAnalyzeRequest, loginUser);

        // 构造查询条件
        QueryWrapper<Picture> queryWrapper = new QueryWrapper<>();
        fillAnalyzeQueryWrapper(spaceSizeAnalyzeRequest, queryWrapper);

        // 查询所有符合条件的图片大小
        queryWrapper.select("picSize");
        // 100、120、1000
        List<Long> picSizeList = pictureService.getBaseMapper().selectObjs(queryWrapper)
                .stream()
                .filter(ObjUtil::isNotNull)
                .map(size -> (Long) size)
                .collect(Collectors.toList());

        // 定义分段范围，注意使用有序的 Map
        Map<String, Long> sizeRanges = new LinkedHashMap<>();
        sizeRanges.put("<100KB", picSizeList.stream().filter(size -> size < 100 * 1024).count());
        sizeRanges.put("100KB-500KB", picSizeList.stream().filter(size -> size >= 100 * 1024 && size < 500 * 1024).count());
        sizeRanges.put("500KB-1MB", picSizeList.stream().filter(size -> size >= 500 * 1024 && size < 1 * 1024 * 1024).count());
        sizeRanges.put(">1MB", picSizeList.stream().filter(size -> size >= 1 * 1024 * 1024).count());

        List<SpaceSizeAnalyzeResponse> collect = sizeRanges.entrySet().stream()
                .map(entry -> new SpaceSizeAnalyzeResponse(entry.getKey(), entry.getValue()))
                .collect(Collectors.toList());

        // 转换为响应对象
        return collect;
    }

    @Override
    public List<SpaceUserAnalyzeResponse> getSpaceUserAnalyze(SpaceUserAnalyzeRequest spaceUserAnalyzeRequest, User loginUser) {
        ThrowUtils.throwIf(spaceUserAnalyzeRequest == null, ErrorCode.PARAMS_ERROR);
        // 检查权限
        checkSpaceAnalyzeAuth(spaceUserAnalyzeRequest, loginUser);

        // 构造查询条件
        QueryWrapper<Picture> queryWrapper = new QueryWrapper<>();
        fillAnalyzeQueryWrapper(spaceUserAnalyzeRequest, queryWrapper);
        // 补充用户 id 查询
        Long userId = spaceUserAnalyzeRequest.getUserId();
        queryWrapper.eq(ObjUtil.isNotNull(userId), "userId", userId);
        // 补充分析维度：每日、每周、每月
        String timeDimension = spaceUserAnalyzeRequest.getTimeDimension();
        switch (timeDimension) {
            case "day":
                queryWrapper.select("DATE_FORMAT(createTime, '%Y-%m-%d') as period", "count(*) as count");
                break;
            case "week":
                queryWrapper.select("YEARWEEK(createTime) as period", "count(*) as count");
                break;
            case "month":
                queryWrapper.select("DATE_FORMAT(createTime, '%Y-%m') as period", "count(*) as count");
                break;
            default:
                throw new BusinessException(ErrorCode.PARAMS_ERROR, "不支持的时间维度");
        }

        // 分组排序
        queryWrapper.groupBy("period").orderByAsc("period");

        // 查询并封装结果
        List<Map<String, Object>> queryResult = pictureService.listMaps(queryWrapper);
        List<SpaceUserAnalyzeResponse> collect = queryResult
                .stream()
                .map(result -> {
                    String period = result.get("period").toString();
                    Long count = ((Number) result.get("count")).longValue();
                    return new SpaceUserAnalyzeResponse(period, count);
                })
                .collect(Collectors.toList());


        return collect;
    }

    /**
     * 空间用户分析
     * @param spaceRankAnalyzeRequest
     * @param loginUser
     * @return
     */
    @Override
    public List<Space> getSpaceRankAnalyze(SpaceRankAnalyzeRequest spaceRankAnalyzeRequest, User loginUser) {
        ThrowUtils.throwIf(spaceRankAnalyzeRequest == null, ErrorCode.PARAMS_ERROR);

        // 检查权限，仅管理员可以查看
        ThrowUtils.throwIf(!userService.isAdmin(loginUser), ErrorCode.NO_AUTH_ERROR);

        // 构造查询条件
        QueryWrapper<Space> queryWrapper = new QueryWrapper<>();
        queryWrapper.select("id", "spaceName", "userId", "totalSize")
                .orderByDesc("totalSize")
                .last("limit " + spaceRankAnalyzeRequest.getTopN()); // 取前 N 名

        List<Space> list = spaceService.list(queryWrapper);

        // 查询并封装结果
        return list;
    }




}
