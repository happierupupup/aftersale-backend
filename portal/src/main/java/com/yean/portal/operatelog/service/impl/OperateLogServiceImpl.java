package com.yean.portal.operatelog.service.impl;

import com.yean.common.dto.operatelog.OperateLogCreateDTO;
import com.yean.portal.operatelog.dto.SysOperateLog;
import com.yean.portal.operatelog.mapper.SysOperateLogMapper;
import com.yean.portal.operatelog.service.OperateLogService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

@Service
@RequiredArgsConstructor
public class OperateLogServiceImpl implements OperateLogService {

    private final SysOperateLogMapper sysOperateLogMapper;

    @Override
    public void create(OperateLogCreateDTO dto) {
        if (dto == null || !StringUtils.hasText(dto.getModule()) || !StringUtils.hasText(dto.getAction())) {
            return;
        }
        SysOperateLog row = new SysOperateLog();
        row.setModule(dto.getModule().trim());
        row.setAction(dto.getAction().trim());
        row.setMethod(trim(dto.getMethod()));
        row.setPath(trim(dto.getPath()));
        row.setOperatorId(dto.getOperatorId());
        row.setOperatorName(trim(dto.getOperatorName()));
        row.setOperatorRole(trim(dto.getOperatorRole()));
        row.setSuccess(Boolean.FALSE.equals(dto.getSuccess()) ? 0 : 1);
        row.setCostMs(dto.getCostMs());
        row.setRemark(trim(dto.getRemark()));
        sysOperateLogMapper.insert(row);
    }

    private static String trim(String value) {
        return StringUtils.hasText(value) ? value.trim() : null;
    }
}
