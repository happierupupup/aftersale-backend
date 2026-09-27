package com.yean.common.rpc;

import com.yean.common.dto.operatelog.OperateLogCreateDTO;
import com.yean.common.result.R;
import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;

/**
 * 写操作日志到 portal。url 由各服务配置 portal.operatelog-url / gateway.portal-url。
 */
@FeignClient(name = "portal-operatelog", url = "${yean.operatelog.portal-url:http://127.0.0.1:8081}")
public interface OperateLogFeignClient {

    @PostMapping("/operatelog")
    R<Void> create(@RequestBody OperateLogCreateDTO dto);
}
