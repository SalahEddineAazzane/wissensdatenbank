CREATE TABLE notiz_historie (
                                id BIGSERIAL PRIMARY KEY,
                                notiz_id BIGINT NOT NULL REFERENCES notizen(id),
                                version INT NOT NULL,
                                titel TEXT NOT NULL,
                                inhalt TEXT NOT NULL,
                                aktion TEXT NOT NULL,
                                zeitpunkt TIMESTAMPTZ NOT NULL DEFAULT now()
);

CREATE INDEX idx_historie_notiz ON notiz_historie (notiz_id, version);

CREATE FUNCTION notiz_historie_schreiben() RETURNS trigger AS $$
BEGIN
    IF TG_OP = 'INSERT' THEN
        INSERT INTO notiz_historie (notiz_id, version, titel, inhalt, aktion)
        VALUES (NEW.id, NEW.version, NEW.titel, NEW.inhalt, 'ERSTELLT');
    ELSIF OLD.geloescht_am IS NULL AND NEW.geloescht_am IS NOT NULL THEN
        INSERT INTO notiz_historie (notiz_id, version, titel, inhalt, aktion)
        VALUES (NEW.id, NEW.version, NEW.titel, NEW.inhalt, 'GELOESCHT');
ELSE
        INSERT INTO notiz_historie (notiz_id, version, titel, inhalt, aktion)
        VALUES (NEW.id, NEW.version, NEW.titel, NEW.inhalt, 'GEAENDERT');
END IF;
RETURN NEW;
END;
$$ LANGUAGE plpgsql;

CREATE TRIGGER trg_notiz_historie
    AFTER INSERT OR UPDATE ON notizen
                        FOR EACH ROW EXECUTE FUNCTION notiz_historie_schreiben();