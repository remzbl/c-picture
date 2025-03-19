package com.remzbl.cpictureback.controller;

import com.remzbl.cpictureback.common.BaseResponse;
import com.remzbl.cpictureback.common.ResultUtils;
import io.swagger.annotations.ApiOperation;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.ArrayList;


@RestController
@RequestMapping("/")
public class MainController {

    /**
     * 健康检查
     */
    @ApiOperation("健康检查接口")
    @GetMapping("/health")
    public BaseResponse<String> health() {
        return ResultUtils.success("ok");
    }

}
