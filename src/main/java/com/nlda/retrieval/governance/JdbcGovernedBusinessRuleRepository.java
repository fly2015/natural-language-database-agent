package com.nlda.retrieval.governance;

import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.time.LocalDateTime;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;

@Repository
public class JdbcGovernedBusinessRuleRepository implements GovernedBusinessRuleRepository {

    private final JdbcTemplate jdbcTemplate;

    public JdbcGovernedBusinessRuleRepository(@Qualifier("governanceJdbcTemplate") JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    @Override
    public List<GovernedBusinessRule> findAll() {
        return hydrate(jdbcTemplate.query("""
                SELECT id, name, text, owner, version, approval_status, effective_start, effective_end,
                       datasource_id, tenant_id, active, content_hash, created_at, updated_at
                  FROM governed_business_rule
                 ORDER BY datasource_id, tenant_id, id
                """, this::mapRuleBase));
    }

    @Override
    public List<GovernedBusinessRule> findApprovedForRetrieval(String datasourceId, String tenantId) {
        LocalDateTime now = LocalDateTime.now();
        return hydrate(jdbcTemplate.query("""
                SELECT id, name, text, owner, version, approval_status, effective_start, effective_end,
                       datasource_id, tenant_id, active, content_hash, created_at, updated_at
                  FROM governed_business_rule
                 WHERE datasource_id = ?
                   AND active = true
                   AND approval_status = 'APPROVED'
                   AND (tenant_id IS NULL OR tenant_id = '' OR tenant_id = ?)
                   AND (effective_start IS NULL OR effective_start <= ?)
                   AND (effective_end IS NULL OR effective_end > ?)
                 ORDER BY id
                """, this::mapRuleBase, normalize(datasourceId), blankToEmpty(tenantId), now, now));
    }

    @Override
    public Optional<GovernedBusinessRule> findById(String id) {
        List<GovernedBusinessRule> rules = hydrate(jdbcTemplate.query("""
                SELECT id, name, text, owner, version, approval_status, effective_start, effective_end,
                       datasource_id, tenant_id, active, content_hash, created_at, updated_at
                  FROM governed_business_rule
                 WHERE id = ?
                """, this::mapRuleBase, normalize(id)));
        return rules.stream().findFirst();
    }

    @Override
    @Transactional
    public GovernedBusinessRule save(GovernedBusinessRule rule) {
        GovernedBusinessRule normalized = normalize(rule);
        if (exists(normalized.id())) {
            jdbcTemplate.update("""
                    UPDATE governed_business_rule
                       SET name = ?,
                           text = ?,
                           owner = ?,
                           version = ?,
                           approval_status = ?,
                           effective_start = ?,
                           effective_end = ?,
                           datasource_id = ?,
                           tenant_id = ?,
                           active = ?,
                           content_hash = ?,
                           updated_at = CURRENT_TIMESTAMP
                     WHERE id = ?
                    """,
                    normalized.name(),
                    normalized.text(),
                    normalized.owner(),
                    normalized.version(),
                    normalized.approvalStatus().name(),
                    timestamp(normalized.effectiveStart()),
                    timestamp(normalized.effectiveEnd()),
                    normalized.datasourceId(),
                    normalized.tenantId(),
                    normalized.active(),
                    normalized.contentHash(),
                    normalized.id());
        } else {
            jdbcTemplate.update("""
                    INSERT INTO governed_business_rule (
                        id, name, text, owner, version, approval_status, effective_start, effective_end,
                        datasource_id, tenant_id, active, content_hash, created_at, updated_at
                    )
                    VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP)
                    """,
                    normalized.id(),
                    normalized.name(),
                    normalized.text(),
                    normalized.owner(),
                    normalized.version(),
                    normalized.approvalStatus().name(),
                    timestamp(normalized.effectiveStart()),
                    timestamp(normalized.effectiveEnd()),
                    normalized.datasourceId(),
                    normalized.tenantId(),
                    normalized.active(),
                    normalized.contentHash());
        }
        replaceAliases(normalized.id(), normalized.aliases());
        replaceSchemaRefs(normalized.id(), normalized.schemaRefs().stream()
                .map(ref -> new ValidatedSchemaRef(ref, true, null))
                .toList());
        return findById(normalized.id()).orElse(normalized);
    }

    private boolean exists(String id) {
        Integer count = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM governed_business_rule WHERE id = ?",
                Integer.class,
                id);
        return count != null && count > 0;
    }

    @Override
    public void updateStatus(String id, ApprovalStatus status, boolean active) {
        jdbcTemplate.update("""
                UPDATE governed_business_rule
                   SET approval_status = ?,
                       active = ?,
                       updated_at = CURRENT_TIMESTAMP
                 WHERE id = ?
                """, status.name(), active, normalize(id));
    }

    @Override
    @Transactional
    public void replaceSchemaRefs(String id, List<ValidatedSchemaRef> schemaRefs) {
        String normalizedId = normalize(id);
        jdbcTemplate.update("DELETE FROM governed_business_rule_schema_ref WHERE rule_id = ?", normalizedId);
        for (ValidatedSchemaRef schemaRef : schemaRefs) {
            jdbcTemplate.update("""
                    INSERT INTO governed_business_rule_schema_ref (
                        rule_id, schema_ref, valid, last_validated_schema_fingerprint, last_validated_at
                    )
                    VALUES (?, ?, ?, ?, CURRENT_TIMESTAMP)
                    """,
                    normalizedId,
                    normalize(schemaRef.schemaRef()),
                    schemaRef.valid(),
                    schemaRef.schemaFingerprint());
        }
    }

