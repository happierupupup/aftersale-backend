package com.yean.trade.order.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

import java.util.ArrayList;
import java.util.List;

@Data
@Schema(description = "订单批量导入结果")
public class OrderBatchResultVO {

    @Schema(description = "文件总行数（不含表头、空行）")
    private int total;

    @Schema(description = "成功条数")
    private int success;

    @Schema(description = "失败条数")
    private int failed;

    @Schema(description = "成功写入的订单号")
    private List<String> orderNos = new ArrayList<>();

    @Schema(description = "失败明细，如「第3行: 订单号已存在」")
    private List<String> errors = new ArrayList<>();
}
