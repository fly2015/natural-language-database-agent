package com.nlda.api;

import com.nlda.audit.AuditEvent;
import com.nlda.audit.AuditLogger;
import com.nlda.format.TableResult;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.web.HttpMediaTypeNotSupportedException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

@RestControllerAdvice(assignableTypes = QueryController.class)
public class ApiExceptionHandler {

    private final AuditLogger auditLogger;

    public ApiExceptionHandler() {
        this.auditLogger = null;
    }

    @Autowired
    public ApiExceptionHandler(AuditLogger auditLogger) {
        this.auditLogger = auditLogger;
    }

    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<QueryResponse> handleValidationFailure() {
        QueryResponse response = rejected("Question is required and must be 1000 characters or fewer.");
        auditRejectedRequest(response, "request.validation", "Question validation failed.");
        return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(response);
    }

    @ExceptionHandler(HttpMessageNotReadableException.class)
    public ResponseEntity<QueryResponse> handleMalformedJson() {
        QueryResponse response = rejected("Request body must be JSON with a non-empty question field.");
        auditRejectedRequest(response, "request.parse", "Malformed JSON request body.");
        return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(response);
    }

    @ExceptionHandler(HttpMediaTypeNotSupportedException.class)
    public ResponseEntity<QueryResponse> handleUnsupportedMediaType() {
        QueryResponse response = rejected("Content-Type must be application/json.");
        auditRejectedRequest(response, "request.media_type", "Unsupported media type.");
        return ResponseEntity.status(HttpStatus.UNSUPPORTED_MEDIA_TYPE).body(response);
    }

    @ExceptionHandler(Exception.class)
    public ResponseEntity<QueryResponse> handleUnexpectedFailure(Exception ex) {
        QueryResponse response = rejected("The request could not be completed safely.");
        auditRejectedRequest(response, "request.error", "Unexpected request failure.");
        return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(response);
    }

    private QueryResponse rejected(String reason) {
        return new QueryResponse("REJECTED", "", new TableResult(List.of(), List.of()), null,
                UUID.randomUUID().toString(), 0, List.of(), reason);
    }

    private void auditRejectedRequest(QueryResponse response, String stepName, String message) {
        if (auditLogger == null) {
            return;
        }
        AuditEvent audit = auditLogger.start(response.traceId(), "");
        audit.step("request.received", "REJECTED", 0, map(), map("message", message));
        audit.step(stepName, "REJECTED", 0, map(), map("reason", response.reason()));
        audit.complete(response.status(), response.latencyMs(), map("status", response.status(), "reason",
                response.reason(), "traceId", response.traceId()));
        auditLogger.record(audit);
    }

    private Map<String, Object> map(Object... values) {
        Map<String, Object> map = new LinkedHashMap<>();
        for (int i = 0; i < values.length; i += 2) {
            map.put((String) values[i], values[i + 1]);
        }
        return map;
    }
}
