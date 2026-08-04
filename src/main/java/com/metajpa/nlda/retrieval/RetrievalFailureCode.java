package com.metajpa.nlda.retrieval;

public enum RetrievalFailureCode {
    NONE("NONE"),
    RF_01("RF-01"),
    RF_02("RF-02"),
    RF_03("RF-03"),
    RF_04("RF-04");

    private final String code;

    RetrievalFailureCode(String code) {
        this.code = code;
    }

    public String code() {
        return code;
    }
}
