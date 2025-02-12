package com.remzbl.cpictureback.utils.interceptor;


import cn.hutool.core.bean.BeanUtil;
import cn.hutool.core.util.StrUtil;
import com.remzbl.cpictureback.exception.ErrorCode;
import com.remzbl.cpictureback.model.dto.user.RedisUser;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;
import org.springframework.web.servlet.HandlerInterceptor;

import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import java.util.Map;
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

        log.debug("拦截器触发，请求路径：{}，方法：{}", request.getRequestURI(), request.getMethod());
        String uri = request.getRequestURI();
        if (uri.contains("/user/login")){
            return true;
        }
        log.debug("请求路径：{}，token：{}", request.getRequestURI(), request.getHeader("Authorization"));
         //放行 OPTIONS 请求
        if ("OPTIONS".equalsIgnoreCase(request.getMethod())) {
            log.debug("放行 OPTIONS 请求");
            return true;
        }
        // 1.获取请求头中的token
        log.debug("从request获取token：{}", request.getHeader("Authorization"));

        String token = request.getHeader("Authorization");

        if (StrUtil.isBlank(token)){
            response.setStatus(ErrorCode.NOT_LOGIN_ERROR.getCode());
            return false;
        }

        // 2.基于TOKEN获取redis中的用户
        String key  = LOGIN_USER_KEY + token;
        Map<Object, Object> userMap = stringRedisTemplate.opsForHash().entries(key);
        // 3.判断用户是否存在
        if (userMap.isEmpty()) {
            response.setStatus(ErrorCode.NOT_LOGIN_ERROR.getCode());
            return false;
        }
        // 5.将查询到的hash数据转为UserDTO
        RedisUser redisUser = BeanUtil.fillBeanWithMap(userMap, new RedisUser(), false);
        // 6.存在，保存用户信息到 ThreadLocal 如此便获得了用户信息 之后的功能中若需要获取用户信息时 从ThreadLocal中获取就行
        UserHolder.saveUser(redisUser);

        log.debug("用户信息{}", redisUser);
        // 7.刷新token有效期
        stringRedisTemplate.expire(key, LOGIN_USER_TTL, TimeUnit.MINUTES);


        log.debug("请求路径：{}，获取到Token：{}", request.getRequestURI(), token);
        log.debug("从Redis获取用户：{}", redisUser);
        log.debug("从UserHolder获取用户：{}", UserHolder.getUser());
        // 8.放行
        return true;
    }
}