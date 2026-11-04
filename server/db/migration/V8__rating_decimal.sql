-- Ratings keep their decimals now (the table's changes are fractional);
-- the game shows them rounded to a whole number.
ALTER TABLE users ALTER COLUMN rating SET DATA TYPE DOUBLE PRECISION;
