package com.temaconsulting.agente_iso.catalog;

import com.temaconsulting.agente_iso.catalog.api.RegistryDefinitionController;
import com.temaconsulting.agente_iso.catalog.application.port.RegistryDefinitionRepository;
import com.temaconsulting.agente_iso.catalog.domain.EvidenceType;
import com.temaconsulting.agente_iso.catalog.domain.RegistryDefinition;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest
@Testcontainers
class RegistryPaginationIntegrationTest {
    @Container
    static PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>("postgres:17-alpine");

    @DynamicPropertySource
    static void configure(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", postgres::getJdbcUrl);
        registry.add("spring.datasource.username", postgres::getUsername);
        registry.add("spring.datasource.password", postgres::getPassword);
    }

    @Autowired
    RegistryDefinitionRepository definitions;

    @Autowired
    RegistryDefinitionController controller;

    @Test
    void reportsActualTotalsForTwoRecordsAndFilteredResults() {
        String prefix = "PaginationTwo-" + UUID.randomUUID();
        insert(prefix + "-A", EvidenceType.DOCUMENT);
        insert(prefix + "-B", EvidenceType.TICKET);

        var all = controller.list(0, 20, prefix, null);
        assertThat(all.items()).hasSize(2);
        assertThat(all.page().totalElements()).isEqualTo(2);
        assertThat(all.page().totalPages()).isEqualTo(1);

        var documents = controller.list(0, 20, prefix, EvidenceType.DOCUMENT);
        assertThat(documents.items()).hasSize(1);
        assertThat(documents.page().totalElements()).isEqualTo(1);
        assertThat(documents.page().totalPages()).isEqualTo(1);

        var none = controller.list(0, 20, prefix + "-missing", null);
        assertThat(none.items()).isEmpty();
        assertThat(none.page().totalElements()).isZero();
        assertThat(none.page().totalPages()).isZero();

        var literal = controller.list(0, 20, "' OR 1=1 --", null);
        assertThat(literal.items()).isEmpty();
        assertThat(literal.page().totalElements()).isZero();
    }

    @Test
    void paginatesMoreThanTwentyMatchingRecords() {
        String prefix = "PaginationMany-" + UUID.randomUUID();
        for (int index = 0; index < 21; index++) {
            insert(prefix + "-" + index, EvidenceType.DOCUMENT);
        }

        var first = controller.list(0, 20, prefix, null);
        var second = controller.list(1, 20, prefix, null);
        assertThat(first.items()).hasSize(20);
        assertThat(second.items()).hasSize(1);
        assertThat(first.page().totalElements()).isEqualTo(21);
        assertThat(first.page().totalPages()).isEqualTo(2);
        assertThat(second.page().totalElements()).isEqualTo(21);
    }

    private void insert(String name, EvidenceType type) {
        definitions.insert(new RegistryDefinition(
                UUID.randomUUID(), "REG-" + UUID.randomUUID(), name, type, null, false
        ));
    }
}
