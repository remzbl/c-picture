package com.remzbl.cpictureback.mapper;

import com.remzbl.cpictureback.model.entity.User;
import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import org.springframework.stereotype.Repository;

/**
* @author remzbl
* @description 针对表【user(用户)】的数据库操作Mapper
* @createDate 2025-01-19 02:39:23
* @Entity com.remzbl.cpictureback.model.entity.User
*/

@Repository
public interface UserMapper extends BaseMapper<User> {

}




