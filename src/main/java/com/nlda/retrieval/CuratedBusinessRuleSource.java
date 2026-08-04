package com.nlda.retrieval;

import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Set;

@Component
public class CuratedBusinessRuleSource implements BusinessRuleSource {

    @Override
    public List<BusinessRule> rules() {
        return List.of(
                new BusinessRule(
                        "rule.revenue",
                        "Business rule: revenue and spending use monetary orders.total_amount unless product-level revenue is needed.",
                        Set.of("orders", "order_items", "products"),
                        Set.of("revenue", "spending", "spend", "sales", "sale")
                ),
                new BusinessRule(
                        "rule.undelivered",
                        "Business rule: undelivered orders are any orders where orders.status is not DELIVERED.",
                        Set.of("orders"),
                        Set.of("undelivered", "pending", "shipped")
                ),
                new BusinessRule(
                        "rule.customer_alias",
                        "Business rule: client and customer refer to rows in customers.",
                        Set.of("customers"),
                        Set.of("client", "clients", "customer", "customers")
                )
        );
    }
}
