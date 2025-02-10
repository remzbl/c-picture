package com.remzbl.cpictureback.aop;


import com.remzbl.cpictureback.model.vo.LoginUserVO;
import org.aspectj.lang.ProceedingJoinPoint;
import com.remzbl.cpictureback.annotation.AuthCheck;
import com.remzbl.cpictureback.exception.BusinessException;
import com.remzbl.cpictureback.exception.ErrorCode;
import com.remzbl.cpictureback.model.entity.User;
import com.remzbl.cpictureback.model.enums.UserRoleEnum;
import com.remzbl.cpictureback.service.UserService;
import org.aspectj.lang.annotation.Around;
import org.aspectj.lang.annotation.Aspect;
import org.springframework.stereotype.Component;
import org.springframework.web.context.request.RequestAttributes;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

import javax.annotation.Resource;
import javax.servlet.http.HttpServletRequest;

@Aspect
@Component
public class AuthInterceptor {

    @Resource
    private UserService userService;

    /**
     * 执行拦截
     *
     * @param joinPoint 切入点
     * @param authCheck 权限校验注解
     */								   //第一个参数可以获取目标方法	  第二个参数即自定义注解所标注的方法才可以用这个切面增强方法
    @Around("@annotation(authCheck)")  //即可以知道哪个方法被增强了
    public Object doInterceptor(ProceedingJoinPoint joinPoint, AuthCheck authCheck) throws Throwable {
        //获取当前请求的用户信息
        RequestAttributes requestAttributes = RequestContextHolder.currentRequestAttributes();// 获取当前请求的所有属性
        HttpServletRequest request = ((ServletRequestAttributes) requestAttributes).getRequest();// 再继续获取http请求信息
        User loginUser = userService.getLoginUser(); //再通过我们业务实现的方法 , 获取请求得到当前用户对象

        //获取用户请求的这个方法所需要的权限
        String mustRole = authCheck.mustRole();  //获取目标方法所需要的权限用户
        UserRoleEnum mustRoleEnum = UserRoleEnum.getEnumByValue(mustRole); // 将权限值转换为对应的枚举类型 `UserRoleEnum`

        // 不需要权限，放行
        if (mustRoleEnum == null) {
            return joinPoint.proceed(); //放行 : 去执行此次请求原本需要执行的方法
        }

        // 以下为：必须有该权限才通过
        // 获取当前用户具有的权限  若UserRoleEnum userRoleEnum = UserRoleEnum.getEnumByValue("admin");
                               //最终，userRoleEnum 的值是 UserRoleEnum.ADMIN。
        UserRoleEnum userRoleEnum = UserRoleEnum.getEnumByValue(loginUser.getUserRole());
        // 没有权限，拒绝
        if (userRoleEnum == null) {
            throw new BusinessException(ErrorCode.NO_AUTH_ERROR);
        }
        // 要求必须有管理员权限，但用户没有管理员权限，拒绝
        //  这里的UserRoleEnum.ADMIN是固定值 与当前用户无关                         //这里userRoleEnum的值是UserRoleEnum.ADMIN或UserRoleEnum.USER
        if (UserRoleEnum.ADMIN.equals(mustRoleEnum) && !UserRoleEnum.ADMIN.equals(userRoleEnum)) {
            throw new BusinessException(ErrorCode.NO_AUTH_ERROR);
        }
        // 通过权限校验，放行
        return joinPoint.proceed();
    }
}
