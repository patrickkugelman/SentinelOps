package io.sentinelops.agent.memory;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.stereotype.Repository;

import java.sql.Array;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.LocalDate;
import java.util.List;

/** Persistence for the incident-memory store (Postgres + pgvector) via raw SQL. */
@Repository
public class IncidentRepository {

    private final JdbcTemplate jdbc;

    public IncidentRepository(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    private static final String UPSERT = """
        INSERT INTO incident (id, source, title, occurred_on, affected_services, service_types,
            symptoms, symptom_type, error_signatures, error_pattern_category, root_cause, fix,
            source_url, embedding)
        VALUES (?,?,?,?,?,?,?,?,?,?,?,?,?, CAST(? AS vector))
        ON CONFLICT (id) DO UPDATE SET
            source=EXCLUDED.source, title=EXCLUDED.title, occurred_on=EXCLUDED.occurred_on,
            affected_services=EXCLUDED.affected_services, service_types=EXCLUDED.service_types,
            symptoms=EXCLUDED.symptoms, symptom_type=EXCLUDED.symptom_type,
            error_signatures=EXCLUDED.error_signatures, error_pattern_category=EXCLUDED.error_pattern_category,
            root_cause=EXCLUDED.root_cause, fix=EXCLUDED.fix, source_url=EXCLUDED.source_url,
            embedding=EXCLUDED.embedding
        """;

    public void save(Incident inc, float[] embedding) {
        jdbc.update(conn -> {
            var ps = conn.prepareStatement(UPSERT);
            ps.setString(1, inc.id());
            ps.setString(2, inc.source());
            ps.setString(3, inc.title());
            if (inc.occurredOn() != null) ps.setObject(4, inc.occurredOn());
            else ps.setNull(4, java.sql.Types.DATE);
            ps.setArray(5, conn.createArrayOf("text", inc.affectedServices().toArray()));
            ps.setArray(6, conn.createArrayOf("text", inc.serviceTypes().toArray()));
            ps.setArray(7, conn.createArrayOf("text", inc.symptoms().toArray()));
            ps.setString(8, inc.symptomType());
            ps.setArray(9, conn.createArrayOf("text", inc.errorSignatures().toArray()));
            ps.setString(10, inc.errorPatternCategory());
            ps.setString(11, inc.rootCause());
            ps.setString(12, inc.fix());
            ps.setString(13, inc.sourceUrl());
            ps.setString(14, toVectorLiteral(embedding));
            return ps;
        });
    }

    public long count() {
        Long n = jdbc.queryForObject("SELECT count(*) FROM incident", Long.class);
        return n == null ? 0 : n;
    }

    /** Vector-similarity recall: the top {@code poolSize} nearest by cosine distance. */
    public List<Candidate> recallByVector(float[] queryEmbedding, int poolSize) {
        String vec = toVectorLiteral(queryEmbedding);
        String sql = """
            SELECT id, source, title, occurred_on, affected_services, service_types, symptoms,
                   symptom_type, error_signatures, error_pattern_category, root_cause, fix, source_url,
                   1 - (embedding <=> CAST(? AS vector)) AS vec_sim
            FROM incident
            ORDER BY embedding <=> CAST(? AS vector)
            LIMIT ?
            """;
        return jdbc.query(sql, CANDIDATE_MAPPER, vec, vec, poolSize);
    }

    private static final RowMapper<Candidate> CANDIDATE_MAPPER = (rs, i) -> new Candidate(map(rs), rs.getDouble("vec_sim"));

    private static Incident map(ResultSet rs) throws SQLException {
        return new Incident(
                rs.getString("id"),
                rs.getString("source"),
                rs.getString("title"),
                rs.getObject("occurred_on", LocalDate.class),
                textArray(rs.getArray("affected_services")),
                textArray(rs.getArray("service_types")),
                textArray(rs.getArray("symptoms")),
                rs.getString("symptom_type"),
                textArray(rs.getArray("error_signatures")),
                rs.getString("error_pattern_category"),
                rs.getString("root_cause"),
                rs.getString("fix"),
                rs.getString("source_url"));
    }

    private static List<String> textArray(Array array) throws SQLException {
        if (array == null) return List.of();
        Object[] raw = (Object[]) array.getArray();
        return java.util.Arrays.stream(raw).map(String::valueOf).toList();
    }

    static String toVectorLiteral(float[] v) {
        StringBuilder sb = new StringBuilder(v.length * 8 + 2);
        sb.append('[');
        for (int i = 0; i < v.length; i++) {
            if (i > 0) sb.append(',');
            sb.append(v[i]);
        }
        return sb.append(']').toString();
    }
}
