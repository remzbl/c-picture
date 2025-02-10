package com.remzbl.cpictureback.service.impl;

import cn.hutool.core.bean.BeanUtil;
import cn.hutool.core.bean.copier.CopyOptions;
import cn.hutool.core.collection.CollUtil;
import cn.hutool.core.lang.UUID;
import cn.hutool.core.util.ObjUtil;
import cn.hutool.core.util.StrUtil;
import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.remzbl.cpictureback.constant.UserConstant;
import com.remzbl.cpictureback.exception.BusinessException;
import com.remzbl.cpictureback.exception.ErrorCode;
import com.remzbl.cpictureback.exception.ThrowUtils;
import com.remzbl.cpictureback.model.dto.user.RedisUser;
import com.remzbl.cpictureback.model.dto.user.UserQueryRequest;
import com.remzbl.cpictureback.model.dto.user.UserRegisterRequest;
import com.remzbl.cpictureback.model.entity.User;
import com.remzbl.cpictureback.model.enums.UserRoleEnum;
import com.remzbl.cpictureback.model.vo.LoginUserVO;
import com.remzbl.cpictureback.model.vo.UserVO;
import com.remzbl.cpictureback.service.UserService;
import com.remzbl.cpictureback.mapper.UserMapper;
import com.remzbl.cpictureback.utils.interceptor.UserHolder;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;
import org.springframework.util.DigestUtils;

import javax.annotation.Resource;
import javax.servlet.http.HttpServletRequest;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;

import static com.remzbl.cpictureback.constant.RedisConstants.LOGIN_USER_KEY;
import static com.remzbl.cpictureback.constant.RedisConstants.LOGIN_USER_TTL;

/**
 * @author remzbl
 * @description 针对表【user(用户)】的数据库操作Service实现
 * @createDate 2025-01-19 02:39:22
 */
@Slf4j  //日志
@Service
public class UserServiceImpl extends ServiceImpl<UserMapper, User> implements UserService {

    @Resource
    private StringRedisTemplate stringRedisTemplate;

    //以下为基础工具业务方法 为其它功能方法提供服务

    //region

    /**
     * 获取加密后的密码 后续多个方法都会用到
     *
     * @param userPassword 用户密码
     * @return 加密后的密码
     */
    @Override
    public String getEncryptPassword(String userPassword) {
        // 盐值，混淆密码
        final String SALT = "remzbl";
        return DigestUtils.md5DigestAsHex((SALT + userPassword).getBytes());
    }

    /**
     * 获取脱敏类的用户信息 查询个人信息
     *
     * @param user 用户
     * @return 脱敏后的用户信息
     */
    @Override
    public LoginUserVO getLoginUserVO(User user) {
        if (user == null) {
            return null;
        }
        LoginUserVO loginUserVO = new LoginUserVO();
        BeanUtil.copyProperties(user, loginUserVO);
        return loginUserVO;
    }

    /**
     * 获得脱敏后的用户信息 查询他人信息
     *
     * @param user
     * @return
     */

    @Override
    public UserVO getUserVO(User user) {
        if (user == null) {
            return null;
        }
        UserVO userVO = new UserVO();
        BeanUtil.copyProperties(user, userVO);
        return userVO;
    }

    /**
     * 获取脱敏后的用户列表 管理员查询
     *
     * @param userList
     * @return
     */
    @Override
    public List<UserVO> getUserVOList(List<User> userList) {
        if (CollUtil.isEmpty(userList)) {
            return new ArrayList<>();
        }
        return userList.stream() //创建流对象
                .map(this::getUserVO) // 中间方法(映射) : 将每个User对象转换为UserVO对象
                .collect(Collectors.toList());// 终止方法(收集) : 将流中的元素收集到一个新的集合中，并返回该集合
    }

    @Override
    public Page<UserVO> getUserVOPage(Page<User> userPage) {
        //创建脱敏分页对象

        Page<UserVO> userVOPage = new Page<>(userPage.getCurrent(), userPage.getSize(), userPage.getTotal());

        //获取符合查询条件的用户列表 并对这些用户信息进行脱敏
        List<User> userList = userPage.getRecords();

        List<UserVO> userVOList = userList.stream() //创建流对象
                .map(this::getUserVO) // 中间方法(映射) : 将每个User对象转换为UserVO对象
                .collect(Collectors.toList());// 终止方法(收集) : 将流中的元素收集到一个新的集合中，并返回该集合

        //将脱敏信息列表设置到脱敏分页对象中
        userVOPage.setRecords(userVOList);
        return userVOPage;
    }


    //判断是否为管理员
    @Override
    public boolean isAdmin(User user) {
        return user != null && UserRoleEnum.ADMIN.getValue().equals(user.getUserRole());
    }




