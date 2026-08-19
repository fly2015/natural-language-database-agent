package com.nlda.retrieval.governance;

import org.springframework.stereotype.Service;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.LocalDateTime;
import java.util.HexFormat;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.stream.Collectors;

@Service
public class BusinessRuleGovernanceService {

    private final GovernedBusinessRuleRepository repository;
    private final RetrievalIndexRebuildService rebuildService;

    public BusinessRuleGovernanceService(
            GovernedBusinessRuleRepository repository,
            RetrievalIndexRebuildService rebuildService
    ) {
        this.repository = repository;
        this.rebuildService = rebuildService;
    }

    public List<GovernedBusinessRule> listRules() {
        return repository.findAll();
    }

    public GovernedBusinessRule saveDraft(
            String id,
            String name,
            String text,
            String owner,
            Integer version,
            LocalDateTime effectiveStart,
            LocalDateTime effectiveEnd,
            String datasourceId,
            String tenantId,
            Set<String> schemaRefs,
            Set<String> aliases
    ) {
        if (blank(id)) {
            throw new IllegalArgumentException("Business rule id is required.");
        }
        if (blank(text)) {
            throw new IllegalArgumentException("Business rule text is required.");
        }
        GovernedBusinessRule rule = new GovernedBusinessRule(
                normalize(id),
                blank(name) ? normalize(id) : name.strip(),
                text == null ? "" : text.strip(),
                blank(owner) ? "unknown" : owner.strip(),
                version == null || version < 1 ? 1 : version,
                ApprovalStatus.DRAFT,
                effectiveStart,
                effectiveEnd,
                blank(datasourceId) ? "default" : datasourceId.strip(),
                tenantId == null ? "" : tenantId.strip(),
                true,
                normalizeSet(schemaRefs),
                normalizeSet(aliases),
                "",
                null,
                null
        );
        return repository.save(withContentHash(rule));
    }

    public GovernedBusinessRule approve(String id) {
        repository.updateStatus(id, ApprovalStatus.APPROVED, true);
        rebuildService.rebuildSelectedRule(id);
        return repository.findById(id).orElseThrow();
    }

    public GovernedBusinessRule deactivate(String id) {
        repository.updateStatus(id, ApprovalStatus.INACTIVE, false);
        rebuildService.rebuildSelectedRule(id);
        return repository.findById(id).orElseThrow();
    }

    public RebuildResult reindex(String id) {
        return rebuildService.rebuildSelectedRule(id);
    }

    private GovernedBusinessRule withContentHash(GovernedBusinessRule rule) {
        return new GovernedBusinessRule(rule.id(), rule.name(), rule.text(), rule.owner(), rule.version(),
                rule.approvalStatus(), rule.effectiveStart(), rule.effectiveEnd(), rule.datasourceId(),
                rule.tenantId(), rule.active(), rule.schemaRefs(), rule.aliases(), contentHash(rule),
                rule.createdAt(), rule.updatedAt());
    }

    private String contentHash(GovernedBusinessRule rule) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            digest.update(canonicalText(rule).getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(digest.digest());
        } catch (NoSuchAlgorithmException ex) {
            throw new IllegalStateException("SHA-256 is not available.", ex);
        }
    }

    private String canonicalText(GovernedBusinessRule rule) {
        return "id=" + rule.id()
                + "\nversion=" + rule.version()
                + "\ndatasource=" + rule.datasourceId()
                + "\ntenant=" + rule.tenantId()
                + "\nschemaRefs=" + rule.schemaRefs().stream().sorted().toList()
                + "\naliases=" + rule.aliases().stream().sorted().toList()
                + "\ntext=" + rule.text();
    }

    private Set<String> normalizeSet(Set<String> values) {
        if (values == null) {
            return Set.of();
        }
        return values.stream()
                .filter(value -> !blank(value))
                .map(this::normalize)
                .collect(Collectors.toUnmodifiableSet());
    }

    private String normalize(String value) {
        return value == null ? "" : value.toLowerCase(Locale.ROOT).strip();
    }

    private boolean blank(String value) {
        return value == null || value.isBlank();
    }
}
