package it.signflow.fse;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.Optional;
import java.util.UUID;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;

@Repository
class FsePreparationRepository {
    private final JdbcClient jdbcClient;

    FsePreparationRepository(JdbcClient jdbcClient) { this.jdbcClient = jdbcClient; }

    Optional<Configuration> configuration(UUID reportId) {
        return jdbcClient.sql("""
                select r.id report_id, r.document_type, s.active source_active, s.create_cda, s.passthrough,
                       coalesce(c.cda_injection_enabled, false) cda_injection_enabled
                from reports r join source_systems s on s.id=r.source_system_id
                left join source_system_fse_document_types c
                  on c.source_system_id=s.id and c.document_type_code=r.document_type
                where r.id=:reportId
                """).param("reportId", reportId).query(this::map).optional();
    }

    private Configuration map(ResultSet rs, int rowNum) throws SQLException {
        return new Configuration(rs.getObject("report_id", UUID.class), rs.getString("document_type"),
                rs.getBoolean("source_active"), rs.getBoolean("create_cda"), rs.getBoolean("passthrough"),
                rs.getBoolean("cda_injection_enabled"));
    }

    record Configuration(UUID reportId, String documentTypeCode, boolean sourceActive,
                         boolean createCda, boolean passthrough, boolean typeEnabled) {}
}
