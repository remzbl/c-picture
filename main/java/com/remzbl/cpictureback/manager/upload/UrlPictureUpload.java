package com.remzbl.cpictureback.manager.upload;

import cn.hutool.core.io.FileUtil;
import cn.hutool.core.util.StrUtil;
import cn.hutool.http.*;
import com.remzbl.cpictureback.exception.BusinessException;
import com.remzbl.cpictureback.exception.ErrorCode;
import com.remzbl.cpictureback.exception.ThrowUtils;
import org.springframework.stereotype.Service;

import java.io.File;
import java.net.MalformedURLException;
import java.net.URL;
import java.util.Arrays;
import java.util.List;

/**
 * URL 图片上传
 */
@Service
public class UrlPictureUpload extends PictureUploadTemplate {

    //重写模板中(抽象类中)的可变部分即可
    // 校验url文件图片的有效性
    @Override
    protected void validPicture(Object inputSource) {
        String fileUrl = (String) inputSource;
        // 1. 非空 校验
        ThrowUtils.throwIf(StrUtil.isBlank(fileUrl), ErrorCode.PARAMS_ERROR, "文件地址为空");

        // 2. URL格式 校验
        try {
            new URL(fileUrl);// 将fileUrl字符串转换成URL对象
        } catch (MalformedURLException e) {
            // 如果fileUrl不是合法的URL格式，抛出异常
            throw new BusinessException(ErrorCode.PARAMS_ERROR, "文件地址格式不正确");
        }
        // 3. URL的协议 校验
        ThrowUtils.throwIf(!fileUrl.startsWith("http://") && !fileUrl.startsWith("https://"),
                ErrorCode.PARAMS_ERROR, "仅支持 HTTP 或 HTTPS 协议的文件地址");

        // 4. 发送 HEAD 请求验证文件是否存在
            // (1) 访问指定的 URL 并获取响应
        HttpRequest request = HttpUtil.createRequest(Method.HEAD, fileUrl);
                //自动释放资源的方式
        try (HttpResponse httpResponse = request.execute()){
            // (2) 访问状态校验 : 未正常返回，无需执行其他判断
            if (httpResponse.getStatus() != HttpStatus.HTTP_OK) {
                ThrowUtils.throwIf(true, ErrorCode.PARAMS_ERROR, "文件地址不存在");
                //return;
            }
            // (3) 文件若存在，则要进行文件类型 校验
            String contentType = httpResponse.header("Content-Type");
                // 不为空，才校验是否合法
            if (StrUtil.isNotBlank(contentType)) {
                // 允许的图片类型
                final List<String> ALLOW_CONTENT_TYPES = Arrays.asList("image/jpeg", "image/jpg", "image/png", "image/webp");
                ThrowUtils.throwIf(!ALLOW_CONTENT_TYPES.contains(contentType.toLowerCase()),
                        ErrorCode.PARAMS_ERROR, "文件类型错误");
            }
            // (4) 文件存在，文件大小 校验
            String contentLengthStr = httpResponse.header("Content-Length");
            if (StrUtil.isNotBlank(contentLengthStr)) {
                try {
                    long contentLength = Long.parseLong(contentLengthStr);
                    final long ONE_M = 1024 * 1024;
                    ThrowUtils.throwIf(contentLength > 3 * ONE_M, ErrorCode.PARAMS_ERROR, "文件大小不能超过 3MB");
                } catch (NumberFormatException e) {
                    throw new BusinessException(ErrorCode.PARAMS_ERROR, "文件大小格式异常");
                }
            }
        }
    }

    @Override
    protected String getOriginFilename(Object inputSource) {
        String fileUrl = (String) inputSource;
        return FileUtil.mainName(fileUrl);
    }

    @Override
    protected void processFile(Object inputSource, File file) throws Exception {
        String fileUrl = (String) inputSource;
        // 下载文件到临时目录
        HttpUtil.downloadFile(fileUrl, file);
    }
}
