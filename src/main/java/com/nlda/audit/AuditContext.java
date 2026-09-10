package com.nlda.audit;

import org.slf4j.MDC;

import java.util.Map;

public final class AuditContext {

    private static final ThreadLocal<AuditEvent> CURRENT = new ThreadLocal<>();

    private AuditContext() {
    }

    public static void set(AuditEvent event) {
        CURRENT.set(event);
        if (event != null) {
            MDC.put("traceId", event.getTraceId());
        }
    }

    public static void clear() {
        CURRENT.remove();
        MDC.remove("traceId");
    }

    public static String traceId() {
        AuditEvent event = CURRENT.get();
        return event == null ? "" : event.getTraceId();
    }

    public static void step(String name, String status, long durationMs, Map<String, Object> input,
            Map<String, Object> output) {
        step(name, null, status, durationMs, input, output);
    }

    public static void step(String name, String implementationClass, String status, long durationMs,
            Map<String, Object> input, Map<String, Object> output) {
        AuditEvent event = CURRENT.get();
        if (event != null) {
            event.step(name, implementationClass, status, durationMs, input, output);
        }
    }
}
