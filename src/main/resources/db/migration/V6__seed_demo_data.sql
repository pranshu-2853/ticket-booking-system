-- =========================================
-- Demo Users
-- Password for both users: 123456
-- =========================================

INSERT INTO users (email, password, role_id)
VALUES (
           '[admin@test.com](mailto:admin@test.com)',
           '$2b$10$gcauHojVYNwJ0U5IJymba.eSGUwHAFwJRbDaJJH11ZiGvhtHt0k06',
           (SELECT id FROM roles WHERE name = 'ROLE_ADMIN')
       );

INSERT INTO users (email, password, role_id)
VALUES (
           '[user@test.com](mailto:user@test.com)',
           '$2b$10$gcauHojVYNwJ0U5IJymba.eSGUwHAFwJRbDaJJH11ZiGvhtHt0k06',
           (SELECT id FROM roles WHERE name = 'ROLE_USER')
       );

-- =========================================
-- Demo Events
-- =========================================

INSERT INTO events (name, location, event_time)
VALUES
    (
        'Coldplay Concert',
        'Ahmedabad',
        '2026-12-31 18:00:00'
    ),
    (
        'IPL Final',
        'Narendra Modi Stadium',
        '2026-05-30 19:30:00'
    );

-- =========================================
-- Seats For Event 1
-- =========================================

INSERT INTO seats
(event_id, seat_number, status, version, created_at, updated_at)
VALUES
    (1, 'A11', 'AVAILABLE', 0, NOW(), NOW()),
    (1, 'A12', 'AVAILABLE', 0, NOW(), NOW()),
    (1, 'A13', 'AVAILABLE', 0, NOW(), NOW()),
    (1, 'A14', 'AVAILABLE', 0, NOW(), NOW()),
    (1, 'A15', 'AVAILABLE', 0, NOW(), NOW());

-- =========================================
-- Seats For Event 2
-- =========================================

INSERT INTO seats
(event_id, seat_number, status, version, created_at, updated_at)
VALUES
    (2, 'A11', 'AVAILABLE', 0, NOW(), NOW()),
    (2, 'A12', 'AVAILABLE', 0, NOW(), NOW()),
    (2, 'A13', 'AVAILABLE', 0, NOW(), NOW()),
    (2, 'A14', 'AVAILABLE', 0, NOW(), NOW()),
    (2, 'A15', 'AVAILABLE', 0, NOW(), NOW());
