package com.remzbl.cpictureback.api.imagesearch.sub;

import com.remzbl.cpictureback.exception.BusinessException;
import com.remzbl.cpictureback.exception.ErrorCode;
import lombok.extern.slf4j.Slf4j;
import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;

/**
 * 获取以图搜图页面地址（step 1）
 */
@Slf4j
public class GetSoImageUrlApi {

    // 返回的是页面代码的时候 : 使用jsoup文档类接收 , 使用各种选择器获取方法来获取数据

    /**
     * 获取以图搜图页面地址
     *  即获取对我们原url地址转化后的url地址 , 这是获取搜图页面资源负载中所需要的url地址
     * @param imageUrl
     * @return
     */
    public static String getSoImageUrl(String imageUrl) {
        // 1. 接口地址拼接
        String url = "https://st.so.com/r?src=st&srcsp=home&img_url=" + imageUrl + "&submittype=imgurl";
        try {
            // 2. 利用Jsoup访问接口并获取返回的页面源代码
            Document document = Jsoup.connect(url).timeout(5000).get();
            // 3.分析返回的页面源代码，找到图片的url地址所在的标签元素为 CSS类为 img_img的元素
            log.info("获取以图搜图页面文档：{}", document);
            // 4. 根据上面的分析使用Jsoup的selectFirst方法获取图片的url地址所在的标签元素 -- img_img元素
            Element imgElement = document.selectFirst(".img_img");
            if (imgElement != null) {
                String soImageUrl = "";
                // img_img元素中style属性包含图片url地址
                String style = imgElement.attr("style");
                if (style.contains("background-image:url(")) {
                    // 提取style属性中URL部分
                    int start = style.indexOf("url(") + 4;  // 从"Url("之后开始
                    int end = style.indexOf(")", start);    // 找到右括号的位置
                    if (start > 4 && end > start) {
                        soImageUrl = style.substring(start, end);
                    }
                }
                return soImageUrl;
            }
            throw new BusinessException(ErrorCode.OPERATION_ERROR, "搜图失败");
        } catch (Exception e) {
            log.error("搜图失败", e);
            throw new BusinessException(ErrorCode.OPERATION_ERROR, "搜图失败");
    }


    }

}
