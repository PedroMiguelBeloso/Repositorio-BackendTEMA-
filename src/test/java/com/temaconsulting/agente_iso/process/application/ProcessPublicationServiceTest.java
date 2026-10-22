package com.temaconsulting.agente_iso.process.application;

import com.temaconsulting.agente_iso.process.application.port.ProcessVersionRepository;

import com.temaconsulting.agente_iso.catalog.application.port.RegistryDefinitionRepository;
import com.temaconsulting.agente_iso.process.domain.ProcessStatus;
import com.temaconsulting.agente_iso.process.domain.ProcessVersion;
import com.temaconsulting.agente_iso.shared.application.error.ApiException;
import com.temaconsulting.agente_iso.shared.application.error.ProblemCode;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;

import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ProcessPublicationServiceTest {

    private static final Instant NOW = Instant.parse("2026-09-30T03:00:00Z");

    @Mock
    private ProcessVersionRepository versions;
    @Mock
    private RegistryDefinitionRepository registry;

    private PublicationValidator validator;
    private ProcessPublicationService service;

    @BeforeEach
    void setUp() {
        validator = new PublicationValidator();
        service = new ProcessPublicationService(
                versions,
                registry,
                validator,
                Clock.fixed(NOW, ZoneOffset.UTC)
        );
    }

    @Test
    void rejectsIncompleteDraftWith422AndDoesNotPublish() {
        UUID processId = UUID.randomUUID();
        UUID versionId = UUID.randomUUID();
        ProcessVersion draft = new ProcessVersion(
                versionId,
                processId,
                "1.0",
                LocalDate.of(2026, 9, 30),
                ProcessStatus.DRAFT,
                0,
                "",
                "",
                List.of(),
                List.of(),
                null,
                NOW.minusSeconds(10)
        );
        when(versions.findWithTree(versionId)).thenReturn(Optional.of(draft));

        assertThatThrownBy(() -> service.publish(processId, versionId, 0))
                .isInstanceOfSatisfying(ApiException.class, ex -> {
                    assertThat(ex.httpStatus()).isEqualTo(HttpStatus.UNPROCESSABLE_ENTITY);
                    assertThat(ex.code()).isEqualTo(ProblemCode.VALIDATION_ERROR);
                    assertThat(ex.details()).isNotEmpty();
                });

        verify(versions, never()).publish(versionId, 0, NOW);
    }

    @Test
    void rejectsStaleRevisionBeforeValidation() {
        UUID processId = UUID.randomUUID();
        UUID versionId = UUID.randomUUID();
        ProcessVersion draft = new ProcessVersion(
                versionId,
                processId,
                "1.0",
                LocalDate.of(2026, 9, 30),
                ProcessStatus.DRAFT,
                4,
                "Objective",
                "Owner",
                List.of(),
                List.of(),
                null,
                NOW.minusSeconds(10)
        );
        when(versions.findWithTree(versionId)).thenReturn(Optional.of(draft));

        assertThatThrownBy(() -> service.publish(processId, versionId, 3))
                .isInstanceOfSatisfying(ApiException.class, ex -> {
                    assertThat(ex.httpStatus()).isEqualTo(HttpStatus.CONFLICT);
                    assertThat(ex.code()).isEqualTo(ProblemCode.STALE_REVISION);
                });
    }

    @Test
    void rejectsAlreadyPublishedVersionWithoutChangingIt() {
        UUID processId = UUID.randomUUID();
        UUID versionId = UUID.randomUUID();
        ProcessVersion published = new ProcessVersion(
                versionId,
                processId,
                "1.0",
                LocalDate.of(2026, 9, 30),
                ProcessStatus.PUBLISHED,
                2,
                "Objective",
                "Owner",
                List.of(),
                List.of(),
                NOW.minusSeconds(5),
                NOW.minusSeconds(20)
        );
        when(versions.findWithTree(versionId)).thenReturn(Optional.of(published));

        assertThatThrownBy(() -> service.publish(processId, versionId, 2))
                .isInstanceOfSatisfying(ApiException.class, ex -> {
                    assertThat(ex.httpStatus()).isEqualTo(HttpStatus.CONFLICT);
                    assertThat(ex.code()).isEqualTo(ProblemCode.CONFLICT);
                });

        verify(versions, never()).publish(versionId, 2, NOW);
    }
}
