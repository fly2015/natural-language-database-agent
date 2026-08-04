package com.nlda.format;

import org.springframework.stereotype.Service;

@Service
public class ResultFormatter {

    public String answer(TableResult table) {
        if (table.rows().isEmpty()) {
            return "No matching data was found.";
        }
        return "Found " + table.rows().size() + " matching row(s).";
    }
}