    /**
     * 用户页面显示(查询)个人信息 需要getLoginUserVO配合
     *
     * @return User
     */
    @Override
    public User getLoginUser() {
        // 判断是否已经登录

        //Object userObj = request.getSession().getAttribute(UserConstant.USER_LOGIN_STATE);
        RedisUser currentUser = UserHolder.getUser();
        log.info("尝试获取登录用户：{}", currentUser);

        if (currentUser == null || currentUser.getId() == null) {
            throw new BusinessException(ErrorCode.NOT_LOGIN_ERROR);
        }
        // 从数据库中查询（追求性能的话可以注释，直接返回上述结果）
        Long userId = currentUser.getId();
        User user = this.getById(userId);
        if (user == null) {
            throw new BusinessException(ErrorCode.NOT_LOGIN_ERROR);
        }

        return user;

        //直接在业务层进行脱敏信息的获取 , 而不是在接口层
        //LoginUserVO loginUserVO = getLoginUserVO(currentUser);
        //return getLoginUserVO(currentUser);
    }



    //endregion



    /**
     * 用户注册
     *
     * @param userAccount   用户账户
     * @param userPassword  用户密码
     * @param checkPassword 校验密码
     * @return long
     */
    @Override
    public long userRegister(String userAccount, String userPassword, String checkPassword) {


        //普通抛出异常的方式
       /*
       // 1. 校验参数 hutool工具库校验数据
       if (StrUtil.hasBlank(userAccount , userPassword , checkPassword)) {
            throw new BusinessException(ErrorCode.PARAMS_ERROR ,  "参数不能为空");
        }

        if (userAccount.length() < 4) {
            throw new BusinessException(ErrorCode.PARAMS_ERROR , "用户账号过短");
        }
        if (userPassword.length() < 8 || checkPassword.length() < 8) {
            throw new BusinessException(ErrorCode.PARAMS_ERROR , "用户密码过短");
        }

        if (!userPassword.equals(checkPassword)) {
            throw new BusinessException(ErrorCode.PARAMS_ERROR , "两次输入的密码不一致");
        }
       */

        // 1校验
        // (1)格式校验 使用自定义异常工具校验异常 断言式
        ThrowUtils.throwIf(StrUtil.hasBlank(userAccount, userPassword, checkPassword), ErrorCode.PARAMS_ERROR, "参数不能为空");
        ThrowUtils.throwIf(userAccount.length() < 4, ErrorCode.PARAMS_ERROR, "用户账号过短");
        ThrowUtils.throwIf(userPassword.length() < 8 || checkPassword.length() < 8, ErrorCode.PARAMS_ERROR, "用户密码过短");
        ThrowUtils.throwIf(!userPassword.equals(checkPassword), ErrorCode.PARAMS_ERROR, "两次输入的密码不一致");

        // (2)账号校验
        // 检查用户账号是否和数据库中已有的重复
           // 手动mapper的方式进行查询是否存在重复  另有更好的方式----用basemapper中提供的方法进行查询

        QueryWrapper<User> queryWrapper = new QueryWrapper<User>()
                .eq("userAccount", userAccount);
                        //列(数据库中字段)   请求传来的数据字段

            //普通的增删查改(查询数量) 可以利用baseMapper中提供的方法实现
        long count = this.baseMapper.selectCount(queryWrapper);
        ThrowUtils.throwIf(count > 0, ErrorCode.PARAMS_ERROR, "账号重复");

        // 2. 密码一定要加密
        String encryptPassword = getEncryptPassword(userPassword);

        // 3. 插入数据到数据库中
        User user = new User();
        user.setUserAccount(userAccount);
        user.setUserPassword(encryptPassword);
        user.setUserName("无名");
        user.setUserRole(UserRoleEnum.USER.getValue());
        boolean saveResult = this.save(user);
        ThrowUtils.throwIf(!saveResult, ErrorCode.SYSTEM_ERROR, "注册失败，数据库错误");
        return user.getId();


    }


