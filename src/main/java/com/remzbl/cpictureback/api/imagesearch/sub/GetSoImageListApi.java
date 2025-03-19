package com.remzbl.cpictureback.api.imagesearch.sub;

import cn.hutool.core.util.StrUtil;
import cn.hutool.http.*;
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

    // 返回的是json类型数据 , 使用Json工具类转化为json对象并利用提供的方法获取其中的值
    // 利用getBeanList方法获取其中的我们需要的data值 , 然后可以自定义类 列表 来接收数据data部分 ,


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
        // 添加请求表头referer:https://st.so.com/r?src=st&srcsp=home

        // 判断响应状态
        if (HttpStatus.HTTP_OK != response.getStatus()) {
            throw new BusinessException(ErrorCode.OPERATION_ERROR, "搜图失败");
        }
        // 解析响应 (其实就是用我们已有的工具类 , 处理我们获得的数据)
        JSONObject body = JSONUtil.parseObj(response.body());
        // 处理响应结果
                                        // JSONObject类处理一 : 获取键为"errno"的值
        if (!Integer.valueOf(0).equals(body.getInt("errno"))) {
            throw new BusinessException(ErrorCode.OPERATION_ERROR, "搜图失败");
        }
                            // JSONObject类处理二 : 获取键为"data"的值
        JSONObject data = body.getJSONObject("data");
                                            // JSONObject类处理三 : 将json字符列表转化为我们自定义的类(根据响应数据中的信息所构造)的列表
        List<SoImageSearchResult> result = data.getBeanList("result", SoImageSearchResult.class);
        // 对结果进行处理, 因为返回的是分开的对象, 不是一个完整的图片路径, 这里需要自己拼接
        for (SoImageSearchResult soImageSearchResult : result) {
            String prefix;

            if (StrUtil.isNotBlank(soImageSearchResult.getHttps())) {
                prefix = "https://" + soImageSearchResult.getHttps() + "/";
            } else {
                prefix = "http://" + soImageSearchResult.getHttp() + "/";
            }
            String imgKey = soImageSearchResult.getImgkey();
            String imgUrl = prefix + imgKey;

            // 无法解决网页图片显示403错误
//            HttpRequest request = HttpUtil.createRequest(Method.HEAD, imgUrl);
//            try(HttpResponse httpResponse = request.execute()){
//                if (httpResponse.getStatus() == HttpStatus.HTTP_FORBIDDEN) {
//                    continue;
//                }
//            }
            soImageSearchResult.setImgUrl(imgUrl);
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