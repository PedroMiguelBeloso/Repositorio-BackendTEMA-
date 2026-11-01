package com.temaconsulting.agente_iso.verification.application;

import com.temaconsulting.agente_iso.catalog.domain.EvidenceType;
import com.temaconsulting.agente_iso.shared.application.model.Page;
import com.temaconsulting.agente_iso.verification.application.port.VerificationResultRepository;
import com.temaconsulting.agente_iso.verification.domain.VerificationResult;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/** Builds the full work list, including items without persisted results yet. */
@Service
public class VerificationWorkStatusQueryService {
    private final VerificationRunQueryService runs;
    private final VerificationRunWorkLoader loader;
    private final VerificationResultRepository results;
    private final VerificationActiveWork active;

    public VerificationWorkStatusQueryService(VerificationRunQueryService runs, VerificationRunWorkLoader loader,
                                              VerificationResultRepository results, VerificationActiveWork active) {
        this.runs = runs;
        this.loader = loader;
        this.results = results;
        this.active = active;
    }

    public List<Item> list(UUID runId) {
        var run = runs.get(runId).run();
        Map<Key, VerificationResult> stored = new HashMap<>();
        for (int page = 0; ; page++) {
            Page<VerificationResult> batch = results.findPage(runId, page, 100);
            for (VerificationResult result : batch.items()) {
                stored.put(new Key(result.expectationId(), result.subjectTicketId()), result);
            }
            if ((long) (page + 1) * 100 >= batch.totalElements()) break;
        }
        var running = "RUNNING".equals(run.status()) ? active.current(runId) : java.util.Optional.<VerificationActiveWork.Item>empty();
        List<Item> items = new ArrayList<>();
        for (var workItem : loader.load(run).items()) {
            var expectation = workItem.expectation();
            List<String> ticketIds;
            if (expectation.registry().evidenceType() == EvidenceType.TICKET) {
                ticketIds = run.ticketSelections().groups().stream()
                        .filter(group -> group.groupId().equals(expectation.ticketSelectionGroupId()))
                        .findFirst().orElseThrow().ticketIds();
            } else {
                ticketIds = java.util.Collections.singletonList(null);
            }
            for (String ticketId : ticketIds) {
                VerificationResult result = stored.get(new Key(expectation.expectationId(), ticketId));
                boolean inProgress = result == null && running.isPresent()
                        && running.get().expectationId().equals(expectation.expectationId())
                        && java.util.Objects.equals(running.get().ticketId(), ticketId);
                items.add(new Item(expectation.expectationId(), expectation.displayName(), ticketId,
                        inProgress, result));
            }
        }
        return List.copyOf(items);
    }

    private record Key(UUID expectationId, String ticketId) { }

    public record Item(UUID expectationId, String displayName, String ticketId,
                       boolean inProgress, VerificationResult result) { }
}
