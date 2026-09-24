package com.yean.portal.dept.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.yean.portal.dept.dto.DeptQueryDTO;
import com.yean.portal.dept.entity.Dept;
import org.apache.ibatis.annotations.Param;

public interface DeptMapper extends BaseMapper<Dept> {

    IPage<Dept> selectDeptList(IPage<Dept> page, @Param("q") DeptQueryDTO query);

    int countByParentId(@Param("parentId") Long parentId);
}
