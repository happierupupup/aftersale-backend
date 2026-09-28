package com.yean.common.rpc;

import com.yean.common.dto.notify.NotifySendDTO;
import com.yean.common.result.R;
import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;

/**
 * 写站内信到 portal。url 默认与 operatelog 同 portal 地址。
 */
@FeignClient(name = "portal-notify", url = "${yean.notify.portal-url:${yean.operatelog.portal-url:http://127.0.0.1:8081}}")
public interface NotifyFeignClient {

    @PostMapping("/notify/send")
    R<Long> send(@RequestBody NotifySendDTO dto);
}
