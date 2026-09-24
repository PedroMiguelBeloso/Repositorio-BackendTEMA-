package com.temaconsulting.agente_iso.shared.dto;

public record PageInfo(
        int page,
        int size,
        long totalElements,
        int totalPages
) {}

