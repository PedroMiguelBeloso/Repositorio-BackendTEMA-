package com.temaconsulting.agente_iso.verification.application;

import com.temaconsulting.agente_iso.integration.application.RedmineTicketReader;
import com.temaconsulting.agente_iso.integration.domain.TicketSnapshot;
import com.temaconsulting.agente_iso.process.domain.ApplicabilityMode;
import com.temaconsulting.agente_iso.process.domain.Cardinality;
import com.temaconsulting.agente_iso.process.domain.EvidenceLocation;
import com.temaconsulting.agente_iso.process.domain.EvidenceType;
import com.temaconsulting.agente_iso.verification.domain.PreviewExpectation;
import com.temaconsulting.agente_iso.verification.domain.TicketSelectionGroup;
import com.temaconsulting.agente_iso.verification.domain.VerificationPreview;
import com.temaconsulting.agente_iso.verification.domain.VerificationScope;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class TicketSelectionValidatorTest {
    private static final String PROJECT_ID = "project-42";

    @Test
    void oneRequiresExactlyOneTicketAndReturnsThatSelection() {
        TicketSelectionGroup group = group(Cardinality.ONE);
        TicketSelectionValidator validator = validator(this::ticket);

        var result = validator.validate(preview(group), List.of(selection(group, "204")), PROJECT_ID);

        assertThat(result.groups()).hasSize(1);
        assertThat(result.groups().getFirst().ticketIds()).containsExactly("204");
    }

    @Test
    void oneRejectsZeroAndMultipleTickets() {
        TicketSelectionGroup group = group(Cardinality.ONE);
        TicketSelectionValidator validator = validator(this::ticket);

        assertInvalid(validator, preview(group), List.of(new TicketSelectionInput(group.id(), List.of())), "at least one");
        assertInvalid(validator, preview(group), List.of(selection(group, "204", "205")), "exactly one");
    }

    @Test
    void multipleAcceptsOneOrMoreTickets() {
        TicketSelectionGroup group = group(Cardinality.MULTIPLE);
        TicketSelectionValidator validator = validator(this::ticket);

        assertThat(validator.validate(preview(group), List.of(selection(group, "204")), PROJECT_ID)
                .groups().getFirst().ticketIds()).containsExactly("204");
        assertThat(validator.validate(preview(group), List.of(selection(group, "204", "205")), PROJECT_ID)
                .groups().getFirst().ticketIds()).containsExactly("204", "205");
    }

    @Test
    void multipleRejectsAnEmptyTicketList() {
        TicketSelectionGroup group = group(Cardinality.MULTIPLE);
        assertInvalid(validator(this::ticket), preview(group),
                List.of(new TicketSelectionInput(group.id(), List.of())), "at least one");
    }

    @Test
    void requiresExactGroupsAndRejectsDuplicatesAndUnknownGroups() {
        TicketSelectionGroup first = group(Cardinality.ONE);
        TicketSelectionGroup second = group(Cardinality.MULTIPLE);
        VerificationPreview preview = preview(first, second);
        TicketSelectionValidator validator = validator(this::ticket);

        assertInvalid(validator, preview, List.of(selection(first, "204")), "Missing");
        assertInvalid(validator, preview, List.of(
                selection(first, "204"), selection(first, "205"), selection(second, "206")
        ), "more than once");
        assertInvalid(validator, preview, List.of(
                selection(first, "204"), selection(second, "206"),
                new TicketSelectionInput("invented-group", List.of("207"))
        ), "Unknown or out-of-scope");
    }

    @Test
    void rejectsBlankAndRepeatedTicketIds() {
        TicketSelectionGroup group = group(Cardinality.MULTIPLE);
        TicketSelectionValidator validator = validator(this::ticket);

        assertInvalid(validator, preview(group),
                List.of(new TicketSelectionInput(group.id(), List.of(" "))), "empty ticket ID");
        assertInvalid(validator, preview(group), List.of(selection(group, "204", "204")), "repeated");
    }

    @Test
    void validatesGroupsIndependentlyAndPreservesTheirMacroActivity() {
        TicketSelectionGroup first = group(Cardinality.ONE);
        TicketSelectionGroup second = group(Cardinality.MULTIPLE);
        var result = validator(this::ticket).validate(
                preview(first, second),
                List.of(selection(first, "204"), selection(second, "204", "205")),
                PROJECT_ID
        );

        assertThat(result.groups()).extracting(group -> group.macroActivityId())
                .containsExactly(first.macroActivityId(), second.macroActivityId());
        assertThat(result.groups()).extracting(group -> group.ticketIds())
                .containsExactly(List.of("204"), List.of("204", "205"));
    }

    @Test
    void rejectsMissingTicketsAndTicketsFromOtherProjects() {
        TicketSelectionGroup group = group(Cardinality.ONE);
        TicketSelectionValidator missingReader = validator((project, id) -> {
            throw new com.temaconsulting.agente_iso.shared.application.ApiException(
                    com.temaconsulting.agente_iso.shared.application.ProblemCode.NOT_FOUND,
                    org.springframework.http.HttpStatus.NOT_FOUND,
                    "not found", List.of()
            );
        });
        assertInvalid(missingReader, preview(group), List.of(selection(group, "999")), "Ticket 999");

        TicketSelectionValidator otherProject = validator(
                (project, id) -> ticketSnapshot(id, "another-project"));
        assertInvalid(otherProject, preview(group), List.of(selection(group, "204")), "Ticket 204");
    }

    @Test
    void aRedmineFailureIsPropagatedAndCannotProduceAValidatedSelection() {
        TicketSelectionGroup group = group(Cardinality.ONE);
        var unavailable = new com.temaconsulting.agente_iso.shared.application.ApiException(
                com.temaconsulting.agente_iso.shared.application.ProblemCode.SOURCE_UNAVAILABLE,
                org.springframework.http.HttpStatus.SERVICE_UNAVAILABLE,
                "Redmine unavailable", List.of()
        );
        assertThatThrownBy(() -> validator((project, id) -> {
            throw unavailable;
        }).validate(preview(group), List.of(selection(group, "204")), PROJECT_ID))
                .isSameAs(unavailable);
    }

    @Test
    void rejectsWhenServiceHasNoConfiguredRedmineProject() {
        TicketSelectionGroup group = group(Cardinality.ONE);
        assertThatThrownBy(() -> validator(this::ticket)
                .validate(preview(group), List.of(selection(group, "204")), null))
                .isInstanceOf(VerificationStartFailure.class)
                .hasMessageContaining("no Redmine project configured");
    }

    @Test
    void allowsScopesWithoutTicketGroupsWhenNoRedmineProjectIsConfigured() {
        var result = validator(this::ticket).validate(preview(), List.of(), null);

        assertThat(result.groups()).isEmpty();
    }

    private TicketSelectionValidator validator(RedmineTicketReader reader) {
        return new TicketSelectionValidator(reader);
    }

    private TicketSnapshot ticket(String projectId, String ticketId) {
        return ticketSnapshot(ticketId, projectId);
    }

    private TicketSnapshot ticketSnapshot(String ticketId, String projectId) {
        return new TicketSnapshot(
                ticketId, projectId, "Project", "Subject", "Open", "1", "Open", false,
                null, null, null, null, List.of(), List.of()
        );
    }

    private static TicketSelectionGroup group(Cardinality cardinality) {
        UUID macroId = UUID.randomUUID();
        UUID expectationId = UUID.randomUUID();
        return TicketSelectionGroup.forMacroActivity(
                macroId, "Tickets", cardinality, List.of(expectationId), List.of(expectationId), true
        );
    }

    private static VerificationPreview preview(TicketSelectionGroup... groups) {
        List<PreviewExpectation> expectations = new ArrayList<>();
        for (TicketSelectionGroup group : groups) {
            for (UUID expectationId : group.relatedExpectationIds()) {
                expectations.add(new PreviewExpectation(
                        expectationId, group.macroActivityId(), null, EvidenceLocation.INPUT,
                        "Ticket", EvidenceType.TICKET, ApplicabilityMode.ALWAYS,
                        group.cardinality(), group.id()
                ));
            }
        }
        return new VerificationPreview(
                UUID.randomUUID(), 1, UUID.randomUUID(), VerificationScope.PROCESS,
                null, expectations, List.of(groups)
        );
    }

    private static TicketSelectionInput selection(TicketSelectionGroup group, String... ticketIds) {
        return new TicketSelectionInput(group.id(), List.of(ticketIds));
    }

    private static void assertInvalid(
            TicketSelectionValidator validator,
            VerificationPreview preview,
            List<TicketSelectionInput> selections,
            String message
    ) {
        assertThatThrownBy(() -> validator.validate(preview, selections, PROJECT_ID))
                .isInstanceOf(VerificationStartFailure.class)
                .hasMessageContaining(message);
    }
}
