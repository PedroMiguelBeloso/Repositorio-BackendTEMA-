package com.temaconsulting.agente_iso.catalog.infrastructure;

import com.temaconsulting.agente_iso.catalog.application.RegistryDefinitionRepository;
import com.temaconsulting.agente_iso.catalog.domain.EvidenceType;
import com.temaconsulting.agente_iso.catalog.domain.RegistryDefinition;
import com.temaconsulting.agente_iso.shared.application.Page;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.stereotype.Repository;
import java.util.List;
import java.util.UUID;

@Repository
public class JdbcRegistryDefinitionRepository implements RegistryDefinitionRepository {

    private final JdbcTemplate jdbc;

    public JdbcRegistryDefinitionRepository(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    private final RowMapper<RegistryDefinition> mapper = (rs, rowNum) -> new RegistryDefinition(
        rs.getObject("id", UUID.class),
        rs.getString("key"),
        rs.getString("canonical_name"),
        EvidenceType.valueOf(rs.getString("evidence_type")),
        rs.getString("official_nomenclature"),
        rs.getBoolean("preloaded")
    );


    @Override
    public java.util.Optional<RegistryDefinition> findById(UUID id) {
        List<RegistryDefinition> rows = jdbc.query(
                "SELECT * FROM registry_definitions WHERE id = ?",
                mapper,
                id
        );
        return rows.stream().findFirst();
    }

    @Override
    public RegistryDefinition insert(RegistryDefinition def) {
        jdbc.update(
            "INSERT INTO registry_definitions (id, key, canonical_name, evidence_type, official_nomenclature, preloaded) VALUES (?, ?, ?, ?::evidence_type, ?, ?)",
            def.id(), def.key(), def.canonicalName(), def.evidenceType().name(), def.officialNomenclature(), def.preloaded()
        );
        return def;
    }

    @Override
    public java.util.Optional<RegistryDefinition> findByKey(String key) {
        List<RegistryDefinition> rows = jdbc.query(
                "SELECT * FROM registry_definitions WHERE key = ?",
                mapper,
                key
        );
        return rows.stream().findFirst();
    }

    @Override
    public Page<RegistryDefinition> list(int page, int size, String query) {
        String filter = query == null || query.isBlank() ? null : "%" + query.trim() + "%";
        List<RegistryDefinition> items;
        Integer total;

        if (filter == null) {
            items = jdbc.query(
                    "SELECT * FROM registry_definitions ORDER BY canonical_name LIMIT ? OFFSET ?",
                    mapper,
                    size,
                    page * size
            );
            total = jdbc.queryForObject(
                    "SELECT count(*) FROM registry_definitions",
                    Integer.class
            );
        } else {
            items = jdbc.query(
                    "SELECT * FROM registry_definitions WHERE canonical_name ILIKE ? ORDER BY canonical_name LIMIT ? OFFSET ?",
                    mapper,
                    filter,
                    size,
                    page * size
            );
            total = jdbc.queryForObject(
                    "SELECT count(*) FROM registry_definitions WHERE canonical_name ILIKE ?",
                    Integer.class,
                    filter
            );
        }

        return new Page<>(items, page, size, total == null ? 0 : total);
    }

    public List<RegistryDefinition> list(int page, int size, String query, EvidenceType type) {
        String sql = "SELECT * FROM registry_definitions WHERE 1=1 ";
        if (type != null) sql += " AND evidence_type = '" + type.name() + "'::evidence_type";
        if (query != null && !query.isBlank()) sql += " AND canonical_name ILIKE '%" + query + "%'";
        sql += " ORDER BY canonical_name LIMIT ? OFFSET ?";
        
        return jdbc.query(sql, mapper, size, page * size);
    }
}