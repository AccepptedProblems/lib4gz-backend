-- Lightning Kart: each race records the question numbers the server judged wrong, a timeout
-- included, in race order. The answer UPDATE appends to it, so checking an answer stays one
-- statement. The default keeps rows written before this, and by a server still on the old code,
-- valid: they read as no wrong answers recorded.
-- Guarded: a database baselined at V1 is only assumed to match it.

ALTER TABLE IF EXISTS races ADD COLUMN IF NOT EXISTS wrong_seqs jsonb NOT NULL DEFAULT '[]';
