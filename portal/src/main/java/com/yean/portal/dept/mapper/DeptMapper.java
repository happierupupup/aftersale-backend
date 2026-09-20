package com.yean.portal.dept.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.yean.portal.dept.dto.DeptQueryDTO;
import com.yean.portal.dept.entity.Dept;
import org.apache.ibatis.annotations.Param;

import java.util.List;

public interface DeptMapper extends BaseMapper<Dept> {

    List<Dept> selectDeptList(DeptQueryDTO query);

    int countByParentId(@Param("parentId") Long parentId);
}
