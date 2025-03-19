package com.remzbl.cpictureback;

import org.mybatis.spring.annotation.MapperScan;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.context.annotation.EnableAspectJAutoProxy;
import org.springframework.scheduling.annotation.EnableAsync;

@EnableAsync //可以使得方法被异步调用(就是删除信息先响应,后端慢慢删 )
@EnableAspectJAutoProxy(exposeProxy = true)    //SpringAOP 切面编程 获取代理对象开启
@MapperScan("com.remzbl.cpictureback.mapper")  //MyBatisPuls扫描操作数据库的路径
@SpringBootApplication
public class CPictureBackApplication {

    public static void main(String[] args) {
        SpringApplication.run(CPictureBackApplication.class, args);
    }

}
