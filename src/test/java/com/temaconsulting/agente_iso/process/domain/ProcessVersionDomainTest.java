package com.temaconsulting.agente_iso.process.domain;

import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class ProcessVersionDomainTest {

        private static final Instant NOW = Instant.parse("2026-09-26T18:00:00Z");

        @Test
        void opensEmptyDraftWithRevisionZero() {
                ProcessVersion draft = ProcessVersion.openDraft(
                                UUID.randomUUID(),
                                UUID.randomUUID(),
                                "1.0",
                                LocalDate.of(2026, 9, 26),
                                NOW);

                assertThat(draft.status()).isEqualTo(ProcessStatus.DRAFT);
                assertThat(draft.revision()).isZero();
                assertThat(draft.policies()).isEmpty();
                assertThat(draft.macroActivities()).isEmpty();
        }

        @Test
        void replacesDraftStructureAndIncrementsRevision() {
                UUID versionId = UUID.randomUUID();
                UUID macroId = UUID.randomUUID();
                ProcessVersion draft = ProcessVersion.openDraft(
                                versionId,
                                UUID.randomUUID(),
                                "1.0",
                                LocalDate.of(2026, 9, 26),
                                NOW);

                ProcessPolicy laterPolicy = new ProcessPolicy(UUID.randomUUID(), 1, "Policy B");
                ProcessPolicy firstPolicy = new ProcessPolicy(UUID.randomUUID(), 0, "Policy A");
                Activity activity = new Activity(
                                UUID.randomUUID(),
                                macroId,
                                "Review scope",
                                "Consultant",
                                "Review the agreed scope",
                                "1 day",
                                0,
                                List.of() // <- Agregado para los registros (records) de actividad
                );
                MacroActivity macro = new MacroActivity(
                                macroId,
                                versionId,
                                "Planning",
                                null,
                                0,
                                List.of(activity),
                                List.of(), // <- Agregado para inputs
                                List.of() // <- Agregado para outputs
                );

                ProcessVersion updated = draft.replaceStructure(
                                0,
                                "1.1",
                                LocalDate.of(2026, 9, 27),
                                "Control the transition",
                                "PMO",
                                List.of(laterPolicy, firstPolicy),
                                List.of(macro));

                assertThat(updated.revision()).isEqualTo(1);
                assertThat(updated.objective()).isEqualTo("Control the transition");
                assertThat(updated.owner()).isEqualTo("PMO");
                assertThat(updated.policies())
                                .extracting(ProcessPolicy::order)
                                .containsExactly(0, 1);
                assertThat(updated.macroActivities()).containsExactly(macro);
        }

        @Test
        void rejectsSavingPublishedVersion() {
                ProcessVersion published = new ProcessVersion(
                                UUID.randomUUID(),
                                UUID.randomUUID(),
                                "1.0",
                                LocalDate.of(2026, 9, 26),
                                ProcessStatus.PUBLISHED,
                                4,
                                "Objective",
                                "Owner",
                                List.of(),
                                List.of(),
                                NOW,
                                NOW);

                assertThatThrownBy(() -> published.replaceStructure(
                                4,
                                "1.0",
                                LocalDate.of(2026, 9, 26),
                                "Objective",
                                "Owner",
                                List.of(),
                                List.of())).isInstanceOf(DraftNotEditableException.class);
        }

        @Test
        void rejectsStaleRevisionInsideAggregate() {
                ProcessVersion draft = new ProcessVersion(
                                UUID.randomUUID(),
                                UUID.randomUUID(),
                                "1.0",
                                LocalDate.of(2026, 9, 26),
                                ProcessStatus.DRAFT,
                                3,
                                "Objective",
                                "Owner",
                                List.of(),
                                List.of(),
                                null,
                                NOW);

                assertThatThrownBy(() -> draft.replaceStructure(
                                2,
                                "1.0",
                                LocalDate.of(2026, 9, 26),
                                "Objective",
                                "Owner",
                                List.of(),
                                List.of())).isInstanceOf(RevisionMismatchException.class);
        }
}
