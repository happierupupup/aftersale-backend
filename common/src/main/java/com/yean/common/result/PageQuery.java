package com.yean.common.result;

import lombok.Data;

/**
 * 统一分页入参。page 从 1 起；size 默认 10，上限 50。
 */
@Data
public class PageQuery {

    public static final long DEFAULT_PAGE = 1L;
    public static final long DEFAULT_SIZE = 10L;
    public static final long MAX_SIZE = 50L;

    /** 页码，从 1 开始 */
    private Long page;

    /** 每页条数，默认 10，最大 50 */
    private Long size;

    public long pageOrDefault() {
        if (page == null || page < 1) {
            return DEFAULT_PAGE;
        }
        return page;
    }

    public long sizeOrDefault() {
        if (size == null || size < 1) {
            return DEFAULT_SIZE;
        }
        return Math.min(size, MAX_SIZE);
    }
}
