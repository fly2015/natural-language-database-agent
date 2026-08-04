package com.metajpa.nlda.orchestrator;

import com.metajpa.nlda.api.QueryResponse;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest
class AgentOrchestratorIntegrationTest {

    @Autowired
    private AgentOrchestrator orchestrator;

    @Test
    void answersRepresentativeBusinessQuestion() {
        QueryResponse response = orchestrator.answer("Show top 10 customers by total spending in 2026");

        assertThat(response.status()).isEqualTo("OK");
        assertThat(response.sql()).contains("SELECT c.id AS customer_id");
        assertThat(response.sql()).contains("LIMIT 10");
        assertThat(response.table().rows()).isNotEmpty();
        assertThat(response.table().columns()).contains("customer_id", "customer_name", "total_spending");
    }

    @Test
    void rejectsUnsupportedQuestionBeforeSqlExecution() {
        QueryResponse response = orchestrator.answer("What is employee churn by department?");

        assertThat(response.status()).isEqualTo("REJECTED");
        assertThat(response.sql()).isNull();
        assertThat(response.reason()).contains("trusted schema context");
    }

    @Test
    void appliesRegionFilterWhenPresent() {
        QueryResponse response = orchestrator.answer("List all undelivered orders in the Hanoi region");

        assertThat(response.status()).isEqualTo("OK");
        assertThat(response.sql()).contains("c.region = 'Hanoi'");
        assertThat(response.table().rows()).hasSize(1);
    }

    @Test
    void supportsSingularCustomerWhenMetricIsExplicit() {
        QueryResponse response = orchestrator.answer("Show top 10 customer by total spending in 2026");

        assertThat(response.status()).isEqualTo("OK");
        assertThat(response.sql()).contains("SUM(o.total_amount)");
        assertThat(response.table().rows()).isNotEmpty();
    }

    @Test
    void asksForMetricWhenTopCustomerQuestionIsAmbiguous() {
        QueryResponse response = orchestrator.answer("Show top 10 customer in 2026");

        assertThat(response.status()).isEqualTo("REJECTED");
        assertThat(response.sql()).isNull();
        assertThat(response.reason()).contains("ranking metric");
    }
}
