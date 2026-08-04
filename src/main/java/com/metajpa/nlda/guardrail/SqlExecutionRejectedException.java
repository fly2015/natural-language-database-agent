package com.metajpa.nlda.guardrail;

public class SqlExecutionRejectedException extends RuntimeException {

    public SqlExecutionRejectedException(String message) {
        super(message);
    }
}
