package it.signflow.reports;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;

@Repository
public class ClinicalDocumentRepository {
    private final JdbcClient jdbcClient;

    public ClinicalDocumentRepository(JdbcClient jdbcClient) {
        this.jdbcClient = jdbcClient;
    }

    public boolean reportExists(UUID reportId) {
        return jdbcClient.sql("select count(*) from reports where id=:id")
                .param("id", reportId).query(Integer.class).single() > 0;
    }

    public List<ClinicalDocumentResponse> list(UUID reportId, boolean includeDeleted) {
        String condition = includeDeleted ? "" : " and status='ACTIVE'";
        return jdbcClient.sql("select * from clinical_documents where report_id=:reportId" + condition
                        + " order by version desc")
                .param("reportId", reportId).query(this::map).list();
    }

    public Optional<ClinicalDocumentResponse> find(UUID reportId, UUID documentId, boolean includeDeleted) {
        String condition = includeDeleted ? "" : " and status='ACTIVE'";
        return jdbcClient.sql("select * from clinical_documents where report_id=:reportId and id=:id" + condition)
                .param("reportId", reportId).param("id", documentId).query(this::map).optional();
    }

    public int nextVersion(UUID reportId) {
        return jdbcClient.sql("select coalesce(max(version), 0) + 1 from clinical_documents where report_id=:reportId")
                .param("reportId", reportId).query(Integer.class).single();
    }

    public ClinicalDocumentResponse insert(UUID id, UUID reportId, String sha256, long size, int version,
                                            String originalFilename, String objectKey, String uploadedBy) {
        jdbcClient.sql("""
                insert into clinical_documents
                    (id, report_id, document_type_code, cda_injection_status, sha256, mime_type,
                     size_bytes, version, original_filename, object_key, uploaded_by, status)
                select :id, r.id, r.document_type,
                       case when s.create_cda and not s.passthrough
                                  and coalesce(c.cda_injection_enabled, false)
                            then 'PENDING_CDA' else 'NOT_REQUESTED' end,
                       :sha256, 'application/pdf', :size, :version, :filename,
                       :objectKey, :uploadedBy, 'ACTIVE'
                from reports r join source_systems s on s.id=r.source_system_id
                left join source_system_fse_document_types c
                  on c.source_system_id=s.id and c.document_type_code=r.document_type
                where r.id=:reportId
                """).param("id", id).param("reportId", reportId).param("sha256", sha256)
                .param("size", size).param("version", version).param("filename", originalFilename)
                .param("objectKey", objectKey).param("uploadedBy", uploadedBy).update();
        return find(reportId, id, true).orElseThrow();
    }

    public void logicalDelete(UUID reportId, UUID documentId, String deletedBy) {
        jdbcClient.sql("""
                update clinical_documents set status='DELETED', deleted_at=now(), deleted_by=:deletedBy
                where report_id=:reportId and id=:id and status='ACTIVE'
                """).param("deletedBy", deletedBy).param("reportId", reportId).param("id", documentId).update();
    }

    private ClinicalDocumentResponse map(ResultSet rs, int rowNum) throws SQLException {
        return new ClinicalDocumentResponse(
                rs.getObject("id", UUID.class), rs.getObject("report_id", UUID.class),
                rs.getString("document_type_code"), rs.getString("cda_injection_status"), rs.getString("sha256"),
                rs.getString("mime_type"), rs.getLong("size_bytes"), rs.getInt("version"),
                rs.getString("original_filename"), rs.getString("object_key"), rs.getString("uploaded_by"),
                rs.getObject("uploaded_at", OffsetDateTime.class), ClinicalDocumentStatus.valueOf(rs.getString("status")),
                rs.getObject("deleted_at", OffsetDateTime.class), rs.getString("deleted_by"));
    }
}
