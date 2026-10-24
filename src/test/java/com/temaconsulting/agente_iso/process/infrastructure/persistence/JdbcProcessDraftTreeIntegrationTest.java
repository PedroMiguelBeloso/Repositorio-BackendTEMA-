package com.temaconsulting.agente_iso.process.infrastructure.persistence;

import com.temaconsulting.agente_iso.process.api.dto.ActivityInput;
import com.temaconsulting.agente_iso.process.api.dto.MacroActivityInput;
import com.temaconsulting.agente_iso.process.api.dto.ProcessPolicyInput;
import com.temaconsulting.agente_iso.process.api.dto.SaveDraftRequest;
import com.temaconsulting.agente_iso.process.application.ProcessDraftService;
import com.temaconsulting.agente_iso.process.application.port.ProcessRepository;
import com.temaconsulting.agente_iso.process.application.port.ProcessVersionRepository;
import com.temaconsulting.agente_iso.process.domain.Activity;
import com.temaconsulting.agente_iso.process.domain.MacroActivity;
import com.temaconsulting.agente_iso.process.domain.ProcessCode;
import com.temaconsulting.agente_iso.process.domain.ProcessDefinition;
import com.temaconsulting.agente_iso.process.domain.ProcessVersion;
import com.temaconsulting.agente_iso.shared.application.error.ApiException;
import com.temaconsulting.agente_iso.shared.application.error.ProblemCode;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@SpringBootTest
@Testcontainers
class JdbcProcessDraftTreeIntegrationTest {

    @Container
    static PostgreSQLContainer<?> postgres =
            new PostgreSQLContainer<>("postgres:17-alpine");

    @DynamicPropertySource
    static void configureProperties(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", postgres::getJdbcUrl);
        registry.add("spring.datasource.username", postgres::getUsername);
        registry.add("spring.datasource.password", postgres::getPassword);
    }

    @Autowired
    private ProcessRepository processes;

    @Autowired
    private ProcessVersionRepository versions;

    @Autowired
    private ProcessDraftService drafts;

    @Autowired
    private JdbcTemplate jdbc;

    @BeforeEach
    void cleanDatabase() {
        jdbc.update("DELETE FROM process_versions");
        jdbc.update("DELETE FROM processes");
    }

    @Test
    void replacesAndReadsOrderedDraftTreeWhilePreservingExistingIds() {
        UUID processId = UUID.randomUUID();
        UUID versionId = UUID.randomUUID();
        Instant createdAt = Instant.parse("2026-09-26T18:00:00Z");

        processes.insert(new ProcessDefinition(
                processId,
                ProcessCode.START_UP,
                "Start Up",
                true,
                createdAt
        ));
        versions.insertDraft(ProcessVersion.openDraft(
                versionId,
                processId,
                "1.0",
                LocalDate.of(2026, 9, 26),
                createdAt
        ));

        ProcessVersion firstSave = drafts.saveDraft(
                processId,
                versionId,
                new SaveDraftRequest(
                        0,
                        "1.0",
                        LocalDate.of(2026, 9, 26),
                        "Initial objective",
                        "Initial owner",
                        List.of(
                                new ProcessPolicyInput(null, 1, "Policy second"),
                                new ProcessPolicyInput(null, 0, "Policy first")
                        ),
                        List.of(
                                macro(null, "Macro second", 1, List.of(
                                        activity(null, "Activity B", 1),
                                        activity(null, "Activity A", 0)
                                )),
                                macro(null, "Macro first", 0, List.of(
                                        activity(null, "Activity C", 0)
                                ))
                        )
                )
        );

        assertThat(firstSave.revision()).isEqualTo(1);
        assertThat(firstSave.policies())
                .extracting(policy -> policy.text())
                .containsExactly("Policy first", "Policy second");
        assertThat(firstSave.macroActivities())
                .extracting(MacroActivity::name)
                .containsExactly("Macro first", "Macro second");
        assertThat(firstSave.macroActivities().get(1).activities())
                .extracting(Activity::name)
                .containsExactly("Activity A", "Activity B");

        MacroActivity keptMacro = firstSave.macroActivities().get(1);
        Activity keptActivity = keptMacro.activities().getFirst();
        UUID keptPolicyId = firstSave.policies().getFirst().id();

        ProcessVersion secondSave = drafts.saveDraft(
                processId,
                versionId,
                new SaveDraftRequest(
                        1,
                        "1.1",
                        LocalDate.of(2026, 9, 27),
                        "Updated objective",
                        "Updated owner",
                        List.of(
                                new ProcessPolicyInput(keptPolicyId, 1, "Policy first updated"),
                                new ProcessPolicyInput(null, 0, "Brand new policy")
                        ),
                        List.of(
                                macro(
                                        keptMacro.id(),
                                        "Macro retained",
                                        0,
                                        List.of(
                                                activity(
                                                        keptActivity.id(),
                                                        "Activity retained",
                                                        1
                                                ),
                                                activity(null, "Activity new", 0)
                                        )
                                )
                        )
                )
        );

        assertThat(secondSave.revision()).isEqualTo(2);
        assertThat(secondSave.macroActivities()).hasSize(1);
        assertThat(secondSave.macroActivities().getFirst().id()).isEqualTo(keptMacro.id());
        assertThat(secondSave.macroActivities().getFirst().activities())
                .extracting(Activity::name)
                .containsExactly("Activity new", "Activity retained");
        assertThat(secondSave.macroActivities().getFirst().activities().get(1).id())
                .isEqualTo(keptActivity.id());
        assertThat(secondSave.policies())
                .filteredOn(policy -> policy.id().equals(keptPolicyId))
                .singleElement()
                .extracting(policy -> policy.text())
                .isEqualTo("Policy first updated");

        ProcessVersion reloaded = versions.findWithTree(versionId).orElseThrow();
        assertThat(reloaded).isEqualTo(secondSave);

        assertThatThrownBy(() -> drafts.saveDraft(
                processId,
                versionId,
                new SaveDraftRequest(
                        1,
                        "1.1",
                        LocalDate.of(2026, 9, 27),
                        "Should not persist",
                        "Updated owner",
                        secondSave.policies().stream()
                                .map(policy -> new ProcessPolicyInput(
                                        policy.id(),
                                        policy.order(),
                                        policy.text()
                                ))
                                .toList(),
                        secondSave.macroActivities().stream()
                                .map(macro -> macro(
                                        macro.id(),
                                        macro.name(),
                                        macro.order(),
                                        macro.activities().stream()
                                                .map(activity -> activity(
                                                        activity.id(),
                                                        activity.name(),
                                                        activity.order()
                                                ))
                                                .toList()
                                ))
                                .toList()
                )
        )).isInstanceOfSatisfying(ApiException.class, ex ->
                assertThat(ex.code()).isEqualTo(ProblemCode.STALE_REVISION)
        );

        ProcessVersion afterRejectedSave = versions.findWithTree(versionId).orElseThrow();
        assertThat(afterRejectedSave.objective()).isEqualTo("Updated objective");
        assertThat(afterRejectedSave.revision()).isEqualTo(2);
    }

    private static MacroActivityInput macro(
            UUID id,
            String name,
            int order,
            List<ActivityInput> activities
    ) {
        return new MacroActivityInput(
                id,
                name,
                null,
                order,
                List.of(),
                activities,
                List.of()
        );
    }

    private static ActivityInput activity(UUID id, String name, int order) {
        return new ActivityInput(
                id,
                name,
                null,
                "Instructions for " + name,
                null,
                order,
                List.of()
        );
    }
}
