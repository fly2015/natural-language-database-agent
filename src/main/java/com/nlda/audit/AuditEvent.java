package com.nlda.audit;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.time.Instant;

public class AuditEvent {

    private final String traceId;
    private final String startedAt;
    private final String question;
    private final List<AuditStep> steps = new ArrayList<>();
    private String status;
    private long durationMs;
    private Map<String, Object> response = Map.of();

    public AuditEvent(String traceId, String question) {
        this.traceId = traceId;
        this.question = question;
        this.startedAt = Instant.now().toString();
    }

    public String getTraceId() {
        return traceId;
    }

    public String getStartedAt() {
        return startedAt;
    }

    public String getQuestion() {
        return question;
    }

    public List<AuditStep> getSteps() {
        return List.copyOf(steps);
    }

    public String getStatus() {
        return status;
    }

    public long getDurationMs() {
        return durationMs;
    }

    public Map<String, Object> getResponse() {
        return response;
    }

    public void step(String name, String status, long durationMs, Map<String, Object> input,
            Map<String, Object> output) {
        step(name, null, status, durationMs, input, output);
    }

    public void step(String name, String implementationClass, String status, long durationMs,
            Map<String, Object> input, Map<String, Object> output) {
        steps.add(new AuditStep(steps.size() + 1, name, implementationClass, status, durationMs, safe(input),
                safe(output)));
    }

    public void complete(String status, long durationMs, Map<String, Object> response) {
        this.status = status;
        this.durationMs = durationMs;
        this.response = safe(response);
    }

    private Map<String, Object> safe(Map<String, Object> value) {
        return value == null ? Map.of() : Collections.unmodifiableMap(new LinkedHashMap<>(value));
    }
}
