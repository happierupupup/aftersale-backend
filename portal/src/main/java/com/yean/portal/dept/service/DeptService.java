package com.yean.portal.dept.service;

import com.yean.common.result.PageResult;
import com.yean.portal.dept.dto.DeptCreateDTO;
import com.yean.portal.dept.dto.DeptQueryDTO;
import com.yean.portal.dept.dto.DeptUpdateDTO;
import com.yean.portal.dept.dto.DeptVO;

import java.util.List;

public interface DeptService {

    DeptVO getById(Long id);

    PageResult<DeptVO> list(DeptQueryDTO query);

    List<DeptVO> tree();

    Long create(DeptCreateDTO dto);

    void update(Long id, DeptUpdateDTO dto);

    void delete(Long id);
}
