package de.salaheddineaazzane.wissensdatenbank;

import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import java.time.OffsetDateTime;
import java.util.*;

@Service
public class NotizService {

    public record Notiz(Long id, String titel, String inhalt, int version, List<String> tags,
                        OffsetDateTime erstelltAm, OffsetDateTime geaendertAm) {}
    public record Treffer(Long id, String titel, String auszug, double rang) {}
    public record Historie(int version, String titel, String inhalt, String aktion, OffsetDateTime zeitpunkt) {}

    private final JdbcClient jdbc;

    public NotizService(JdbcClient jdbc) {
        this.jdbc = jdbc;
    }

    @Transactional
    public long erstelle(String titel, String inhalt, List<String> tags) {
        long id = jdbc.sql("INSERT INTO notizen (titel, inhalt) VALUES (:t, :i) RETURNING id")
                .param("t", titel).param("i", inhalt == null ? "" : inhalt)
                .query(Long.class).single();
        setzeTags(id, tags);
        return id;
    }

    @Transactional
    public boolean aendere(long id, String titel, String inhalt, List<String> tags) {
        int n = jdbc.sql("""
                UPDATE notizen SET titel = :t, inhalt = :i, version = version + 1, geaendert_am = now()
                WHERE id = :id AND geloescht_am IS NULL
                """)
                .param("t", titel).param("i", inhalt == null ? "" : inhalt).param("id", id).update();
        if (n == 0) return false;
        jdbc.sql("DELETE FROM notiz_tags WHERE notiz_id = :id").param("id", id).update();
        setzeTags(id, tags);
        return true;
    }

    @Transactional
    public boolean loesche(long id) {
        return jdbc.sql("""
                UPDATE notizen SET geloescht_am = now(), version = version + 1, geaendert_am = now()
                WHERE id = :id AND geloescht_am IS NULL
                """).param("id", id).update() > 0;
    }

    private void setzeTags(long notizId, List<String> tags) {
        if (tags == null) return;
        for (String roh : tags) {
            if (roh == null || roh.isBlank()) continue;
            String name = roh.trim().toLowerCase();
            jdbc.sql("INSERT INTO tags (name) VALUES (:n) ON CONFLICT (name) DO NOTHING")
                    .param("n", name).update();
            jdbc.sql("""
                    INSERT INTO notiz_tags (notiz_id, tag_id)
                    SELECT :nid, id FROM tags WHERE name = :n
                    ON CONFLICT DO NOTHING
                    """).param("nid", notizId).param("n", name).update();
        }
    }

    public Optional<Notiz> finde(long id) {
        return jdbc.sql("""
                SELECT n.id, n.titel, n.inhalt, n.version, n.erstellt_am, n.geaendert_am,
                       COALESCE((SELECT array_agg(t.name ORDER BY t.name)
                                 FROM notiz_tags nt JOIN tags t ON t.id = nt.tag_id
                                 WHERE nt.notiz_id = n.id), '{}') AS tags
                FROM notizen n
                WHERE n.id = :id AND n.geloescht_am IS NULL
                """)
                .param("id", id)
                .query((rs, i) -> new Notiz(
                        rs.getLong("id"), rs.getString("titel"), rs.getString("inhalt"), rs.getInt("version"),
                        Arrays.asList((String[]) rs.getArray("tags").getArray()),
                        rs.getObject("erstellt_am", OffsetDateTime.class),
                        rs.getObject("geaendert_am", OffsetDateTime.class)))
                .optional();
    }

    public List<Treffer> suche(String q, String tag, int seite, int groesse) {
        int limit = Math.min(Math.max(groesse, 1), 50);
        int offset = Math.max(seite, 0) * limit;
        return jdbc.sql("""
                SELECT n.id, n.titel,
                       ts_headline('german', n.inhalt, abfrage, 'MaxFragments=1,MaxWords=20,MinWords=5') AS auszug,
                       ts_rank(n.suchvektor, abfrage) AS rang
                FROM notizen n, websearch_to_tsquery('german', :q) abfrage
                WHERE n.geloescht_am IS NULL
                  AND n.suchvektor @@ abfrage
                  AND (CAST(:tag AS text) IS NULL OR EXISTS (
                        SELECT 1 FROM notiz_tags nt JOIN tags t ON t.id = nt.tag_id
                        WHERE nt.notiz_id = n.id AND t.name = CAST(:tag AS text)))
                ORDER BY rang DESC, n.id
                LIMIT :limit OFFSET :offset
                """)
                .param("q", q).param("tag", tag == null || tag.isBlank() ? null : tag.toLowerCase())
                .param("limit", limit).param("offset", offset)
                .query((rs, i) -> new Treffer(rs.getLong("id"), rs.getString("titel"),
                        rs.getString("auszug"), rs.getDouble("rang")))
                .list();
    }

    public List<Historie> historie(long id) {
        return jdbc.sql("""
                SELECT version, titel, inhalt, aktion, zeitpunkt
                FROM notiz_historie WHERE notiz_id = :id ORDER BY id
                """)
                .param("id", id)
                .query((rs, i) -> new Historie(rs.getInt("version"), rs.getString("titel"),
                        rs.getString("inhalt"), rs.getString("aktion"),
                        rs.getObject("zeitpunkt", OffsetDateTime.class)))
                .list();
    }
}