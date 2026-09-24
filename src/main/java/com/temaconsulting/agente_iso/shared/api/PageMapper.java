package com.temaconsulting.agente_iso.shared.api;

import com.temaconsulting.agente_iso.shared.dto.PageInfo;

public class PageMapper {
    public static PageInfo toPageInfo(int page, int size, long totalElements) {
        long totalPages = totalElements / size
                + (totalElements % size == 0 ? 0 : 1);

        return new PageInfo(page, size, totalElements, Math.toIntExact(totalPages));
    }
}
