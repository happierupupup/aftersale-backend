package com.yean.portal.notify.service;

import com.yean.common.result.PageResult;
import com.yean.portal.notify.dto.NotifyQueryDTO;
import com.yean.portal.notify.dto.NotifySendDTO;
import com.yean.portal.notify.dto.NotifyVO;

public interface NotifyService {

    Long send(NotifySendDTO dto);

    PageResult<NotifyVO> list(NotifyQueryDTO query);

    void markRead(Long id);
}