    /**
     * 用户登录
     *
     * @param userAccount   用户账户
     * @param userPassword  用户密码
     * @param request       请求
     * @return LoginUserVO
     */
    @Override
    public LoginUserVO userLogin(String userAccount, String userPassword, HttpServletRequest request) {
        // 1. 校验
        /*if (StrUtil.hasBlank(userAccount, userPassword)) {
            throw new BusinessException(ErrorCode.PARAMS_ERROR, "参数为空");
        }
        if (userAccount.length() < 4) {
            throw new BusinessException(ErrorCode.PARAMS_ERROR, "用户账号错误");
        }
        if (userPassword.length() < 8) {
            throw new BusinessException(ErrorCode.PARAMS_ERROR, "用户密码错误");
        }*/

        ThrowUtils.throwIf(StrUtil.hasBlank(userAccount, userPassword), ErrorCode.PARAMS_ERROR, "参数不能为空");
        ThrowUtils.throwIf(userAccount.length() < 4, ErrorCode.PARAMS_ERROR, "用户账号错误");
        ThrowUtils.throwIf(userPassword.length() < 8, ErrorCode.PARAMS_ERROR, "用户密码过短");
        // 2. 对用户传递的密码进行加密
        String encryptPassword = getEncryptPassword(userPassword);
        // 3. 查询数据库中的用户是否存在
        QueryWrapper<User> queryWrapper = new QueryWrapper<User>()
            .eq("userAccount", userAccount)
            .eq("userPassword", encryptPassword);

        //登录 : 查询一个 进行对比
        User user = this.baseMapper.selectOne(queryWrapper);

        // 不存在，抛异常
        if (user == null) {
            log.info("user login failed, userAccount cannot match userPassword");
            throw new BusinessException(ErrorCode.PARAMS_ERROR, "用户不存在或者密码错误");
        }
        // 4. 保存用户的登录态
        //request.getSession().setAttribute(UserConstant.USER_LOGIN_STATE, user);

        // 优化 : 使用redis存储用户信息
        // 7.1.随机生成token，作为登录令牌
        String token = UUID.randomUUID().toString(true);
        // 7.2.将User对象转为HashMap存储
        RedisUser redisUser = BeanUtil.copyProperties(user, RedisUser.class);
            //Map<String, Object> userMap = BeanUtil.beanToMap(redisUser);如果只是这样普通的转化 会导致下面redis存储发生类型转化错误
            //解决方法一 : 自己创建一个Map结构类 适配我们自己的redisUser中的字段
            //解决方法二 : 继续使用下面这个工具类 可以用其中的CopyOptions自定义类型
        Map<String, Object> userMap = BeanUtil.beanToMap(redisUser, new HashMap<>(),
                CopyOptions.create()
                        .setIgnoreNullValue(true) // 忽略为空的字段
                        .setFieldValueEditor((fieldName, fieldValue) -> fieldValue.toString()));// 设置值编辑器将key 与 都转为String类型

        // 7.3.存储
        String tokenKey = LOGIN_USER_KEY + token;
        stringRedisTemplate.opsForHash().putAll(tokenKey, userMap); // 必须保证userMap中的key和value都是String类型

        // 7.4.设置token有效期
        stringRedisTemplate.expire(tokenKey, LOGIN_USER_TTL, TimeUnit.MINUTES);

        // 获取用户信息视图对象
        LoginUserVO loginUserVO = this.getLoginUserVO(user);

        loginUserVO.setToken(token);

        log.info("用户登录成功，ID：{}，Token：{}，存储到Redis：{}",
                redisUser.getId(), token, redisUser);

        return loginUserVO;


    }








    /**
     * 用户注销(退出登录)
     * @return boolean
     */
    @Override
    public boolean userLogout() {
        // 判断是否已经登录
        //Object userObj = request.getSession().getAttribute(UserConstant.USER_LOGIN_STATE);
        RedisUser user = UserHolder.getUser();
        if (user == null) {
            throw new BusinessException(ErrorCode.OPERATION_ERROR, "未登录");
        }
        // 移除登录态
        UserHolder.removeUser();
        return true;
    }

    @Override
    public QueryWrapper<User> getQueryWrapper(UserQueryRequest userQueryRequest) {
        if (userQueryRequest == null) {
            throw new BusinessException(ErrorCode.PARAMS_ERROR, "请求参数为空");
        }
        Long id = userQueryRequest.getId();
        String userName = userQueryRequest.getUserName();
        String userAccount = userQueryRequest.getUserAccount();
        String userProfile = userQueryRequest.getUserProfile();
        String userRole = userQueryRequest.getUserRole();
        String sortField = userQueryRequest.getSortField();
        String sortOrder = userQueryRequest.getSortOrder();
        QueryWrapper<User> queryWrapper = new QueryWrapper<>();
        queryWrapper.eq(ObjUtil.isNotNull(id), "id", id);
        queryWrapper.eq(StrUtil.isNotBlank(userRole), "userRole", userRole);
        queryWrapper.like(StrUtil.isNotBlank(userAccount), "userAccount", userAccount);
        queryWrapper.like(StrUtil.isNotBlank(userName), "userName", userName);
        queryWrapper.like(StrUtil.isNotBlank(userProfile), "userProfile", userProfile);
        queryWrapper.orderBy(StrUtil.isNotEmpty(sortField), sortOrder.equals("ascend"), sortField);
        return queryWrapper;
    }


}




