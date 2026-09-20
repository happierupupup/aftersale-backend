package com.yean.aftersale.aftersale.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.yean.aftersale.aftersale.dto.AftersaleOperLog;
import org.apache.ibatis.annotations.Param;

import java.util.List;

public interface AftersaleOperLogMapper extends BaseMapper<AftersaleOperLog> {

    List<AftersaleOperLog> selectByAftersaleNo(@Param("aftersaleNo") String aftersaleNo);
}
