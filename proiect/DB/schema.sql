-- ─────────────────────────────────────────────────────────────────────────────
-- MindBuddy — schema bazei de date (PostgreSQL)
-- ─────────────────────────────────────────────────────────────────────────────
-- Ruleaza o singura data pe o baza goala:
--   psql -U <user> -d <database> -f schema.sql
-- sau deschizi fisierul in pgAdmin si dai Run.
-- Tabelele si coloanele sunt cele folosite de backend/index.js.

-- ─── USERS ──────────────────────────────────────────────────────────────────
-- pin-ul de blocare se tine pe telefon (EncryptedSharedPreferences), in DB
-- ramane doar codul de resetare pin trimis pe email.
CREATE TABLE IF NOT EXISTS users (
  id                          SERIAL PRIMARY KEY,
  username                    TEXT NOT NULL UNIQUE,
  email                       TEXT NOT NULL UNIQUE,
  password_hash               TEXT NOT NULL,
  is_verified                 BOOLEAN NOT NULL DEFAULT FALSE,
  verification_token          TEXT,
  reset_token                 TEXT,
  reset_token_expires         TIMESTAMPTZ,
  pin_reset_code              TEXT,
  pin_reset_expires           TIMESTAMPTZ,
  crisis_locked               BOOLEAN NOT NULL DEFAULT FALSE,
  crisis_locked_at            TIMESTAMPTZ,
  crisis_unlock_token         TEXT,
  crisis_unlock_token_expires TIMESTAMPTZ,
  trusted_contact_email       TEXT,
  created_at                  TIMESTAMPTZ NOT NULL DEFAULT NOW()
);

-- ─── CONVERSATIONS ────────────────────────────────────────────────────────────
CREATE TABLE IF NOT EXISTS conversations (
  id         SERIAL PRIMARY KEY,
  user_id    INTEGER NOT NULL REFERENCES users(id) ON DELETE CASCADE,
  title      TEXT NOT NULL DEFAULT 'Conversatie noua',
  created_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
  updated_at TIMESTAMPTZ
);

-- ─── MESSAGES ─────────────────────────────────────────────────────────────────
-- role e 'user' sau 'assistant'. la stergerea conversatiei se sterg si mesajele.
CREATE TABLE IF NOT EXISTS messages (
  id              SERIAL PRIMARY KEY,
  conversation_id INTEGER NOT NULL REFERENCES conversations(id) ON DELETE CASCADE,
  role            TEXT NOT NULL,
  content         TEXT NOT NULL,
  created_at      TIMESTAMPTZ NOT NULL DEFAULT NOW()
);

-- ─── MOOD_ENTRIES ─────────────────────────────────────────────────────────────
-- un rand pentru fiecare mesaj clasificat. valorile emotiilor sunt 0..100.
-- suicidal_mention e flag-ul pe care se construieste pragul de criza.
CREATE TABLE IF NOT EXISTS mood_entries (
  id               SERIAL PRIMARY KEY,
  user_id          INTEGER NOT NULL REFERENCES users(id) ON DELETE CASCADE,
  happy            INTEGER NOT NULL DEFAULT 0,
  sad              INTEGER NOT NULL DEFAULT 0,
  anxious          INTEGER NOT NULL DEFAULT 0,
  angry            INTEGER NOT NULL DEFAULT 0,
  neutral          INTEGER NOT NULL DEFAULT 0,
  dominant_emotion TEXT,
  text_excerpt     TEXT,
  suicidal_mention BOOLEAN NOT NULL DEFAULT FALSE,
  created_at       TIMESTAMPTZ NOT NULL DEFAULT NOW()
);

-- ─── DEVICE_CRISIS_LOCKS ──────────────────────────────────────────────────────
-- blocare la nivel de device, ca sa tina si dupa stergerea/recrearea contului.
-- device_id e unic (backend foloseste ON CONFLICT (device_id)).
CREATE TABLE IF NOT EXISTS device_crisis_locks (
  id          SERIAL PRIMARY KEY,
  device_id   TEXT NOT NULL UNIQUE,
  locked_at   TIMESTAMPTZ NOT NULL DEFAULT NOW(),
  unlocked_at TIMESTAMPTZ
);

-- ─── INDEXURI ─────────────────────────────────────────────────────────────────
-- pe interogarile frecvente: istoricul unei conversatii, graficul de emotii,
-- lista de conversatii ordonata dupa ultima activitate.
CREATE INDEX IF NOT EXISTS idx_messages_conversation ON messages(conversation_id, created_at);
CREATE INDEX IF NOT EXISTS idx_mood_user_created     ON mood_entries(user_id, created_at);
CREATE INDEX IF NOT EXISTS idx_conversations_user    ON conversations(user_id, updated_at);
