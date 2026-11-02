-- WasteLens Initial Schema Migration V1
-- Target: Supabase PostgreSQL / Local PostgreSQL
-- Schema: wastelens_private (isolated from PostgREST / public access)

CREATE SCHEMA IF NOT EXISTS wastelens_private;
REVOKE ALL ON SCHEMA wastelens_private FROM PUBLIC;

CREATE TABLE IF NOT EXISTS wastelens_private.waste_entry (
  id            uuid PRIMARY KEY DEFAULT gen_random_uuid(),
  owner_id      uuid NOT NULL,
  submission_id uuid NOT NULL,
  entry_date    date NOT NULL CHECK (entry_date >= DATE '2020-01-01'),
  category      text NOT NULL CHECK (category IN ('produce','grains','dairy','protein','prepared','other')),
  grams         integer NOT NULL CHECK (grams BETWEEN 1 AND 100000),
  stage         text NOT NULL CHECK (stage IN ('preparation','service','storage')),
  reason        text NOT NULL CHECK (reason IN ('overproduction','spoilage','trimming','plate_return','other')),
  created_at    timestamptz NOT NULL DEFAULT now(),
  UNIQUE (owner_id, submission_id)
);

CREATE INDEX IF NOT EXISTS waste_entry_owner_date_idx
  ON wastelens_private.waste_entry (owner_id, entry_date, id);

REVOKE ALL ON wastelens_private.waste_entry FROM PUBLIC;

-- Revoke from cloud IdP roles if they exist
DO $$
BEGIN
  IF EXISTS (SELECT 1 FROM pg_roles WHERE rolname = 'anon') THEN
    EXECUTE 'REVOKE ALL ON SCHEMA wastelens_private FROM anon';
    EXECUTE 'REVOKE ALL ON wastelens_private.waste_entry FROM anon';
  END IF;
  IF EXISTS (SELECT 1 FROM pg_roles WHERE rolname = 'authenticated') THEN
    EXECUTE 'REVOKE ALL ON SCHEMA wastelens_private FROM authenticated';
    EXECUTE 'REVOKE ALL ON wastelens_private.waste_entry FROM authenticated';
  END IF;
END $$;

-- Decision D2: Secondary DB protection with Row Level Security
ALTER TABLE wastelens_private.waste_entry ENABLE ROW LEVEL SECURITY;

