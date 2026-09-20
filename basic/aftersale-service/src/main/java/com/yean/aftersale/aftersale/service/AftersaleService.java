package com.yean.aftersale.aftersale.service;

import com.yean.aftersale.aftersale.dto.AftersaleActionDTO;
import com.yean.aftersale.aftersale.dto.AftersaleApplyDTO;
import com.yean.aftersale.aftersale.dto.AftersaleQueryDTO;
import com.yean.aftersale.aftersale.dto.AftersaleVO;
import com.yean.aftersale.config.Operator;

import java.util.List;

public interface AftersaleService {

    AftersaleVO apply(AftersaleApplyDTO dto, Operator operator);

    List<AftersaleVO> page(AftersaleQueryDTO query, Operator operator);

    AftersaleVO detail(String aftersaleNo, Operator operator);

    AftersaleVO approve(AftersaleActionDTO dto, Operator operator);

    AftersaleVO reject(AftersaleActionDTO dto, Operator operator);

    AftersaleVO withdraw(AftersaleActionDTO dto, Operator operator);
}
