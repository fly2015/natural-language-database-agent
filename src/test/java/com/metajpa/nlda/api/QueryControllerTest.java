package com.metajpa.nlda.api;

import com.metajpa.nlda.format.TableResult;
import com.metajpa.nlda.orchestrator.AgentOrchestrator;
import org.junit.jupiter.api.Test;
import org.springframework.http.ResponseEntity;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class QueryControllerTest {

    @Test
    void returnsStructuredResponse() {
        QueryResponse expected = new QueryResponse(
                "OK",
                "Found 1 matching row(s).",
                new TableResult(List.of("ORDER_ID"), List.of(Map.of("ORDER_ID", 1))),
                "SELECT id AS order_id FROM orders LIMIT 100",
                "trace-1",
                12,
                List.of(),
                ""
        );
        AgentOrchestrator orchestrator = mock(AgentOrchestrator.class);
        when(orchestrator.answer("List all undelivered orders in the Hanoi region")).thenReturn(expected);

        QueryController controller = new QueryController(orchestrator);
        ResponseEntity<QueryResponse> response = controller.query(
                new QueryRequest("List all undelivered orders in the Hanoi region"));

        assertThat(response.getStatusCode().is2xxSuccessful()).isTrue();
        assertThat(response.getBody()).isEqualTo(expected);
        assertThat(response.getBody().status()).isEqualTo("OK");
        assertThat(response.getBody().table().columns()).contains("ORDER_ID");
        assertThat(response.getBody().sql()).contains("LIMIT 100");
        assertThat(response.getBody().traceId()).isEqualTo("trace-1");
    }
}
