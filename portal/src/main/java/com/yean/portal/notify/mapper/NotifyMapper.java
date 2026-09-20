package com.yean.portal.notify.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.yean.portal.notify.dto.NotifyQueryDTO;
import com.yean.portal.notify.entity.Notify;

import java.util.List;

public interface NotifyMapper extends BaseMapper<Notify> {

    List<Notify> selectNotifyList(NotifyQueryDTO query);
}
