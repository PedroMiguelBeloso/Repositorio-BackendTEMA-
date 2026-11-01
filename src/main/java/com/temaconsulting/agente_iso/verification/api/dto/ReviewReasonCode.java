package com.temaconsulting.agente_iso.verification.api.dto;

/** Stable reason exposed to clients when the automatic proposal needs review. */
public enum ReviewReasonCode {
    APPLICABILITY_PENDING,
    MISSING_TICKET_SELECTION,
    SOURCE_NOT_CONFIGURED,
    SOURCE_UNAVAILABLE,
    SOURCE_DATA_INVALID,
    SEARCH_INCOMPLETE,
    MULTIPLE_MATCHES,
    RULE_NOT_DEFINED,
    AI_INCONCLUSIVE,
    UNSUPPORTED_SOURCE,
    OTHER
}
