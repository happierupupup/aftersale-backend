package com.yean.portal.notify.service;

import com.yean.portal.notify.dto.NotifyQueryDTO;
import com.yean.portal.notify.dto.NotifySendDTO;
import com.yean.portal.notify.dto.NotifyVO;

import java.util.List;

public interface NotifyService {

    Long send(NotifySendDTO dto);

    List<NotifyVO> list(NotifyQueryDTO query);

    void markRead(Long id);
}
