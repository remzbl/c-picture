package com.remzbl.cpictureback.utils.interceptor;


import cn.hutool.core.bean.BeanUtil;
import cn.hutool.core.util.StrUtil;
import com.remzbl.cpictureback.exception.ErrorCode;
import com.remzbl.cpictureback.exception.ThrowUtils;
import com.remzbl.cpictureback.model.dto.user.RedisUser;
import com.remzbl.cpictureback.model.entity.Space;
import com.remzbl.cpictureback.model.vo.PictureVO;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;
import org.springframework.web.servlet.HandlerInterceptor;

import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.TimeUnit;

import static com.remzbl.cpictureback.constant.RedisConstants.LOGIN_USER_KEY;
import static com.remzbl.cpictureback.constant.RedisConstants.LOGIN_USER_TTL;
@Slf4j
@Component
public class RefreshTokenInterceptor implements HandlerInterceptor {


    private StringRedisTemplate stringRedisTemplate;
    @Autowired
    public RefreshTokenInterceptor(StringRedisTemplate stringRedisTemplate) {
        this.stringRedisTemplate = stringRedisTemplate;
    }


    /**
     * 接口前置操作 : 拦截请求，判断用户是否登录
     * @param request
     * @param response
     * @param handler
     * @return
     * @throws Exception
     */

    @Override
    public boolean preHandle(HttpServletRequest request, HttpServletResponse response, Object handler) throws Exception {

        if ("OPTIONS".equalsIgnoreCase(request.getMethod())) {
            log.debug("放行 OPTIONS 请求");
            return true;
        }
        String uri = request.getRequestURI();
        if (uri.contains("/user/login") || uri.contains("/user/register")){
            return true;
        }

        if (uri.contains("list/page/vo/cache")){
            return true;
        }

//        log.info("请求路径：{}，token：{}", request.getRequestURI(), request.getHeader("Authorization"));
         //放行 OPTIONS 请求



        // 这是什么神来之垃圾手  直接让我旋转起飞
        // 外面释放不让过 就在这里放行
        // 放行 /api/picture/tag_category","/api/picture/list/page/vo","/api/picture/list/page/vo/cache",三个接口
//        if (    uri.contains("tag_category") ||
//                uri.contains("list/page/vo") ||
//                uri.contains("list/page/vo/cache")
//
//        ){
//
//            return true;
//        }


        // 1.获取请求头中的token
        //log.debug("从request获取token：{}", request.getHeader("Authorization"));
        String token = request.getHeader("Authorization");

        if (StrUtil.isBlank(token)){
            response.setStatus(ErrorCode.NOT_LOGIN_ERROR.getCode());
            return false;
        }

        // 2.基于TOKEN获取redis中的用户
        String key  = LOGIN_USER_KEY + token;
        log.info("keykeykeykey{}", key);

        Map<Object, Object> userMap = stringRedisTemplate.opsForHash().entries(key);
        log.info("userMapuserMapuserMap{}", userMap);
        // 3.判断用户是否存在
        if (userMap.isEmpty()) {
            response.setStatus(ErrorCode.NOT_LOGIN_ERROR.getCode());
            return false;
        }
        // 5.将查询到的hash数据转为UserDTO
        RedisUser redisUser = BeanUtil.fillBeanWithMap(userMap, new RedisUser(), false);
        // 6.存在，保存用户信息到 ThreadLocal 如此便获得了用户信息 之后的功能中若需要获取用户信息时 从ThreadLocal中获取就行
        UserHolder.saveUser(redisUser);
        log.info("用户信息{}", redisUser);

        // 7.刷新token有效期
        stringRedisTemplate.expire(key, LOGIN_USER_TTL, TimeUnit.MINUTES);

        log.info("请求路径：{}，获取到Token：{}", request.getRequestURI(), token);
        log.info("从Redis获取用户：{}", redisUser);
        log.info("从UserHolder获取用户：{}", UserHolder.getUser());


        if (UserHolder.getUser() == null) {
            log.error("RedisUser 为空");
            return false;
        }


        // 8.放行
        return true;
    }


    @Override
    public void afterCompletion(HttpServletRequest request, HttpServletResponse response, Object handler, Exception ex) throws Exception {
        // 在请求处理完成后，清除ThreadLocal中的用户信息，以避免内存泄漏。
        UserHolder.removeUser();
    }
}