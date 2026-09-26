package com.temaconsulting.agente_iso.shared.dto;

import java.util.List;

public record Page<T>(
        List<T> items,
        PageInfo page
) {
}
