INSERT INTO customers (id, name, region, vip) VALUES
    (1, 'Acme Retail', 'Hanoi', TRUE),
    (2, 'Blue Ocean Market', 'Da Nang', FALSE),
    (3, 'Continental Stores', 'Ho Chi Minh City', TRUE);

INSERT INTO products (id, name, category) VALUES
    (1, 'Analytics Starter', 'Software'),
    (2, 'Database Support', 'Service'),
    (3, 'Security Audit', 'Service');

INSERT INTO orders (id, customer_id, order_date, status, total_amount) VALUES
    (1, 1, DATE '2026-01-15', 'DELIVERED', 2500.00),
    (2, 1, DATE '2026-02-12', 'PENDING', 750.00),
    (3, 2, DATE '2026-02-20', 'SHIPPED', 1200.00),
    (4, 3, DATE '2026-03-05', 'DELIVERED', 4300.00);

INSERT INTO order_items (id, order_id, product_id, quantity, unit_price) VALUES
    (1, 1, 1, 5, 300.00),
    (2, 1, 2, 2, 500.00),
    (3, 2, 3, 1, 750.00),
    (4, 3, 1, 4, 300.00),
    (5, 4, 2, 5, 500.00),
    (6, 4, 3, 3, 600.00);
