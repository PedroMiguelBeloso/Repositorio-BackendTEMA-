package com.temaconsulting.agente_iso.process.api;

import com.temaconsulting.agente_iso.process.api.dto.CreateProcessRequest;
import com.temaconsulting.agente_iso.process.api.dto.CreateVersionRequest;
import com.temaconsulting.agente_iso.process.api.dto.PublishVersionRequest;
import com.temaconsulting.agente_iso.process.application.ProcessDraftService;
import com.temaconsulting.agente_iso.process.application.ProcessPublicationService;
import com.temaconsulting.agente_iso.process.application.ProcessService;
import com.temaconsulting.agente_iso.process.domain.ProcessCode;
import com.temaconsulting.agente_iso.process.domain.ProcessDefinition;
import com.temaconsulting.agente_iso.process.domain.ProcessStatus;
import com.temaconsulting.agente_iso.process.domain.ProcessVersion;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.context.junit.jupiter.SpringJUnitConfig;

import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

@SpringJUnitConfig(ProcessAuthorizationTest.TestConfiguration.class)
class ProcessAuthorizationTest {

    @Autowired
    private ProcessController processController;

    @Autowired
    private ProcessVersionController versionController;

    @Autowired
    private ProcessService processService;

    @Autowired
    private ProcessDraftService draftService;

    @Autowired
    private ProcessPublicationService publicationService;

    @Test
    @WithMockUser(roles = "READER")
    void readerCannotCreateProcess() {
        assertThatThrownBy(() -> processController.create(
                new CreateProcessRequest(ProcessCode.START_UP, "Start Up")
        )).isInstanceOf(AccessDeniedException.class);
    }


    @Test
    @WithMockUser(roles = "ADMIN")
    void adminCanCreateProcess() {
        ProcessDefinition process = new ProcessDefinition(
                UUID.randomUUID(),
                ProcessCode.START_UP,
                "Start Up",
                true,
                Instant.parse("2026-09-26T15:00:00Z")
        );
        when(processService.create(ProcessCode.START_UP, "Start Up")).thenReturn(process);

        assertThat(processController.create(
                new CreateProcessRequest(ProcessCode.START_UP, "Start Up")
        ).id()).isEqualTo(process.id());
    }

    @Test
    @WithMockUser(roles = "READER")
    void readerCanReadProcess() {
        UUID processId = UUID.randomUUID();
        ProcessDefinition process = new ProcessDefinition(
                processId,
                ProcessCode.START_UP,
                "Start Up",
                true,
                Instant.parse("2026-09-26T15:00:00Z")
        );
        when(processService.get(processId)).thenReturn(process);

        assertThat(processController.get(processId).id()).isEqualTo(processId);
    }

    @Test
    @WithMockUser(roles = "READER")
    void readerCannotCreateDraft() {
        UUID processId = UUID.randomUUID();

        assertThatThrownBy(() -> versionController.createVersion(
                processId,
                new CreateVersionRequest("1.0", LocalDate.of(2026, 9, 26), null)
        )).isInstanceOf(AccessDeniedException.class);
    }


    @Test
    @WithMockUser(roles = "ADMIN")
    void adminCanCreateDraft() {
        UUID processId = UUID.randomUUID();
        CreateVersionRequest request = new CreateVersionRequest(
                "1.0",
                LocalDate.of(2026, 9, 26),
                null
        );
        ProcessVersion version = new ProcessVersion(
                UUID.randomUUID(),
                processId,
                "1.0",
                request.versionDate(),
                ProcessStatus.DRAFT,
                0,
                "",
                "",
                null,
                Instant.parse("2026-09-26T15:00:00Z")
        );
        when(draftService.createDraft(processId, request)).thenReturn(version);

        assertThat(versionController.createVersion(processId, request).id())
                .isEqualTo(version.id());
    }

    @Test
    @WithMockUser(roles = "READER")
    void readerCanReadDraft() {
        UUID processId = UUID.randomUUID();
        UUID versionId = UUID.randomUUID();
        ProcessVersion version = new ProcessVersion(
                versionId,
                processId,
                "1.0",
                LocalDate.of(2026, 9, 26),
                ProcessStatus.DRAFT,
                0,
                "",
                "",
                null,
                Instant.parse("2026-09-26T15:00:00Z")
        );
        when(draftService.getVersion(processId, versionId)).thenReturn(version);

        assertThat(versionController.getVersion(processId, versionId).id())
                .isEqualTo(versionId);
    }



    @Test
    @WithMockUser(roles = "READER")
    void readerCannotPublishVersion() {
        assertThatThrownBy(() -> versionController.publish(
                UUID.randomUUID(),
                UUID.randomUUID(),
                new PublishVersionRequest(0)
        )).isInstanceOf(AccessDeniedException.class);
    }


    @Configuration
    @EnableMethodSecurity
    static class TestConfiguration {

        @Bean
        ProcessService processService() {
            return mock(ProcessService.class);
        }

        @Bean
        ProcessDraftService processDraftService() {
            return mock(ProcessDraftService.class);
        }

        @Bean
        ProcessPublicationService processPublicationService() {
            return mock(ProcessPublicationService.class);
        }

        @Bean
        ProcessController processController(ProcessService processService) {
            return new ProcessController(processService);
        }

        @Bean
        ProcessVersionController processVersionController(
                ProcessDraftService drafts,
                ProcessPublicationService publications
        ) {
            return new ProcessVersionController(drafts, publications);
        }
    }
}
