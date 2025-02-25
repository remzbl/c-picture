package com.remzbl.cpictureback.model.vo;

import cn.hutool.json.JSONUtil;
import com.remzbl.cpictureback.model.entity.Picture;
import lombok.Data;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.BeanUtils;

import java.io.Serializable;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
@Slf4j
@Data
public class PictureVO implements Serializable {

    /**
     * id
     */
    private Long id;

    /**
     * 图片 url
     */
    private String url;

    /**
     * 缩略图 url
     */
    private String thumbnailUrl;

    /**
     * 图片名称
     */
    private String name;

    /**
     * 简介
     */
    private String introduction;

    /**
     * 标签
     */
    private List<String> tags;

    /**
     * 分类
     */
    private String category;

    /**
     * 文件体积
     */
    private Long picSize;

    /**
     * 图片宽度
     */
    private Integer picWidth;

    /**
     * 图片高度
     */
    private Integer picHeight;

    /**
     * 图片比例
     */
    private Double picScale;

    /**
     * 图片格式
     */
    private String picFormat;


    /**
     * 用户 id
     */
    private Long userId;

    /**
     * 空间 id
     */
    private Long spaceId;

    /**
     * 创建时间
     */
    private Date createTime;

    /**
     * 编辑时间
     */
    private Date editTime;

    /**
     * 更新时间
     */
    private Date updateTime;

    /**
     * 创建用户信息
     */
    private UserVO user;


    /**
     * 权限列表
     */
    private List<String> permissionList = new ArrayList<>();

    private static final long serialVersionUID = 1L;


    // 以下为图片 的 原信息与脱敏信息的转换方法(这里的转化只是图片表的转变)  但是如果需要关联其他表的信息 可以在业务层中实现
    /**
     * 封装类转对象
     */
    public static Picture voToObj(PictureVO pictureVO) {
        if (pictureVO == null) {
            return null;
        }
        Picture picture = new Picture();
        BeanUtils.copyProperties(pictureVO, picture);
        // 类型不同，需要转换
        picture.setTags(JSONUtil.toJsonStr(pictureVO.getTags()));
        return picture;
    }

    /**
     * 对象转封装类
     */
    public static PictureVO objToVo(Picture picture) {
        if (picture == null) {
            return null;
        }
        PictureVO pictureVO = new PictureVO();
        BeanUtils.copyProperties(picture, pictureVO);
        log.info("pictureVO.getTags():" + JSONUtil.toList(picture.getTags(), String.class));
        log.info("pictureVO.getTags():" + JSONUtil.toList(picture.getTags(), String.class));
        log.info("pictureVO.getTags():" + JSONUtil.toList(picture.getTags(), String.class));
        log.info("pictureVO.getTags():" + JSONUtil.toList(picture.getTags(), String.class));
        log.info("pictureVO.getTags():" + JSONUtil.toList(picture.getTags(), String.class));
        log.info("pictureVO.getTags():" + JSONUtil.toList(picture.getTags(), String.class));
        // 类型不同，需要转换
        pictureVO.setTags(JSONUtil.toList(picture.getTags(), String.class));
        // 日志记录标签转化为json格式的信息

        return pictureVO;
    }
}