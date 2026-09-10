package com.nlda.format;

import com.nlda.audit.AuditContext;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

@Service
public class ResultFormatter {

    private static final Logger log = LoggerFactory.getLogger(ResultFormatter.class);

    public String answer(TableResult table) {
        long started = System.nanoTime();
        String answer;
        if (table.rows().isEmpty()) {
            answer = "No matching data was found.";
        } else {
            answer = "Found " + table.rows().size() + " matching row(s).";
        }
        log.info("flowEvent=response.format.completed traceId={} status=OK latencyMs={} rowCount={}",
                AuditContext.traceId(), (System.nanoTime() - started) / 1_000_000, table.rows().size());
        return answer;
    }
}
