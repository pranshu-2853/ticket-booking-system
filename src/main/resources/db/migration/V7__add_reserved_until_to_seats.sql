-- Reservation deadline for the RESERVED seat state introduced by the
-- reserve -> pay -> confirm booking flow. Nullable: only set while a seat is
-- RESERVED, cleared on BOOKED or AVAILABLE. The reservation sweeper reclaims
-- seats whose reserved_until is in the past.
ALTER TABLE seats
    ADD COLUMN reserved_until TIMESTAMP;
