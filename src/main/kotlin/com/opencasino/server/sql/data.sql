INSERT INTO players (balance, id, email, first_name, last_name)
VALUES ('10000.00', '74ed6d18-1be8-474b-9288-1e2c3ca92c02', 'user@test.com', 'john', 'gay') ON CONFLICT DO NOTHING;