CREATE TABLE notizen (
                         id BIGSERIAL PRIMARY KEY,
                         titel TEXT NOT NULL,
                         inhalt TEXT NOT NULL DEFAULT '',
                         version INT NOT NULL DEFAULT 1,
                         geloescht_am TIMESTAMPTZ,
                         erstellt_am TIMESTAMPTZ NOT NULL DEFAULT now(),
                         geaendert_am TIMESTAMPTZ NOT NULL DEFAULT now(),
                         suchvektor tsvector GENERATED ALWAYS AS (
                             setweight(to_tsvector('german', titel), 'A') ||
                             setweight(to_tsvector('german', inhalt), 'B')
                             ) STORED
);

CREATE INDEX idx_notizen_suche ON notizen USING GIN (suchvektor);

CREATE TABLE tags (
                      id BIGSERIAL PRIMARY KEY,
                      name TEXT NOT NULL UNIQUE
);

CREATE TABLE notiz_tags (
                            notiz_id BIGINT NOT NULL REFERENCES notizen(id),
                            tag_id BIGINT NOT NULL REFERENCES tags(id),
                            PRIMARY KEY (notiz_id, tag_id)
);