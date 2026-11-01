package com.temaconsulting.agente_iso.verification.infrastructure.persistence;

import com.temaconsulting.agente_iso.verification.application.error.VerificationResultDecisionFailure;
import com.temaconsulting.agente_iso.verification.application.model.AiRetryAttempt;
import com.temaconsulting.agente_iso.verification.application.model.ApplicabilityAssessment;
import com.temaconsulting.agente_iso.verification.application.model.VerificationResultWriteCommand;
import com.temaconsulting.agente_iso.verification.application.port.AiRetryRepository;
import com.temaconsulting.agente_iso.verification.application.port.VerificationResultRepository;
import com.temaconsulting.agente_iso.verification.domain.VerificationResult;
import java.sql.Timestamp;
import java.util.List;
import java.util.UUID;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

@Repository
public class JdbcAiRetryRepository implements AiRetryRepository {
    private final JdbcTemplate jdbc;
    private final VerificationResultRepository results;

    public JdbcAiRetryRepository(JdbcTemplate jdbc, VerificationResultRepository results) {
        this.jdbc = jdbc;
        this.results = results;
    }

    @Override
    @Transactional
    public VerificationResult save(AiRetryAttempt attempt, ApplicabilityAssessment applicability,
                                   List<String> limitations,
                                   List<VerificationResultWriteCommand.EvidenceReference> evidence) {
        var target = results.lockDecisionTarget(attempt.runId(), attempt.resultId())
                .orElseThrow(VerificationResultDecisionFailure::notFound);
        if (!"COMPLETED".equals(target.runStatus()) || target.revision() != attempt.previousRevision()) {
            throw VerificationResultDecisionFailure.conflict();
        }
        int changed = jdbc.update("""
                UPDATE verification_results
                SET proposed_status = ?::result_status, effective_status = ?::result_status,
                    applicability_decision = ?::applicability_decision,
                    applicability_origin = ?::decision_origin,
                    applicability_rationale = ?, rationale = ?, revision = revision + 1
                WHERE id = ? AND run_id = ? AND revision = ?
                """, attempt.proposedStatus().name(), attempt.effectiveStatus().name(),
                applicability.decision().name(), applicability.origin().name(),
                applicability.rationale(), attempt.rationale(),
                attempt.resultId(), attempt.runId(), attempt.previousRevision());
        if (changed != 1) throw VerificationResultDecisionFailure.conflict();
        jdbc.update("DELETE FROM verification_result_limitations WHERE result_id = ?", attempt.resultId());
        for (int index = 0; index < limitations.size(); index++) {
            jdbc.update("INSERT INTO verification_result_limitations (result_id, position, text) VALUES (?, ?, ?)",
                    attempt.resultId(), index + 1, limitations.get(index));
        }
        jdbc.update("DELETE FROM verification_result_applicability_limitations WHERE result_id = ?", attempt.resultId());
        for (int index = 0; index < applicability.limitations().size(); index++) {
            jdbc.update("INSERT INTO verification_result_applicability_limitations (result_id, position, text) VALUES (?, ?, ?)",
                    attempt.resultId(), index + 1, applicability.limitations().get(index));
        }
        jdbc.update("DELETE FROM verification_result_evidence WHERE result_id = ?", attempt.resultId());
        for (var reference : evidence) {
            jdbc.update("""
                    INSERT INTO verification_result_evidence
                        (result_id, kind, source, external_id, display_name, web_url, ticket_id, observed_at)
                    VALUES (?, ?::evidence_type, ?::source_type, ?, ?, ?, ?, ?)
                    """, attempt.resultId(), reference.kind().name(), reference.source().name(),
                    reference.externalId(), reference.displayName(), reference.webUrl(),
                    reference.ticketId(), Timestamp.from(reference.observedAt()));
        }
        jdbc.update("""
                INSERT INTO verification_ai_retries
                    (id, run_id, result_id, requested_by, attempted_at, ticket_snapshot, previous_result,
                     previous_revision, previous_proposed_status, previous_effective_status,
                     proposed_status, effective_status, applicability_decision, rationale,
                     outcome, model, response_id)
                VALUES (?, ?, ?, ?, ?, ?::jsonb, ?::jsonb, ?, ?::result_status, ?::result_status,
                        ?::result_status, ?::result_status, ?::applicability_decision, ?, ?, ?, ?)
                """, attempt.id(), attempt.runId(), attempt.resultId(), attempt.requestedBy(),
                Timestamp.from(attempt.attemptedAt()), attempt.ticketSnapshotJson(), attempt.previousResultJson(),
                attempt.previousRevision(), attempt.previousProposedStatus().name(),
                attempt.previousEffectiveStatus().name(), attempt.proposedStatus().name(),
                attempt.effectiveStatus().name(), attempt.applicabilityDecision().name(),
                attempt.rationale(), attempt.outcome(), attempt.model(), attempt.responseId());
        return results.findByRunAndId(attempt.runId(), attempt.resultId())
                .orElseThrow(VerificationResultDecisionFailure::notFound);
    }

    @Override
    public List<AiRetryAttempt> list(UUID runId, UUID resultId) {
        return jdbc.query("""
                SELECT id, run_id, result_id, requested_by, attempted_at, ticket_snapshot::text AS ticket_snapshot,
                       previous_result::text AS previous_result,
                       previous_revision, previous_proposed_status::text AS previous_proposed_status,
                       previous_effective_status::text AS previous_effective_status,
                       proposed_status::text AS proposed_status, effective_status::text AS effective_status,
                       applicability_decision::text AS applicability_decision, rationale, outcome, model, response_id
                FROM verification_ai_retries
                WHERE run_id = ? AND result_id = ?
                ORDER BY attempted_at DESC, id DESC
                LIMIT 100
                """, (rs, row) -> new AiRetryAttempt(
                rs.getObject("id", UUID.class), rs.getObject("run_id", UUID.class),
                rs.getObject("result_id", UUID.class), rs.getObject("requested_by", UUID.class),
                rs.getTimestamp("attempted_at").toInstant(), rs.getString("ticket_snapshot"),
                rs.getString("previous_result"),
                rs.getInt("previous_revision"),
                com.temaconsulting.agente_iso.verification.domain.ResultStatus.valueOf(rs.getString("previous_proposed_status")),
                com.temaconsulting.agente_iso.verification.domain.ResultStatus.valueOf(rs.getString("previous_effective_status")),
                com.temaconsulting.agente_iso.verification.domain.ResultStatus.valueOf(rs.getString("proposed_status")),
                com.temaconsulting.agente_iso.verification.domain.ResultStatus.valueOf(rs.getString("effective_status")),
                com.temaconsulting.agente_iso.verification.domain.ApplicabilityDecision.valueOf(rs.getString("applicability_decision")),
                rs.getString("rationale"), rs.getString("outcome"), rs.getString("model"), rs.getString("response_id")
        ), runId, resultId);
    }
}
