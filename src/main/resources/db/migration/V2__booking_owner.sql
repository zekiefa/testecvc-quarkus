-- Dono da reserva: o `sub` do JWT que a criou.
-- Necessário para a autorização por dono decidida no ADR-003 (M1.4): consultar,
-- cancelar, check-in e check-out só valem para quem criou a reserva.

ALTER TABLE booking ADD COLUMN user_subject TEXT;
UPDATE booking SET user_subject = '' WHERE user_subject IS NULL;
ALTER TABLE booking ALTER COLUMN user_subject SET NOT NULL;

CREATE INDEX booking_user_subject_idx ON booking (user_subject);
