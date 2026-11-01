package com.temaconsulting.agente_iso.verification.api.dto;

/** Describes whether a source was consulted; separate from the compliance decision. */
public enum EvidenceSearchStatus {
    NOT_ATTEMPTED,
    FOUND,
    NOT_FOUND,
    INCOMPLETE,
    UNKNOWN
}
