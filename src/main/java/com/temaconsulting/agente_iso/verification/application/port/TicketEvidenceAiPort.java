package com.temaconsulting.agente_iso.verification.application.port;

import com.temaconsulting.agente_iso.integration.domain.TicketSnapshot;
import com.temaconsulting.agente_iso.verification.domain.ResultStatus;

import java.util.List;

/** Interprets ticket evidence only after Redmine identity/project checks have succeeded. */
public interface TicketEvidenceAiPort {
    Assessment assess(String expectationName, String canonicalName, String officialNomenclature,
                      List<String> policies, List<String> instructions, TicketSnapshot ticket);

    record Assessment(ResultStatus status, String rationale, List<String> limitations,
                      List<String> citedFields, String model, String responseId) {
        public Assessment {
            if (status == null || rationale == null || rationale.isBlank())
                throw new IllegalArgumentException("Ticket assessment requires status and rationale");
            limitations = limitations == null ? List.of() : List.copyOf(limitations);
            citedFields = citedFields == null ? List.of() : List.copyOf(citedFields);
        }
    }
}
