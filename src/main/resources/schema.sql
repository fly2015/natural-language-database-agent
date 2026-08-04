DROP TABLE IF EXISTS order_items;
DROP TABLE IF EXISTS orders;
DROP TABLE IF EXISTS products;
DROP TABLE IF EXISTS customers;

CREATE TABLE customers (
    id BIGINT PRIMARY KEY,
    name VARCHAR(120) NOT NULL,
    region VARCHAR(80) NOT NULL,
    vip BOOLEAN NOT NULL DEFAULT FALSE
);

CREATE TABLE products (
    id BIGINT PRIMARY KEY,
    name VARCHAR(120) NOT NULL,
    category VARCHAR(80) NOT NULL
);

CREATE TABLE orders (
    id BIGINT PRIMARY KEY,
    customer_id BIGINT NOT NULL REFERENCES customers(id),
    order_date DATE NOT NULL,
    status VARCHAR(40) NOT NULL,
    total_amount DECIMAL(12, 2) NOT NULL
);

CREATE TABLE order_items (
    id BIGINT PRIMARY KEY,
    order_id BIGINT NOT NULL REFERENCES orders(id),
    product_id BIGINT NOT NULL REFERENCES products(id),
    quantity INTEGER NOT NULL,
    unit_price DECIMAL(12, 2) NOT NULL
);
