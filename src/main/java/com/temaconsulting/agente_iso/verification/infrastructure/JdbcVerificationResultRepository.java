package com.temaconsulting.agente_iso.verification.infrastructure;

import com.temaconsulting.agente_iso.catalog.domain.EvidenceType;
import com.temaconsulting.agente_iso.integration.domain.IntegrationSource;
import com.temaconsulting.agente_iso.shared.application.Page;
import com.temaconsulting.agente_iso.verification.application.ApplicabilityAssessment;
import com.temaconsulting.agente_iso.verification.application.VerificationResultConflictException;
import com.temaconsulting.agente_iso.verification.application.VerificationResultProgress;
import com.temaconsulting.agente_iso.verification.application.VerificationResultRepository;
import com.temaconsulting.agente_iso.verification.application.VerificationResultValidationException;
import com.temaconsulting.agente_iso.verification.application.VerificationResultWriteCommand;
import com.temaconsulting.agente_iso.verification.domain.ApplicabilityDecision;
import com.temaconsulting.agente_iso.verification.domain.DecisionOrigin;
import com.temaconsulting.agente_iso.verification.domain.HumanApplicabilityDecision;
import com.temaconsulting.agente_iso.verification.domain.HumanDecisionEvent;
import com.temaconsulting.agente_iso.verification.domain.HumanDecisionType;
import com.temaconsulting.agente_iso.verification.domain.ResultStatus;
import com.temaconsulting.agente_iso.verification.domain.VerificationEvidenceReference;
import com.temaconsulting.agente_iso.verification.domain.VerificationResult;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowCallbackHandler;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;

@Repository
public class JdbcVerificationResultRepository implements VerificationResultRepository {
    private static final Comparator<VerificationEvidenceReference> EVIDENCE_ORDER =
            Comparator.comparing(VerificationEvidenceReference::observedAt)
                    .thenComparing(reference -> reference.kind().name())
                    .thenComparing(reference -> reference.source().name())
                    .thenComparing(VerificationEvidenceReference::externalId)
                    .thenComparing(VerificationEvidenceReference::displayName)
                    .thenComparing(
                            VerificationEvidenceReference::ticketId,
                            Comparator.nullsFirst(Comparator.naturalOrder())
                    )
                    .thenComparing(
                            VerificationEvidenceReference::webUrl,
                            Comparator.nullsFirst(Comparator.naturalOrder())
                    );

    private final JdbcTemplate jdbc;

