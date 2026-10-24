package com.temaconsulting.agente_iso.shared.api.dto;

import java.util.List;

public record Page<T>(
        List<T> items,
        PageInfo page
) {
}
