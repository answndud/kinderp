package com.kinderp.global.common;

import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;

public final class PageRequests {

    public static final int DEFAULT_PAGE_SIZE = 20;
    public static final int MAX_PAGE_SIZE = 100;

    private PageRequests() {
    }

    public static Pageable of(int page, int size) {
        return PageRequest.of(normalizePage(page), normalizeSize(size, DEFAULT_PAGE_SIZE));
    }

    public static Pageable of(int page, int size, Sort sort) {
        return PageRequest.of(normalizePage(page), normalizeSize(size, DEFAULT_PAGE_SIZE), sort);
    }

    public static Pageable of(int page, int size, int defaultSize, Sort sort) {
        return PageRequest.of(normalizePage(page), normalizeSize(size, defaultSize), sort);
    }

    public static PageRequest pageRequest(int page, int size, Sort sort) {
        return PageRequest.of(normalizePage(page), normalizeSize(size, DEFAULT_PAGE_SIZE), sort);
    }

    public static PageRequest pageRequest(int page, int size, int defaultSize, Sort sort) {
        return PageRequest.of(normalizePage(page), normalizeSize(size, defaultSize), sort);
    }

    public static int normalizePage(int page) {
        return Math.max(page, 0);
    }

    public static int normalizeSize(int size, int defaultSize) {
        int resolvedDefault = defaultSize > 0 ? defaultSize : DEFAULT_PAGE_SIZE;
        if (size <= 0) {
            return resolvedDefault;
        }
        return Math.min(size, MAX_PAGE_SIZE);
    }
}
