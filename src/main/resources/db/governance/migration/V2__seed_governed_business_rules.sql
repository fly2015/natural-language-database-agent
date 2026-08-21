DELETE FROM governed_business_rule_alias
WHERE rule_id IN ('rule.revenue', 'rule.undelivered', 'rule.customer_alias');

DELETE FROM governed_business_rule_schema_ref
WHERE rule_id IN ('rule.revenue', 'rule.undelivered', 'rule.customer_alias');

DELETE FROM governed_business_rule
WHERE id IN ('rule.revenue', 'rule.undelivered', 'rule.customer_alias');

INSERT INTO governed_business_rule (
    id, name, text, owner, version, approval_status, datasource_id, tenant_id, active, created_at, updated_at
) VALUES
    (
        'rule.revenue',
        'Revenue',
        'Business rule: revenue and spending use monetary orders.total_amount unless product-level revenue is needed.',
        'data-governance',
        1,
        'APPROVED',
        'default',
        '',
        true,
        CURRENT_TIMESTAMP,
        CURRENT_TIMESTAMP
    ),
    (
        'rule.undelivered',
        'Undelivered orders',
        'Business rule: undelivered orders are any orders where orders.status is not DELIVERED.',
        'data-governance',
        1,
        'APPROVED',
        'default',
        '',
        true,
        CURRENT_TIMESTAMP,
        CURRENT_TIMESTAMP
    ),
    (
        'rule.customer_alias',
        'Customer aliases',
        'Business rule: client and customer refer to rows in customers.',
        'data-governance',
        1,
        'APPROVED',
        'default',
        '',
        true,
        CURRENT_TIMESTAMP,
        CURRENT_TIMESTAMP
    );

INSERT INTO governed_business_rule_schema_ref (rule_id, schema_ref) VALUES
    ('rule.revenue', 'orders'),
    ('rule.revenue', 'order_items'),
    ('rule.revenue', 'products'),
    ('rule.undelivered', 'orders'),
    ('rule.customer_alias', 'customers');

INSERT INTO governed_business_rule_alias (rule_id, alias) VALUES
    ('rule.revenue', 'revenue'),
    ('rule.revenue', 'spending'),
    ('rule.revenue', 'spend'),
    ('rule.revenue', 'sales'),
    ('rule.revenue', 'sale'),
    ('rule.undelivered', 'undelivered'),
    ('rule.undelivered', 'pending'),
    ('rule.undelivered', 'shipped'),
    ('rule.customer_alias', 'client'),
    ('rule.customer_alias', 'clients'),
    ('rule.customer_alias', 'customer'),
    ('rule.customer_alias', 'customers');
