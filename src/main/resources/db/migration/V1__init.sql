CREATE TABLE roles (
                       id BIGSERIAL PRIMARY KEY,
                       name VARCHAR(255) NOT NULL UNIQUE
);

CREATE TABLE users (
                       id BIGSERIAL PRIMARY KEY,
                       email VARCHAR(255) NOT NULL UNIQUE,
                       password VARCHAR(255) NOT NULL,
                       role_id BIGINT NOT NULL,

                       CONSTRAINT fk_users_role
                           FOREIGN KEY (role_id)
                               REFERENCES roles(id)
);

CREATE TABLE refresh_tokens (
                                id BIGSERIAL PRIMARY KEY,
                                user_id BIGINT NOT NULL,
                                token VARCHAR(255) NOT NULL UNIQUE,
                                expiry_date TIMESTAMP NOT NULL,

                                CONSTRAINT fk_refresh_token_user
                                    FOREIGN KEY (user_id)
                                        REFERENCES users(id)
);

CREATE TABLE events (
                        id BIGSERIAL PRIMARY KEY,
                        name VARCHAR(255),
                        event_time TIMESTAMP,
                        location VARCHAR(255)
);

CREATE TABLE seats (
                       id BIGSERIAL PRIMARY KEY,
                       seat_number VARCHAR(255) NOT NULL,
                       status VARCHAR(50),
                       event_id BIGINT NOT NULL,

                       CONSTRAINT fk_seat_event
                           FOREIGN KEY (event_id)
                               REFERENCES events(id),

                       CONSTRAINT uq_event_seat
                           UNIQUE (event_id, seat_number)
);

CREATE INDEX idx_seat_event_id
    ON seats(event_id);

CREATE TABLE bookings (
                          id BIGSERIAL PRIMARY KEY,

                          user_id BIGINT NOT NULL,
                          seat_id BIGINT NOT NULL UNIQUE,

                          status VARCHAR(50) NOT NULL,
                          payment_status VARCHAR(50) NOT NULL,

                          created_at TIMESTAMP NOT NULL,
                          updated_at TIMESTAMP NOT NULL,

                          CONSTRAINT fk_booking_user
                              FOREIGN KEY (user_id)
                                  REFERENCES users(id),

                          CONSTRAINT fk_booking_seat
                              FOREIGN KEY (seat_id)
                                  REFERENCES seats(id)
);

CREATE INDEX idx_booking_user_id
    ON bookings(user_id);