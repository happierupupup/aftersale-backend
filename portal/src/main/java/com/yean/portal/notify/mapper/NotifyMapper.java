package com.yean.portal.notify.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.yean.portal.notify.dto.NotifyQueryDTO;
import com.yean.portal.notify.entity.Notify;
import org.apache.ibatis.annotations.Param;

public interface NotifyMapper extends BaseMapper<Notify> {

    IPage<Notify> selectNotifyList(IPage<Notify> page, @Param("q") NotifyQueryDTO query);
}