    public JdbcVerificationResultRepository(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    @Override
    @Transactional
    public VerificationResult save(VerificationResultWriteCommand command) {
        Expectation expectation = findExpectation(command.runId(), command.expectationId())
                .orElseThrow(() -> new VerificationResultValidationException(
                        "expectationId",
                        "Expectation does not belong to the run version and scope"
                ));
        validateCommand(command, expectation);
        if (expectation.evidenceType() == EvidenceType.TICKET) {
            validateSelectedTicket(command, expectation);
        } else if (command.subjectTicketId() != null) {
            throw new VerificationResultValidationException(
                    "subjectTicketId",
                    "Only TICKET expectations may have a subjectTicketId"
            );
        }

        UUID resultId = UUID.randomUUID();
        List<UUID> inserted = jdbc.query(
                """
                INSERT INTO verification_results (
                    id, run_id, expectation_id, macro_activity_id, activity_id,
                    subject_ticket_id, display_name, proposed_status, effective_status,
                    applicability_decision, applicability_origin, applicability_rationale,
                    rationale, revision
                ) VALUES (?, ?, ?, ?, ?, ?, ?, ?::result_status, ?::result_status,
                          ?::applicability_decision, ?::decision_origin, ?, ?, 1)
                ON CONFLICT DO NOTHING
                RETURNING id
                """,
                (rs, rowNum) -> rs.getObject("id", UUID.class),
                resultId,
                command.runId(),
                command.expectationId(),
                expectation.macroActivityId(),
                expectation.activityId(),
                command.subjectTicketId(),
                expectation.displayName(),
                command.proposedStatus().name(),
                command.proposedStatus().name(),
                command.applicabilityDecision().name(),
                command.applicabilityOrigin().name(),
                command.applicabilityRationale(),
                command.rationale()
        );

        if (!inserted.isEmpty()) {
            insertRelatedRows(resultId, command);
            return loadResults(inserted).getFirst();
        }

        UUID existingId = findExistingId(command);
        if (existingId == null) {
            throw new IllegalStateException("Result insert conflicted but no matching result was found");
        }
        VerificationResult existing = loadResults(List.of(existingId)).getFirst();
        if (!sameEvaluation(existing, command, expectation)) {
            throw new VerificationResultConflictException(
                    "A different evaluation is already stored for this run, expectation and ticket"
            );
        }
        return existing;
    }

    @Override
    public VerificationResultProgress progress(UUID runId) {
        return jdbc.queryForObject(
                """
                SELECT COUNT(*) AS result_count,
                       COUNT(*) FILTER (WHERE effective_status = 'REVIEW_REQUIRED'::result_status)
                           AS review_required_count
                FROM verification_results
                WHERE run_id = ?
                """,
                (rs, rowNum) -> new VerificationResultProgress(
                        rs.getLong("result_count"),
                        rs.getLong("review_required_count")
                ),
                runId
        );
    }

    @Override
    public Optional<String> findRunStatus(UUID runId) {
        return jdbc.query(
                "SELECT status::text FROM verification_runs WHERE id = ?",
                (rs, rowNum) -> rs.getString(1),
                runId
        ).stream().findFirst();
    }

    @Override
    public Page<VerificationResult> findPage(UUID runId, int page, int size) {
        Long total = jdbc.queryForObject(
                "SELECT COUNT(*) FROM verification_results WHERE run_id = ?",
                Long.class,
                runId
        );
        long offset = Math.multiplyExact((long) page, size);
        List<UUID> ids = jdbc.query(
                """
                SELECT vr.id
                FROM verification_results vr
                JOIN evidence_expectations ee ON ee.id = vr.expectation_id
                JOIN macro_activities ma ON ma.id = ee.macro_activity_id
                WHERE vr.run_id = ?
                ORDER BY ma."order", ee."order", vr.subject_ticket_id NULLS FIRST, vr.id
                LIMIT ? OFFSET ?
                """,
                (rs, rowNum) -> rs.getObject("id", UUID.class),
                runId,
                size,
                offset
        );
        return new Page<>(
                loadResults(ids),
                page,
                size,
                total == null ? 0 : total
        );
    }

    private Optional<Expectation> findExpectation(UUID runId, UUID expectationId) {
        List<Expectation> matches = jdbc.query(
                """
                SELECT ee.id, ee.macro_activity_id, ee.activity_id,
                       COALESCE(ee.display_name, rd.canonical_name) AS display_name,
                       ee.location::text AS location, rd.evidence_type::text AS evidence_type
                FROM verification_runs r
                JOIN evidence_expectations ee ON ee.id = ?
                JOIN macro_activities ma ON ma.id = ee.macro_activity_id
                JOIN registry_definitions rd ON rd.id = ee.registry_definition_id
                WHERE r.id = ?
                  AND ma.process_version_id = r.process_version_id
                  AND (
                      r.scope = 'PROCESS'::verification_scope
                      OR (r.scope = 'MACRO_ACTIVITY'::verification_scope
                          AND r.macro_activity_id = ee.macro_activity_id)
                  )
                """,
                (rs, rowNum) -> new Expectation(
                        rs.getObject("id", UUID.class),
                        rs.getObject("macro_activity_id", UUID.class),
                        rs.getObject("activity_id", UUID.class),
                        rs.getString("display_name"),
                        EvidenceType.valueOf(rs.getString("evidence_type")),
                        com.temaconsulting.agente_iso.process.domain.EvidenceLocation
                                .valueOf(rs.getString("location"))
                ),
                expectationId,
                runId
        );
        if (!matches.isEmpty()) {
            return Optional.of(matches.getFirst());
        }
        boolean runExists = Boolean.TRUE.equals(jdbc.queryForObject(
                "SELECT EXISTS (SELECT 1 FROM verification_runs WHERE id = ?)",
                Boolean.class,
                runId
        ));
        if (!runExists) {
            throw new VerificationResultValidationException("runId", "Verification run does not exist");
        }
        return Optional.empty();
    }

    private void validateCommand(VerificationResultWriteCommand command, Expectation expectation) {
        if (!expectation.id().equals(command.expectationId())
                || !expectation.macroActivityId().equals(command.macroActivityId())
                || !Objects.equals(expectation.activityId(), command.activityId())
                || !expectation.displayName().equals(command.displayName())) {
            throw new VerificationResultValidationException(
                    "expectationId",
                    "Result metadata must match the selected published expectation"
            );
        }
        if (expectation.evidenceType() == EvidenceType.TICKET && command.subjectTicketId() == null) {
            throw new VerificationResultValidationException(
                    "subjectTicketId",
                    "A TICKET result must identify the selected ticket"
            );
        }
        if (expectation.evidenceType() != EvidenceType.TICKET && command.subjectTicketId() != null) {
            throw new VerificationResultValidationException(
                    "subjectTicketId",
                    "Only TICKET expectations may have a subjectTicketId"
            );
        }
    }

    private void validateSelectedTicket(VerificationResultWriteCommand command, Expectation expectation) {
        String groupId = "ticket-group:" + expectation.macroActivityId();
        boolean selected = Boolean.TRUE.equals(jdbc.queryForObject(
                """
                SELECT EXISTS (
                    SELECT 1 FROM verification_ticket_selections
                    WHERE run_id = ? AND group_id = ? AND ticket_id = ?
                )
                """,
                Boolean.class,
                command.runId(),
                groupId,
                command.subjectTicketId()
        ));
        if (!selected) {
            throw new VerificationResultValidationException(
                    "subjectTicketId",
                    "Ticket is not selected for the expectation's ticket group"
            );
        }
    }

    private void insertRelatedRows(UUID resultId, VerificationResultWriteCommand command) {
        for (int index = 0; index < command.limitations().size(); index++) {
            jdbc.update(
                    """
                    INSERT INTO verification_result_limitations (result_id, position, text)
                    VALUES (?, ?, ?)
                    """,
                    resultId,
                    index + 1,
                    command.limitations().get(index)
            );
        }
        for (int index = 0; index < command.applicabilityLimitations().size(); index++) {
            jdbc.update(
                    """
                    INSERT INTO verification_result_applicability_limitations (result_id, position, text)
                    VALUES (?, ?, ?)
                    """,
                    resultId,
                    index + 1,
                    command.applicabilityLimitations().get(index)
            );
        }
        for (VerificationResultWriteCommand.EvidenceReference reference : command.evidence()) {
            jdbc.update(
                    """
                    INSERT INTO verification_result_evidence (
                        result_id, kind, source, external_id, display_name, web_url, ticket_id, observed_at
                    ) VALUES (?, ?::evidence_type, ?::source_type, ?, ?, ?, ?, ?)
                    """,
                    resultId,
                    reference.kind().name(),
                    reference.source().name(),
                    reference.externalId(),
                    reference.displayName(),
                    reference.webUrl(),
                    reference.ticketId(),
                    Timestamp.from(reference.observedAt())
            );
        }
    }

    private UUID findExistingId(VerificationResultWriteCommand command) {
        return jdbc.query(
                """
                SELECT id FROM verification_results
                WHERE run_id = ? AND expectation_id = ?
                  AND subject_ticket_id IS NOT DISTINCT FROM ?
                """,
                (rs, rowNum) -> rs.getObject("id", UUID.class),
                command.runId(),
                command.expectationId(),
                command.subjectTicketId()
        ).stream().findFirst().orElse(null);
    }

    private List<VerificationResult> loadResults(List<UUID> ids) {
        if (ids.isEmpty()) {
            return List.of();
        }
        String placeholders = String.join(",", Collections.nCopies(ids.size(), "?"));
        Object[] arguments = ids.toArray();
        List<ResultRow> rows = jdbc.query(
                """
                SELECT vr.id, vr.run_id, vr.expectation_id, vr.macro_activity_id, vr.activity_id,
                       vr.display_name, vr.subject_ticket_id, ee.location::text AS location,
                       rd.evidence_type::text AS evidence_type, vr.proposed_status::text AS proposed_status,
                       vr.effective_status::text AS effective_status,
                       vr.applicability_decision::text AS applicability_decision,
                       vr.applicability_origin::text AS applicability_origin,
                       vr.applicability_rationale, vr.rationale, vr.revision
                FROM verification_results vr
                JOIN evidence_expectations ee ON ee.id = vr.expectation_id
                JOIN registry_definitions rd ON rd.id = ee.registry_definition_id
                WHERE vr.id IN (%s)
                ORDER BY vr.id
                """.formatted(placeholders),
                (rs, rowNum) -> mapResultRow(rs),
                arguments
        );
        Map<UUID, List<String>> resultLimitations = emptyLists(rows);
        Map<UUID, List<String>> applicabilityLimitations = emptyLists(rows);
        Map<UUID, List<VerificationEvidenceReference>> evidence = emptyLists(rows);
        Map<UUID, List<HumanDecisionEvent>> decisions = emptyLists(rows);

        jdbc.query(
                """
                SELECT result_id, text
                FROM verification_result_limitations
                WHERE result_id IN (%s)
                ORDER BY result_id, position
                """.formatted(placeholders),
                (RowCallbackHandler) rs -> {
                    resultLimitations.get(rs.getObject("result_id", UUID.class)).add(rs.getString("text"));
                },
                arguments
        );
        jdbc.query(
                """
                SELECT result_id, text
                FROM verification_result_applicability_limitations
                WHERE result_id IN (%s)
                ORDER BY result_id, position
                """.formatted(placeholders),
                (RowCallbackHandler) rs -> {
                    applicabilityLimitations.get(rs.getObject("result_id", UUID.class)).add(rs.getString("text"));
                },
                arguments
        );
        jdbc.query(
                """
                SELECT result_id, kind::text AS kind, source::text AS source, external_id,
                       display_name, web_url, ticket_id, observed_at
                FROM verification_result_evidence
                WHERE result_id IN (%s)
                ORDER BY result_id, observed_at, kind::text, source::text, external_id,
                         display_name, ticket_id, web_url
                """.formatted(placeholders),
                (RowCallbackHandler) rs -> {
                    evidence.get(rs.getObject("result_id", UUID.class)).add(mapEvidence(rs));
                },
                arguments
        );
        jdbc.query(
                """
                SELECT result_id, id, decision_type::text AS decision_type,
                       applicability_value::text AS applicability_value,
                       status_value::text AS status_value, rationale, decided_by, decided_at
                FROM human_decisions
                WHERE result_id IN (%s)
                ORDER BY result_id, decided_at, id
                """.formatted(placeholders),
                (RowCallbackHandler) rs -> {
                    decisions.get(rs.getObject("result_id", UUID.class)).add(mapDecision(rs));
                },
                arguments
        );

        Map<UUID, VerificationResult> byId = new HashMap<>();
        for (ResultRow row : rows) {
            byId.put(row.id(), new VerificationResult(
                    row.id(),
                    row.runId(),
                    row.expectationId(),
                    row.macroActivityId(),
                    row.activityId(),
                    row.displayName(),
                    row.evidenceType(),
                    row.location(),
                    row.subjectTicketId(),
                    new ApplicabilityAssessment(
                            row.applicabilityDecision(),
                            row.applicabilityOrigin(),
                            row.applicabilityRationale(),
                            applicabilityLimitations.get(row.id())
                    ),
                    row.proposedStatus(),
                    row.effectiveStatus(),
                    row.rationale(),
                    resultLimitations.get(row.id()),
                    evidence.get(row.id()),
                    decisions.get(row.id()),
                    row.revision()
            ));
        }
        return ids.stream().map(byId::get).toList();
    }

    private ResultRow mapResultRow(ResultSet rs) throws SQLException {
        return new ResultRow(
                rs.getObject("id", UUID.class),
                rs.getObject("run_id", UUID.class),
                rs.getObject("expectation_id", UUID.class),
                rs.getObject("macro_activity_id", UUID.class),
                rs.getObject("activity_id", UUID.class),
                rs.getString("display_name"),
                EvidenceType.valueOf(rs.getString("evidence_type")),
                com.temaconsulting.agente_iso.process.domain.EvidenceLocation
                        .valueOf(rs.getString("location")),
                rs.getString("subject_ticket_id"),
                ResultStatus.valueOf(rs.getString("proposed_status")),
                ResultStatus.valueOf(rs.getString("effective_status")),
                ApplicabilityDecision.valueOf(rs.getString("applicability_decision")),
                DecisionOrigin.valueOf(rs.getString("applicability_origin")),
                rs.getString("applicability_rationale"),
                rs.getString("rationale"),
                rs.getInt("revision")
        );
    }

    private VerificationEvidenceReference mapEvidence(ResultSet rs) throws SQLException {
        return new VerificationEvidenceReference(
                EvidenceType.valueOf(rs.getString("kind")),
                IntegrationSource.valueOf(rs.getString("source")),
                rs.getString("external_id"),
                rs.getString("display_name"),
                rs.getString("web_url"),
                rs.getTimestamp("observed_at").toInstant(),
                rs.getString("ticket_id")
        );
    }

    private HumanDecisionEvent mapDecision(ResultSet rs) throws SQLException {
        String applicability = rs.getString("applicability_value");
        String status = rs.getString("status_value");
        return new HumanDecisionEvent(
                rs.getObject("id", UUID.class),
                HumanDecisionType.valueOf(rs.getString("decision_type")),
                applicability == null ? null : HumanApplicabilityDecision.valueOf(applicability),
                status == null ? null : ResultStatus.valueOf(status),
                rs.getString("rationale"),
                rs.getObject("decided_by", UUID.class),
                rs.getTimestamp("decided_at").toInstant()
        );
    }

    private <T> Map<UUID, List<T>> emptyLists(List<ResultRow> rows) {
        Map<UUID, List<T>> values = new LinkedHashMap<>();
        rows.forEach(row -> values.put(row.id(), new ArrayList<>()));
        return values;
    }

    private boolean sameEvaluation(
            VerificationResult stored,
            VerificationResultWriteCommand command,
            Expectation expectation
    ) {
        List<VerificationEvidenceReference> submittedEvidence = command.evidence().stream()
                .map(this::toDomainReference)
                .sorted(EVIDENCE_ORDER)
                .toList();
        List<VerificationEvidenceReference> storedEvidence = stored.evidence().stream()
                .sorted(EVIDENCE_ORDER)
                .toList();
        return stored.runId().equals(command.runId())
                && stored.expectationId().equals(command.expectationId())
                && stored.macroActivityId().equals(expectation.macroActivityId())
                && Objects.equals(stored.activityId(), expectation.activityId())
                && stored.displayName().equals(expectation.displayName())
                && Objects.equals(stored.subjectTicketId(), command.subjectTicketId())
                && stored.evidenceType() == expectation.evidenceType()
                && stored.location() == expectation.location()
                && stored.applicability().decision() == command.applicabilityDecision()
                && stored.applicability().origin() == command.applicabilityOrigin()
                && stored.applicability().rationale().equals(command.applicabilityRationale())
                && stored.applicability().limitations().equals(command.applicabilityLimitations())
                && stored.proposedStatus() == command.proposedStatus()
                && stored.rationale().equals(command.rationale())
                && stored.limitations().equals(command.limitations())
                && storedEvidence.equals(submittedEvidence);
    }

    private VerificationEvidenceReference toDomainReference(
            VerificationResultWriteCommand.EvidenceReference reference
    ) {
        return new VerificationEvidenceReference(
                reference.kind(),
                reference.source(),
                reference.externalId(),
                reference.displayName(),
                reference.webUrl(),
                reference.observedAt(),
                reference.ticketId()
        );
    }

    private record Expectation(
            UUID id,
            UUID macroActivityId,
            UUID activityId,
            String displayName,
            EvidenceType evidenceType,
            com.temaconsulting.agente_iso.process.domain.EvidenceLocation location
    ) {
    }

    private record ResultRow(
            UUID id,
            UUID runId,
            UUID expectationId,
            UUID macroActivityId,
            UUID activityId,
            String displayName,
            EvidenceType evidenceType,
            com.temaconsulting.agente_iso.process.domain.EvidenceLocation location,
            String subjectTicketId,
            ResultStatus proposedStatus,
            ResultStatus effectiveStatus,
            ApplicabilityDecision applicabilityDecision,
            DecisionOrigin applicabilityOrigin,
            String applicabilityRationale,
            String rationale,
            int revision
    ) {
    }
}
