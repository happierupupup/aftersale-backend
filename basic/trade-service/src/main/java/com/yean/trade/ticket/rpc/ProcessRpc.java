package com.yean.trade.ticket.rpc;

import com.yean.common.dto.process.ProcessActionDTO;
import com.yean.common.dto.process.ProcessHistoryVO;
import com.yean.common.dto.process.ProcessInstanceVO;
import com.yean.common.dto.process.ProcessStartDTO;
import com.yean.common.exception.BizException;
import com.yean.common.result.R;
import com.yean.common.rpc.ProcessFeignClient;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class ProcessRpc {

    private final ProcessFeignClient processFeignClient;

    public ProcessInstanceVO start(ProcessStartDTO dto) {
        return unwrap(processFeignClient.start(dto));
    }

    public ProcessInstanceVO complete(ProcessActionDTO dto) {
        return unwrap(processFeignClient.complete(dto));
    }

    public ProcessInstanceVO reject(ProcessActionDTO dto) {
        return unwrap(processFeignClient.reject(dto));
    }

    public ProcessInstanceVO withdraw(ProcessActionDTO dto) {
        return unwrap(processFeignClient.withdraw(dto));
    }

    public ProcessInstanceVO rollback(ProcessActionDTO dto) {
        return unwrap(processFeignClient.rollback(dto));
    }

    public ProcessInstanceVO current(String businessKey) {
        return unwrap(processFeignClient.current(businessKey));
    }

    public ProcessHistoryVO history(String businessKey) {
        return unwrap(processFeignClient.history(businessKey));
    }

    private static <T> T unwrap(R<T> r) {
        if (r == null) {
            throw new BizException("process-service 无响应");
        }
        if (r.getCode() != 0) {
            throw new BizException(r.getCode(), r.getMsg());
        }
        return r.getData();
    }
}
