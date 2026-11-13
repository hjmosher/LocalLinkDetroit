-- Portable subset (works on H2 and PostgreSQL). Loaded at startup; data comes from seed/*.json.
-- Authoritative copy lives here: backend/src/main/resources/schema.sql
CREATE TABLE skill (
  id               VARCHAR(64)  PRIMARY KEY,
  label            VARCHAR(120) NOT NULL,
  grp              VARCHAR(32)  NOT NULL,
  regulated_hint   BOOLEAN      NOT NULL
);

CREATE TABLE provider (
  id                    VARCHAR(32)  PRIMARY KEY,
  name                  VARCHAR(160) NOT NULL,
  structure             VARCHAR(16)  NOT NULL,  -- sole_prop | partnership | llc | corp
  city                  VARCHAR(64)  NOT NULL,
  lat                   DOUBLE       NOT NULL,
  lon                   DOUBLE       NOT NULL,
  service_radius_miles  INTEGER      NOT NULL,
  summary               VARCHAR(300) NOT NULL,
  lara_status           VARCHAR(32)  NOT NULL,  -- active_good_standing | not_in_good_standing | not_applicable (simulated)
  availability          VARCHAR(16)  NOT NULL,  -- this_month | next_month | flexible
  fictional             BOOLEAN      NOT NULL DEFAULT TRUE
);

CREATE TABLE provider_skill (
  provider_id  VARCHAR(32) NOT NULL REFERENCES provider(id),
  skill_id     VARCHAR(64) NOT NULL REFERENCES skill(id),
  position     INTEGER     NOT NULL,            -- 0 = primary skill
  PRIMARY KEY (provider_id, skill_id)
);

CREATE TABLE license (
  id              INTEGER      PRIMARY KEY,
  provider_id     VARCHAR(32)  NOT NULL REFERENCES provider(id),
  type            VARCHAR(120) NOT NULL,
  state_verified  BOOLEAN      NOT NULL        -- simulated flag
);

CREATE TABLE license_skill (
  license_id  INTEGER     NOT NULL REFERENCES license(id),
  skill_id    VARCHAR(64) NOT NULL REFERENCES skill(id),
  PRIMARY KEY (license_id, skill_id)
);
