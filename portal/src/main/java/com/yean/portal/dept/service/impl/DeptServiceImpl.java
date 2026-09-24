package com.yean.portal.dept.service.impl;

import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.yean.common.enums.ErrorEnum;
import com.yean.common.exception.BizException;
import com.yean.common.result.PageResult;
import com.yean.portal.config.RedisLock;
import com.yean.portal.dept.dto.DeptCreateDTO;
import com.yean.portal.dept.dto.DeptQueryDTO;
import com.yean.portal.dept.dto.DeptUpdateDTO;
import com.yean.portal.dept.dto.DeptVO;
import com.yean.portal.dept.entity.Dept;
import com.yean.portal.dept.mapper.DeptMapper;
import com.yean.portal.dept.service.DeptService;
import com.yean.portal.user.mapper.UserMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class DeptServiceImpl implements DeptService {

    private final DeptMapper deptMapper;
    private final UserMapper userMapper;
    private final RedisLock redisLock;

    @Override
    public DeptVO getById(Long id) {
        DeptVO found = findInForest(buildForest(), id);
        if (found == null) {
            throw new BizException(ErrorEnum.DEPT_NOT_FOUND);
        }
        return found;
    }

    @Override
    public PageResult<DeptVO> list(DeptQueryDTO query) {
        DeptQueryDTO cond = query == null ? new DeptQueryDTO() : query;
        IPage<Dept> result = deptMapper.selectDeptList(new Page<>(cond.pageOrDefault(), cond.sizeOrDefault()), cond);
        return PageResult.of(result.getCurrent(), result.getSize(), result.getTotal(),
                result.getRecords().stream().map(this::toVO).collect(Collectors.toList()));
    }

    @Override
    public List<DeptVO> tree() {
        return buildForest();
    }

    private List<DeptVO> buildForest() {
        List<DeptVO> all = deptMapper.selectList(null).stream()
                .map(this::toVO)
                .collect(Collectors.toList());
        Map<Long, DeptVO> map = all.stream().collect(Collectors.toMap(DeptVO::getId, d -> d));
        List<DeptVO> roots = new ArrayList<>();
        for (DeptVO node : all) {
            Long parentId = node.getParentId();
            if (parentId == null || parentId == 0L || !map.containsKey(parentId)) {
                roots.add(node);
                continue;
            }
            map.get(parentId).getChildren().add(node);
        }
        return roots;
    }

    private DeptVO findInForest(List<DeptVO> nodes, Long id) {
        for (DeptVO node : nodes) {
            if (Objects.equals(node.getId(), id)) {
                return node;
            }
            DeptVO nested = findInForest(node.getChildren(), id);
            if (nested != null) {
                return nested;
            }
        }
        return null;
    }

    @Override
    public Long create(DeptCreateDTO dto) {
        return redisLock.execute("lock:dept:parent:" + dto.getParentId(), () -> {
            Dept dept = new Dept();
            dept.setName(dto.getName());
            dept.setParentId(dto.getParentId());
            dept.setLevel(resolveLevel(dto.getParentId()));
            deptMapper.insert(dept);
            return dept.getId();
        });
    }

    @Override
    public void update(Long id, DeptUpdateDTO dto) {
        redisLock.execute("lock:dept:" + id, () -> {
            Dept dept = requireDept(id);
            if (StringUtils.hasText(dto.getName())) {
                dept.setName(dto.getName());
            }
            if (dto.getParentId() != null) {
                if (Objects.equals(dto.getParentId(), id)) {
                    throw new BizException(ErrorEnum.DEPT_PARENT_SELF);
                }
                dept.setParentId(dto.getParentId());
                dept.setLevel(resolveLevel(dto.getParentId()));
            }
            deptMapper.updateById(dept);
        });
    }

    @Override
    public void delete(Long id) {
        redisLock.execute("lock:dept:" + id, () -> {
            requireDept(id);
            if (deptMapper.countByParentId(id) > 0) {
                throw new BizException(ErrorEnum.DEPT_HAS_CHILDREN);
            }
            if (userMapper.countByDeptId(id) > 0) {
                throw new BizException(ErrorEnum.DEPT_HAS_USERS);
            }
            deptMapper.deleteById(id);
        });
    }

    private Dept requireDept(Long id) {
        Dept dept = deptMapper.selectById(id);
        if (dept == null) {
            throw new BizException(ErrorEnum.DEPT_NOT_FOUND);
        }
        return dept;
    }

    private int resolveLevel(Long parentId) {
        if (parentId == null || parentId == 0) {
            return 1;
        }
        Dept parent = deptMapper.selectById(parentId);
        if (parent == null) {
            throw new BizException(ErrorEnum.DEPT_PARENT_NOT_FOUND);
        }
        return parent.getLevel() + 1;
    }

    private DeptVO toVO(Dept dept) {
        DeptVO vo = new DeptVO();
        vo.setId(dept.getId());
        vo.setName(dept.getName());
        vo.setParentId(dept.getParentId());
        vo.setLevel(dept.getLevel());
        vo.setCreateTime(dept.getCreateTime());
        return vo;
    }
}
