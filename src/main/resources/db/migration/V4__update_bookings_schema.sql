ALTER TABLE bookings
DROP COLUMN status,
    DROP COLUMN payment_status,
    DROP COLUMN created_at,
    DROP COLUMN updated_at;

ALTER TABLE bookings
    ADD COLUMN booked_at TIMESTAMP;