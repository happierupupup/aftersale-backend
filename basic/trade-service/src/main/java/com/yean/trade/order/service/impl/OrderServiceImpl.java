package com.yean.trade.order.service.impl;

import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.yean.common.enums.ErrorEnum;
import com.yean.common.exception.BizException;
import com.yean.common.dto.Operator;
import com.yean.trade.order.dto.OrderVO;
import com.yean.common.result.PageResult;
import com.yean.trade.order.dto.Order;
import com.yean.trade.order.dto.OrderBatchResultVO;
import com.yean.trade.order.dto.OrderCreateDTO;
import com.yean.trade.order.mapper.OrderMapper;
import com.yean.trade.order.service.OrderService;
import lombok.RequiredArgsConstructor;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;
import org.springframework.web.multipart.MultipartFile;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class OrderServiceImpl implements OrderService {

    private static final long DEFAULT_MERCHANT_ID = 2L;
    private static final int DEFAULT_STATUS = 1;
    private static final int DEFAULT_PAY_STATUS = 1;
    private static final int MAX_IMPORT_ROWS = 2000;
    private static final List<String> REQUIRED_HEADERS = List.of(
            "userid", "merchantid", "skuid", "skuname", "num", "amount");

    private final OrderMapper orderMapper;
    private final StringRedisTemplate stringRedisTemplate;

    @Override
    public OrderVO getByOrderNo(String orderNo) {
        Order order = orderMapper.selectByOrderNo(orderNo);
        if (order == null) {
            throw new BizException(ErrorEnum.ORDER_NOT_FOUND);
        }
        return toVO(order);
    }

    @Override
    public PageResult<OrderVO> listByUserId(Long userId, long page, long size) {
        IPage<Order> result = orderMapper.selectByUserId(new Page<>(page, size), userId);
        return PageResult.of(result.getCurrent(), result.getSize(), result.getTotal(),
                result.getRecords().stream().map(this::toVO).collect(Collectors.toList()));
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public OrderVO create(OrderCreateDTO dto, Operator operator) {
        OrderCreateDTO normalized = normalizeCreate(dto, operator);
        Order row = toEntity(normalized);
        try {
            orderMapper.insert(row);
        } catch (DuplicateKeyException e) {
            throw new BizException(ErrorEnum.ORDER_EXISTS);
        }
        return toVO(orderMapper.selectByOrderNo(row.getOrderNo()));
    }

    @Override
    public OrderBatchResultVO upload(MultipartFile file, Operator operator) {
        if (file == null || file.isEmpty()) {
            throw new BizException(ErrorEnum.ORDER_IMPORT_EMPTY);
        }
        String filename = file.getOriginalFilename() == null ? "" : file.getOriginalFilename().toLowerCase(Locale.ROOT);
        if (!filename.endsWith(".csv") && !filename.endsWith(".txt")) {
            throw new BizException(ErrorEnum.ORDER_IMPORT_FORMAT, "仅支持 .csv / .txt");
        }

        List<String> lines = new ArrayList<>();
        try (BufferedReader reader = new BufferedReader(
                new InputStreamReader(file.getInputStream(), StandardCharsets.UTF_8))) {
            String line;
            while ((line = reader.readLine()) != null) {
                // 去掉 UTF-8 BOM
                if (lines.isEmpty() && line.startsWith("\uFEFF")) {
                    line = line.substring(1);
                }
                if (StringUtils.hasText(line)) {
                    lines.add(line.trim());
                }
            }
        } catch (Exception e) {
            throw new BizException(ErrorEnum.ORDER_IMPORT_FORMAT, e.getMessage());
        }
        if (lines.size() < 2) {
            throw new BizException(ErrorEnum.ORDER_IMPORT_EMPTY);
        }

        Map<String, Integer> headerIndex = parseHeader(lines.get(0));
        OrderBatchResultVO result = new OrderBatchResultVO();
        int dataRows = lines.size() - 1;
        if (dataRows > MAX_IMPORT_ROWS) {
            throw new BizException(ErrorEnum.ORDER_IMPORT_FORMAT, "单次最多导入 " + MAX_IMPORT_ROWS + " 行");
        }
        result.setTotal(dataRows);

        for (int i = 1; i < lines.size(); i++) {
            int rowNo = i + 1;
            try {
                OrderCreateDTO dto = parseRow(lines.get(i), headerIndex);
                OrderVO vo = create(dto, operator);
                result.setSuccess(result.getSuccess() + 1);
                result.getOrderNos().add(vo.getOrderNo());
            } catch (BizException e) {
                result.setFailed(result.getFailed() + 1);
                result.getErrors().add("第" + rowNo + "行: " + e.getMessage());
            } catch (Exception e) {
                result.setFailed(result.getFailed() + 1);
                result.getErrors().add("第" + rowNo + "行: " + e.getMessage());
            }
        }
        return result;
    }

    private OrderCreateDTO normalizeCreate(OrderCreateDTO dto, Operator operator) {
        if (dto == null) {
            throw new BizException(ErrorEnum.PARAM_INVALID);
        }
        Long userId = dto.getUserId();
        if (userId == null) {
            if (operator == null || operator.getUserId() == null) {
                throw new BizException(ErrorEnum.UNAUTHORIZED);
            }
            userId = operator.getUserId();
        }
        OrderCreateDTO n = new OrderCreateDTO();
        n.setOrderNo(StringUtils.hasText(dto.getOrderNo()) ? dto.getOrderNo().trim() : nextOrderNo());
        n.setUserId(userId);
        n.setMerchantId(dto.getMerchantId() == null ? DEFAULT_MERCHANT_ID : dto.getMerchantId());
        n.setSkuId(dto.getSkuId());
        n.setSkuName(dto.getSkuName() == null ? null : dto.getSkuName().trim());
        n.setNum(dto.getNum());
        n.setAmount(dto.getAmount());
        n.setStatus(dto.getStatus() == null ? DEFAULT_STATUS : dto.getStatus());
        n.setPayStatus(dto.getPayStatus() == null ? DEFAULT_PAY_STATUS : dto.getPayStatus());
        validateStatus(n.getStatus(), n.getPayStatus());
        if (orderMapper.selectByOrderNo(n.getOrderNo()) != null) {
            throw new BizException(ErrorEnum.ORDER_EXISTS);
        }
        return n;
    }

    private static void validateStatus(Integer status, Integer payStatus) {
        if (status == null || status < 1 || status > 4) {
            throw new BizException(ErrorEnum.PARAM_INVALID.getCode(), "status 须为 1~4");
        }
        if (payStatus == null || (payStatus != 0 && payStatus != 1)) {
            throw new BizException(ErrorEnum.PARAM_INVALID.getCode(), "payStatus 须为 0 或 1");
        }
    }

    private Order toEntity(OrderCreateDTO dto) {
        Order row = new Order();
        row.setOrderNo(dto.getOrderNo());
        row.setUserId(dto.getUserId());
        row.setMerchantId(dto.getMerchantId());
        row.setSkuId(dto.getSkuId());
        row.setSkuName(dto.getSkuName());
        row.setNum(dto.getNum());
        row.setAmount(dto.getAmount());
        row.setStatus(dto.getStatus());
        row.setPayStatus(dto.getPayStatus());
        return row;
    }

    private String nextOrderNo() {
        String day = LocalDate.now().format(DateTimeFormatter.BASIC_ISO_DATE);
        String key = "order:seq:" + day;
        Long seq = stringRedisTemplate.opsForValue().increment(key);
        if (seq != null && seq == 1L) {
            stringRedisTemplate.expire(key, Duration.ofHours(48));
        }
        return "ORD" + day + String.format("%06d", seq == null ? 1 : seq);
    }

    private Map<String, Integer> parseHeader(String headerLine) {
        String[] cols = splitCsv(headerLine);
        Map<String, Integer> index = new HashMap<>();
        for (int i = 0; i < cols.length; i++) {
            String key = cols[i].trim().toLowerCase(Locale.ROOT).replace("_", "");
            index.put(key, i);
        }
        for (String required : REQUIRED_HEADERS) {
            if (!index.containsKey(required)) {
                throw new BizException(ErrorEnum.ORDER_IMPORT_FORMAT,
                        "缺少列 " + required + "，表头示例: orderNo,userId,merchantId,skuId,skuName,num,amount,status,payStatus");
            }
        }
        return index;
    }

    private OrderCreateDTO parseRow(String line, Map<String, Integer> headerIndex) {
        String[] cols = splitCsv(line);
        OrderCreateDTO dto = new OrderCreateDTO();
        dto.setOrderNo(cell(cols, headerIndex, "orderno"));
        dto.setUserId(parseLong(cell(cols, headerIndex, "userid"), "userId"));
        String merchant = cell(cols, headerIndex, "merchantid");
        if (StringUtils.hasText(merchant)) {
            dto.setMerchantId(parseLong(merchant, "merchantId"));
        }
        dto.setSkuId(parseLong(cell(cols, headerIndex, "skuid"), "skuId"));
        dto.setSkuName(cell(cols, headerIndex, "skuname"));
        dto.setNum(parseInt(cell(cols, headerIndex, "num"), "num"));
        dto.setAmount(parseDecimal(cell(cols, headerIndex, "amount"), "amount"));
        String status = cell(cols, headerIndex, "status");
        if (StringUtils.hasText(status)) {
            dto.setStatus(parseInt(status, "status"));
        }
        String payStatus = cell(cols, headerIndex, "paystatus");
        if (StringUtils.hasText(payStatus)) {
            dto.setPayStatus(parseInt(payStatus, "payStatus"));
        }
        if (!StringUtils.hasText(dto.getSkuName())) {
            throw new BizException(ErrorEnum.PARAM_INVALID.getCode(), "skuName 不能为空");
        }
        if (dto.getSkuId() == null || dto.getNum() == null || dto.getAmount() == null) {
            throw new BizException(ErrorEnum.PARAM_INVALID);
        }
        return dto;
    }

    private static String cell(String[] cols, Map<String, Integer> headerIndex, String key) {
        Integer idx = headerIndex.get(key);
        if (idx == null || idx < 0 || idx >= cols.length) {
            return null;
        }
        String v = cols[idx].trim();
        return v.isEmpty() ? null : v;
    }

    private static String[] splitCsv(String line) {
        // 简单 CSV：按逗号切，支持双引号包裹字段
        List<String> parts = new ArrayList<>();
        StringBuilder cur = new StringBuilder();
        boolean inQuote = false;
        for (int i = 0; i < line.length(); i++) {
            char c = line.charAt(i);
            if (c == '"') {
                inQuote = !inQuote;
                continue;
            }
            if (c == ',' && !inQuote) {
                parts.add(cur.toString());
                cur.setLength(0);
                continue;
            }
            cur.append(c);
        }
        parts.add(cur.toString());
        return parts.toArray(new String[0]);
    }

    private static Long parseLong(String raw, String field) {
        if (!StringUtils.hasText(raw)) {
            return null;
        }
        try {
            return Long.parseLong(raw.trim());
        } catch (NumberFormatException e) {
            throw new BizException(ErrorEnum.PARAM_INVALID.getCode(), field + " 不是合法数字");
        }
    }

    private static Integer parseInt(String raw, String field) {
        if (!StringUtils.hasText(raw)) {
            return null;
        }
        try {
            return Integer.parseInt(raw.trim());
        } catch (NumberFormatException e) {
            throw new BizException(ErrorEnum.PARAM_INVALID.getCode(), field + " 不是合法整数");
        }
    }

    private static BigDecimal parseDecimal(String raw, String field) {
        if (!StringUtils.hasText(raw)) {
            return null;
        }
        try {
            return new BigDecimal(raw.trim());
        } catch (NumberFormatException e) {
            throw new BizException(ErrorEnum.PARAM_INVALID.getCode(), field + " 不是合法金额");
        }
    }

    private OrderVO toVO(Order order) {
        OrderVO vo = new OrderVO();
        vo.setOrderNo(order.getOrderNo());
        vo.setUserId(order.getUserId());
        vo.setMerchantId(order.getMerchantId());
        vo.setSkuId(order.getSkuId());
        vo.setSkuName(order.getSkuName());
        vo.setNum(order.getNum());
        vo.setAmount(order.getAmount());
        vo.setStatus(order.getStatus());
        vo.setPayStatus(order.getPayStatus());
        vo.setCreateTime(order.getCreateTime());
        return vo;
    }
}
