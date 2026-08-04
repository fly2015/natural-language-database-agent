package com.metajpa.nlda.guardrail;

public interface SchemaCatalog {

    boolean tableExists(String tableName);

    boolean columnExists(String tableName, String columnName);
}
