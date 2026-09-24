package com.yean.common.result;

import lombok.Data;

import java.util.Collections;
import java.util.List;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * 统一分页出参。
 */
@Data
public class PageResult<T> {

    /** 当前页，从 1 开始 */
    private long page;

    /** 每页条数 */
    private long size;

    /** 总记录数 */
    private long total;

    /** 总页数 */
    private long pages;

    /** 当前页数据 */
    private List<T> records;

    public static <T> PageResult<T> of(long page, long size, long total, List<T> records) {
        PageResult<T> result = new PageResult<>();
        result.setPage(page);
        result.setSize(size);
        result.setTotal(total);
        result.setPages(size <= 0 ? 0 : (total + size - 1) / size);
        result.setRecords(records == null ? Collections.emptyList() : records);
        return result;
    }

    public static <T> PageResult<T> empty(long page, long size) {
        return of(page, size, 0, Collections.emptyList());
    }

    public <R> PageResult<R> map(Function<T, R> mapper) {
        List<R> mapped = records == null ? Collections.emptyList()
                : records.stream().map(mapper).collect(Collectors.toList());
        return of(page, size, total, mapped);
    }
}
