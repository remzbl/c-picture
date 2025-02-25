package com.remzbl.cpictureback.api.imagesearch.sub;

import cn.hutool.core.util.StrUtil;
import cn.hutool.http.HttpRequest;
import cn.hutool.http.HttpResponse;
import cn.hutool.http.HttpStatus;
import cn.hutool.json.JSONArray;
import cn.hutool.json.JSONObject;
import cn.hutool.json.JSONUtil;
import com.remzbl.cpictureback.api.imagesearch.model.SoImageSearchResult;
import com.remzbl.cpictureback.exception.BusinessException;
import com.remzbl.cpictureback.exception.ErrorCode;
import lombok.extern.slf4j.Slf4j;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * 获取图片列表（step 3）
 */
@Slf4j
public class GetSoImageListApi {


    /**
     * 获取图片列表
     * @return
     */
    public static List<SoImageSearchResult> getImageList(String imageUrl, Integer start) {
        String url = "https://st.so.com/stu?a=mrecomm&start=" + start;
        Map<String, Object> formData = new HashMap<>();
        formData.put("img_url", imageUrl);
        HttpResponse response = HttpRequest.post(url)
                .form(formData)
                .timeout(5000)
                .execute();
        // 判断响应状态
        if (HttpStatus.HTTP_OK != response.getStatus()) {
            throw new BusinessException(ErrorCode.OPERATION_ERROR, "搜图失败");
        }
        // 解析响应
        JSONObject body = JSONUtil.parseObj(response.body());
        // 处理响应结果
        if (!Integer.valueOf(0).equals(body.getInt("errno"))) {
            throw new BusinessException(ErrorCode.OPERATION_ERROR, "搜图失败");
        }
        JSONObject data = body.getJSONObject("data");
        List<SoImageSearchResult> result = data.getBeanList("result", SoImageSearchResult.class);
        // 对结果进行处理, 因为返回的是分开的对象, 不是一个完整的图片路径, 这里需要自己拼接
        for (SoImageSearchResult soImageSearchResult : result) {
            String prefix;
            if (StrUtil.isNotBlank(soImageSearchResult.getHttps())) {
                prefix = "https://" + soImageSearchResult.getHttps() + "/";
            } else {
                prefix = "http://" + soImageSearchResult.getHttp() + "/";
            }
            soImageSearchResult.setImgUrl(prefix + soImageSearchResult.getImgkey());
        }



        return result;
    }



    /**
     * 处理接口响应内容
     *
     * @param responseBody 接口返回的JSON字符串
     */
    private static List<SoImageSearchResult> processResponse(String responseBody) {
        // 解析响应对象
        JSONObject jsonObject = new JSONObject(responseBody);
        if (!jsonObject.containsKey("data")) {
            throw new BusinessException(ErrorCode.OPERATION_ERROR, "未获取到图片列表");
        }
        JSONObject data = jsonObject.getJSONObject("data");
        if (!data.containsKey("list")) {
            throw new BusinessException(ErrorCode.OPERATION_ERROR, "未获取到图片列表");
        }
        JSONArray list = data.getJSONArray("list");
        return JSONUtil.toList(list, SoImageSearchResult.class);
    }


}