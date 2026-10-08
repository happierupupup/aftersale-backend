package com.yean.common.rpc;

import com.yean.common.dto.process.ProcessActionDTO;
import com.yean.common.dto.process.ProcessHistoryVO;
import com.yean.common.dto.process.ProcessInstanceVO;
import com.yean.common.dto.process.ProcessStartDTO;
import com.yean.common.result.R;
import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;

import javax.validation.Valid;

/**
 * trade → process-service。url 配 yean.process.url。
 */
@FeignClient(name = "process-service", url = "${yean.process.url:http://127.0.0.1:8083}")
public interface ProcessFeignClient {

    @PostMapping("/process/start")
    R<ProcessInstanceVO> start(@Valid @RequestBody ProcessStartDTO dto);

    @PostMapping("/process/complete")
    R<ProcessInstanceVO> complete(@Valid @RequestBody ProcessActionDTO dto);

    @PostMapping("/process/reject")
    R<ProcessInstanceVO> reject(@Valid @RequestBody ProcessActionDTO dto);

    @PostMapping("/process/timeout-close")
    R<ProcessInstanceVO> timeoutClose(@Valid @RequestBody ProcessActionDTO dto);

    @PostMapping("/process/withdraw")
    R<ProcessInstanceVO> withdraw(@Valid @RequestBody ProcessActionDTO dto);

    @PostMapping("/process/rollback")
    R<ProcessInstanceVO> rollback(@Valid @RequestBody ProcessActionDTO dto);

    @GetMapping("/process/{businessKey}/current")
    R<ProcessInstanceVO> current(@PathVariable("businessKey") String businessKey);

    @GetMapping("/process/{businessKey}/history")
    R<ProcessHistoryVO> history(@PathVariable("businessKey") String businessKey);
}
