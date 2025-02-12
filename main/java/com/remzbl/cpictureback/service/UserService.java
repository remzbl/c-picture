package com.remzbl.cpictureback.service;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.remzbl.cpictureback.model.dto.user.UserQueryRequest;
import com.remzbl.cpictureback.model.entity.User;
import com.baomidou.mybatisplus.extension.service.IService;
import com.remzbl.cpictureback.model.vo.LoginUserVO;
import com.remzbl.cpictureback.model.vo.UserVO;

import javax.servlet.http.HttpServletRequest;
import java.util.List;

/**
* @author remzbl
* @description 针对表【user(用户)】的数据库操作Service
* @createDate 2025-01-19 02:39:23
*/
public interface UserService extends IService<User> {

    //基础业务工具方法(为其它业务方法提供服务 或者 为一些controller层的方法提供服务(脱敏信息提供))
    //region

    /**
     * 1 获取加密后的密码
     *
     * @param userPassword
     * @return
     */
    String getEncryptPassword(String userPassword);

    /**
     * 2获得脱敏后的登录用户信息 (查询个人信息)
     *
     * @param user
     * @return
     */
    LoginUserVO getLoginUserVO(User user);

    /**
     * 3获得脱敏后的用户信息 (查询他人信息)
     *
     * @param user
     * @return
     */
    UserVO getUserVO(User user);

    /**
     * 4获得脱敏后的用户信息列表 (管理员查询)
     *
     * @param userList
     * @return 脱敏后的用户列表
     */
    List<UserVO> getUserVOList(List<User> userList);

    Page<UserVO> getUserVOPage(Page<User> userPage);

    boolean isAdmin(User user);

    //endregion


    //以下为用户模块业务方法


    // 用户注册完成后 后端返回给前端的数据data 是用户注册的ID 所以接口返回类型是long
    // 这个long类型也是这个注册接口给响应泛型类中data类型的指定----<T> 为 long
    // long userRegister(UserRegisterRequest userRegisterRequest); // 也是可以的

    /**
     * 用户注册
     *
     * @param userAccount   用户账户
     * @param userPassword  用户密码
     * @param checkPassword 校验密码
     * @return 新用户 id
     */
    long userRegister(String userAccount, String userPassword, String checkPassword);

    /**
     * 用户登录
     *
     * @param userAccount  用户账户
     * @param userPassword 用户密码
     * @param request
     * @return 脱敏后的用户信息
     */
    LoginUserVO userLogin(String userAccount, String userPassword, HttpServletRequest request);



    /**
     * 获取当前登录用户
     *
     * @return
     */
    User getLoginUser();



    /**
     * 用户注销
     *
     * @return
     */
    boolean userLogout();

    /**
     * 获取查询条件
     * @param userQueryRequest
     * @return
     */
    QueryWrapper<User> getQueryWrapper(UserQueryRequest userQueryRequest);
}

