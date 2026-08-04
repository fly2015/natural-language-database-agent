package com.nlda.api;

import com.nlda.format.TableResult;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.web.HttpMediaTypeNotSupportedException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

import java.util.List;
import java.util.UUID;

@RestControllerAdvice(assignableTypes = QueryController.class)
public class ApiExceptionHandler {

    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<QueryResponse> handleValidationFailure() {
        QueryResponse response = rejected("Question is required and must be 1000 characters or fewer.");
        return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(response);
    }

    @ExceptionHandler(HttpMessageNotReadableException.class)
    public ResponseEntity<QueryResponse> handleMalformedJson() {
        QueryResponse response = rejected("Request body must be JSON with a non-empty question field.");
        return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(response);
    }

    @ExceptionHandler(HttpMediaTypeNotSupportedException.class)
    public ResponseEntity<QueryResponse> handleUnsupportedMediaType() {
        QueryResponse response = rejected("Content-Type must be application/json.");
        return ResponseEntity.status(HttpStatus.UNSUPPORTED_MEDIA_TYPE).body(response);
    }

    @ExceptionHandler(Exception.class)
    public ResponseEntity<QueryResponse> handleUnexpectedFailure(Exception ex) {
        QueryResponse response = rejected("The request could not be completed safely.");
        return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(response);
    }

    private QueryResponse rejected(String reason) {
        return new QueryResponse("REJECTED", "", new TableResult(List.of(), List.of()), null,
                UUID.randomUUID().toString(), 0, List.of(), reason);
    }
}
