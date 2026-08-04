package com.metajpa.nlda.format;

import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

class ResultFormatterTest {

    private final ResultFormatter formatter = new ResultFormatter();

    @Test
    void explainsEmptyResultWithoutInventingData() {
        String answer = formatter.answer(new TableResult(List.of("customer_id"), List.of()));

        assertThat(answer).isEqualTo("No matching data was found.");
    }

    @Test
    void reportsReturnedRowCount() {
        String answer = formatter.answer(new TableResult(
                List.of("customer_id"),
                List.of(Map.of("customer_id", 1), Map.of("customer_id", 2))
        ));

        assertThat(answer).isEqualTo("Found 2 matching row(s).");
    }
}
