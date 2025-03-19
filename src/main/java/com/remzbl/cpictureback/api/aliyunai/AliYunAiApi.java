package com.remzbl.cpictureback.api.aliyunai;

import cn.hutool.core.util.StrUtil;
import cn.hutool.http.HttpRequest;
import cn.hutool.http.HttpResponse;
import cn.hutool.json.JSONUtil;
import com.remzbl.cpictureback.api.aliyunai.model.CreateOutPaintingTaskRequest;
import com.remzbl.cpictureback.api.aliyunai.model.CreateOutPaintingTaskResponse;
import com.remzbl.cpictureback.api.aliyunai.model.GetOutPaintingTaskResponse;
import com.remzbl.cpictureback.exception.BusinessException;
import com.remzbl.cpictureback.exception.ErrorCode;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

@Slf4j
@Component
public class AliYunAiApi {

    // 读取配置文件
    @Value("${aliYunAi.apiKey}")
    private String apiKey;

    // 静态常量 : static final 修饰的变量 就是 静态常量

    // 创建任务地址
    public static final String CREATE_OUT_PAINTING_TASK_URL = "https://dashscope.aliyuncs.com/api/v1/services/aigc/image2image/out-painting";

    // 查询任务状态
    public static final String GET_OUT_PAINTING_TASK_URL = "https://dashscope.aliyuncs.com/api/v1/tasks/%s";


    /**
     * 创建任务
     *
     * @param createOutPaintingTaskRequest
     * @return
     */
    public CreateOutPaintingTaskResponse createOutPaintingTask(CreateOutPaintingTaskRequest createOutPaintingTaskRequest) {
        if (createOutPaintingTaskRequest == null) {
            throw new BusinessException(ErrorCode.OPERATION_ERROR, "扩图参数为空");
        }
        // 发送请求
        HttpRequest httpRequest = HttpRequest.post(CREATE_OUT_PAINTING_TASK_URL)
                .header("Authorization", "Bearer " + apiKey)
                // 必须开启异步处理
                .header("X-DashScope-Async", "enable")
                .header("Content-Type", "application/json")
                    // 将请求实体类序列化为JSON字符串
                .body(JSONUtil.toJsonStr(createOutPaintingTaskRequest));
        // 处理响应
                // 使用try-with-resources语法 (就是try后跟着小括号)，自动关闭HttpResponse
        try (HttpResponse httpResponse = httpRequest.execute()) {
            // 1. http请求(非业务逻辑)的响应错误处理  (HTTP 请求可能因为网络问题、服务器错误、认证失败等原因返回非 200 状态码)
            if (!httpResponse.isOk()) {
                log.error("请求异常：{}", httpResponse.body());
                throw new BusinessException(ErrorCode.OPERATION_ERROR, "AI 扩图失败");
            }
                // 将响应体解析(反序列化)为Java对象
            CreateOutPaintingTaskResponse createOutPaintingTaskResponse = JSONUtil.toBean(httpResponse.body(), CreateOutPaintingTaskResponse.class);
            // 2. 官方业务逻辑层面的响应错误处理
            if (createOutPaintingTaskResponse.getCode() != null) {
                String errorMessage = createOutPaintingTaskResponse.getMessage();
                log.error("请求异常：{}", errorMessage);
                throw new BusinessException(ErrorCode.OPERATION_ERROR, "AI 扩图失败，" + errorMessage);
            }
            return createOutPaintingTaskResponse;
        }
    }

    /**
     * 查询创建的任务结果
     *
     * @param taskId
     * @return
     */
    public GetOutPaintingTaskResponse getOutPaintingTask(String taskId) {
        if (StrUtil.isBlank(taskId)) {
            throw new BusinessException(ErrorCode.OPERATION_ERROR, "任务 ID 不能为空");
        }
        String url = String.format(GET_OUT_PAINTING_TASK_URL, taskId);
        // 构建请求
        HttpRequest httpRequest = HttpRequest.get(url)
                .header("Authorization", "Bearer " + apiKey);

        // 发送请求 并 处理响应
                                            // 发送请求
        try (HttpResponse httpResponse = httpRequest.execute()) {
            if (!httpResponse.isOk()) {
                log.error("请求异常：{}", httpResponse.body());
                throw new BusinessException(ErrorCode.OPERATION_ERROR, "获取任务结果失败");
            }
            // 因为这个请求存在3种情况而且需要处理时间，可能需要轮询检查任务的状态 , 更适合在前端轮询检查, 而不是在后端返回信息
            return JSONUtil.toBean(httpResponse.body(), GetOutPaintingTaskResponse.class);
        }
    }
}
