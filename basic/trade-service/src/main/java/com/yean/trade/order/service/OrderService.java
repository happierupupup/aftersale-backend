package com.yean.trade.order.service;

import com.yean.common.dto.Operator;
import com.yean.trade.order.dto.OrderVO;
import com.yean.common.result.PageResult;
import com.yean.trade.order.dto.OrderBatchResultVO;
import com.yean.trade.order.dto.OrderCreateDTO;
import org.springframework.web.multipart.MultipartFile;

public interface OrderService {

    OrderVO getByOrderNo(String orderNo);

    PageResult<OrderVO> listByUserId(Long userId, long page, long size);

    OrderVO create(OrderCreateDTO dto, Operator operator);

    /** CSV 批量导入，表头见接口说明；单行失败不中断，汇总返回。 */
    OrderBatchResultVO upload(MultipartFile file, Operator operator);
}
