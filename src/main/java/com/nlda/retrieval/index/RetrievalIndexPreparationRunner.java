package com.nlda.retrieval.index;

import com.nlda.retrieval.contract.SchemaRetriever;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;

@Component
@Order(Ordered.HIGHEST_PRECEDENCE + 100)
public class RetrievalIndexPreparationRunner implements ApplicationRunner {

    private final SchemaRetriever schemaRetriever;

    public RetrievalIndexPreparationRunner(SchemaRetriever schemaRetriever) {
        this.schemaRetriever = schemaRetriever;
    }

    @Override
    public void run(ApplicationArguments args) {
        schemaRetriever.prepare();
    }
}