    private void replaceAliases(String id, Set<String> aliases) {
        jdbcTemplate.update("DELETE FROM governed_business_rule_alias WHERE rule_id = ?", id);
        for (String alias : aliases) {
            jdbcTemplate.update("INSERT INTO governed_business_rule_alias (rule_id, alias) VALUES (?, ?)",
                    id, normalize(alias));
        }
    }

    private List<GovernedBusinessRule> hydrate(List<RuleBase> bases) {
        if (bases.isEmpty()) {
            return List.of();
        }
        List<String> ids = bases.stream().map(RuleBase::id).toList();
        Map<String, Set<String>> aliases = groupedValues("governed_business_rule_alias", "alias", ids);
        Map<String, Set<String>> schemaRefs = groupedValues("governed_business_rule_schema_ref", "schema_ref", ids);
        return bases.stream()
                .map(base -> base.toRule(
                        schemaRefs.getOrDefault(base.id(), Set.of()),
                        aliases.getOrDefault(base.id(), Set.of())
                ))
                .toList();
    }

    private Map<String, Set<String>> groupedValues(String table, String valueColumn, List<String> ids) {
        String placeholders = ids.stream().map(id -> "?").collect(Collectors.joining(","));
        return jdbcTemplate.query("SELECT rule_id, " + valueColumn + " FROM " + table
                        + " WHERE rule_id IN (" + placeholders + ")",
                rs -> {
                    Map<String, Set<String>> values = new java.util.LinkedHashMap<>();
                    while (rs.next()) {
                        values.computeIfAbsent(rs.getString("rule_id"), ignored -> new LinkedHashSet<>())
                                .add(rs.getString(valueColumn));
                    }
                    return values;
                },
                ids.toArray());
    }

    private RuleBase mapRuleBase(ResultSet rs, int rowNum) throws SQLException {
        return new RuleBase(
                rs.getString("id"),
                rs.getString("name"),
                rs.getString("text"),
                rs.getString("owner"),
                rs.getInt("version"),
                ApprovalStatus.valueOf(rs.getString("approval_status")),
                localDateTime(rs.getTimestamp("effective_start")),
                localDateTime(rs.getTimestamp("effective_end")),
                rs.getString("datasource_id"),
                rs.getString("tenant_id"),
                rs.getBoolean("active"),
                rs.getString("content_hash"),
                localDateTime(rs.getTimestamp("created_at")),
                localDateTime(rs.getTimestamp("updated_at"))
        );
    }

    private GovernedBusinessRule normalize(GovernedBusinessRule rule) {
        return new GovernedBusinessRule(
                normalize(rule.id()),
                defaultText(rule.name(), normalize(rule.id())),
                rule.text() == null ? "" : rule.text().strip(),
                defaultText(rule.owner(), "unknown"),
                Math.max(1, rule.version()),
                rule.approvalStatus() == null ? ApprovalStatus.DRAFT : rule.approvalStatus(),
                rule.effectiveStart(),
                rule.effectiveEnd(),
                defaultText(rule.datasourceId(), "default"),
                blankToEmpty(rule.tenantId()),
                rule.active(),
                normalizeSet(rule.schemaRefs()),
                normalizeSet(rule.aliases()),
                rule.contentHash(),
                rule.createdAt(),
                rule.updatedAt()
        );
    }

    private Set<String> normalizeSet(Set<String> values) {
        if (values == null) {
            return Set.of();
        }
        return values.stream()
                .filter(value -> !blank(value))
                .map(this::normalize)
                .collect(Collectors.toCollection(LinkedHashSet::new));
    }

    private String normalize(String value) {
        return value == null ? "" : value.toLowerCase(Locale.ROOT).strip();
    }

    private String blankToEmpty(String value) {
        return value == null ? "" : value.strip();
    }

    private String defaultText(String value, String defaultValue) {
        return blank(value) ? defaultValue : value.strip();
    }

    private boolean blank(String value) {
        return value == null || value.isBlank();
    }

    private Timestamp timestamp(LocalDateTime value) {
        return value == null ? null : Timestamp.valueOf(value);
    }

    private LocalDateTime localDateTime(Timestamp value) {
        return value == null ? null : value.toLocalDateTime();
    }

    private record RuleBase(
            String id,
            String name,
            String text,
            String owner,
            int version,
            ApprovalStatus approvalStatus,
            LocalDateTime effectiveStart,
            LocalDateTime effectiveEnd,
            String datasourceId,
            String tenantId,
            boolean active,
            String contentHash,
            LocalDateTime createdAt,
            LocalDateTime updatedAt
    ) {
        private GovernedBusinessRule toRule(Set<String> schemaRefs, Set<String> aliases) {
            return new GovernedBusinessRule(id, name, text, owner, version, approvalStatus, effectiveStart,
                    effectiveEnd, datasourceId, tenantId, active, schemaRefs, aliases, contentHash, createdAt,
                    updatedAt);
        }
    }
}
