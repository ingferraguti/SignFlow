package it.signflow.reports;

import it.signflow.identity.PageResponse;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;

@Repository
public class ReportRepository {
    private static final String JOINS = """
            from reports r
            join practices pr on pr.id = r.practice_id
            join patient_metadata pm on pm.id = r.patient_metadata_id
            left join application_users u on u.id = r.assigned_signer_id
            join source_systems ss on ss.id = r.source_system_id
            """;
    private static final String SUMMARY_SELECT = """
            select r.*, pr.practice_identifier, pm.patient_identifier, pm.first_name patient_first_name,
                   pm.last_name patient_last_name, u.username signer_username,
                   u.signer_fiscal_code, ss.code source_system_code
            """;

    private final JdbcClient jdbcClient;

    public ReportRepository(JdbcClient jdbcClient) {
        this.jdbcClient = jdbcClient;
    }

    public PageResponse<ReportSummaryResponse> search(ReportSearchCriteria criteria) {
        Filter filter = filter(criteria);
        long total = jdbcClient.sql("select count(*) " + JOINS + filter.sql())
                .params(filter.params()).query(Long.class).single();
        String orderBy = orderBy(criteria.sortBy(), criteria.direction());
        Map<String, Object> pageParams = new HashMap<>(filter.params());
        pageParams.put("limit", criteria.size());
        pageParams.put("offset", criteria.page() * criteria.size());
        List<ReportSummaryResponse> items = jdbcClient.sql(SUMMARY_SELECT + JOINS + filter.sql()
                        + " order by " + orderBy + " limit :limit offset :offset")
                .params(pageParams).query(this::mapSummary).list();
        return new PageResponse<>(items, criteria.page(), criteria.size(), total);
    }

    public Optional<ReportDetailResponse> findById(UUID id) {
        return jdbcClient.sql("""
                select r.*, pr.practice_identifier, pr.external_reference practice_external_reference,
                       pr.description practice_description, pm.patient_identifier,
                       pm.first_name patient_first_name, pm.last_name patient_last_name,
                       pm.fiscal_code patient_fiscal_code, pm.birth_date patient_birth_date,
                       u.username signer_username, u.signer_fiscal_code, ss.code source_system_code
                """ + JOINS + " where r.id = :id")
                .param("id", id).query(this::mapDetail).optional();
    }

    private Filter filter(ReportSearchCriteria criteria) {
        Map<String, Object> params = new HashMap<>();
        StringBuilder sql = new StringBuilder(" where 1=1");
        if (criteria.lookupType() != ReportLookupType.NONE) {
            String column = switch (criteria.lookupType()) {
                case INTERNAL -> "r.internal_identifier";
                case EXTERNAL -> "r.external_identifier";
                case FSE -> "r.fse_identifier";
                case NONE -> throw new IllegalStateException();
            };
            sql.append(" and ").append(column).append(" = :exactIdentifier");
            params.put("exactIdentifier", criteria.exactIdentifier());
            return new Filter(sql.toString(), params);
        }
        addText(sql, params, "patient", criteria.patient(), """
                 and (lower(pm.patient_identifier) like :patient
                   or lower(pm.first_name) like :patient
                   or lower(pm.last_name) like :patient
                   or lower(pm.first_name || ' ' || pm.last_name) like :patient
                   or lower(pm.last_name || ' ' || pm.first_name) like :patient
                   or lower(coalesce(pm.fiscal_code, '')) like :patient)
                """);
        addText(sql, params, "signer", criteria.signer(), """
                 and (lower(coalesce(u.username, '')) like :signer
                   or lower(coalesce(u.first_name, '')) like :signer
                   or lower(coalesce(u.last_name, '')) like :signer
                   or lower(coalesce(u.first_name, '') || ' ' || coalesce(u.last_name, '')) like :signer)
                """);
        if (criteria.signerFiscalCode() != null) {
            sql.append(" and upper(coalesce(u.signer_fiscal_code, '')) = :signerFiscalCode");
            params.put("signerFiscalCode", criteria.signerFiscalCode().trim().toUpperCase());
        }
        if (criteria.state() != null) {
            sql.append(" and r.state = :state");
            params.put("state", criteria.state().name());
        }
        if (criteria.sourceSystemId() != null) {
            sql.append(" and r.source_system_id = :sourceSystemId");
            params.put("sourceSystemId", criteria.sourceSystemId());
        }
        addText(sql, params, "department", criteria.department(), " and lower(r.department) like :department");
        addDate(sql, params, "producedFrom", criteria.producedFrom(), "r.produced_at >= :producedFrom");
        addDate(sql, params, "producedTo", criteria.producedToExclusive(), "r.produced_at < :producedTo");
        addDate(sql, params, "modifiedFrom", criteria.modifiedFrom(), "r.modified_at >= :modifiedFrom");
        addDate(sql, params, "modifiedTo", criteria.modifiedToExclusive(), "r.modified_at < :modifiedTo");
        addDate(sql, params, "signedFrom", criteria.signedFrom(), "r.signed_at >= :signedFrom");
        addDate(sql, params, "signedTo", criteria.signedToExclusive(), "r.signed_at < :signedTo");
        return new Filter(sql.toString(), params);
    }

    private void addText(StringBuilder sql, Map<String, Object> params, String name, String value, String condition) {
        if (value != null) {
            sql.append(condition);
            params.put(name, "%" + value.trim().toLowerCase() + "%");
        }
    }

    private void addDate(StringBuilder sql, Map<String, Object> params, String name, OffsetDateTime value, String condition) {
        if (value != null) {
            sql.append(" and ").append(condition);
            params.put(name, value);
        }
    }

    private String orderBy(String sortBy, String direction) {
        String column = switch (sortBy) {
            case "internalIdentifier" -> "r.internal_identifier";
            case "modifiedAt" -> "r.modified_at";
            case "signedAt" -> "r.signed_at";
            case "state" -> "r.state";
            default -> "r.produced_at";
        };
        return column + ("asc".equals(direction) ? " asc" : " desc") + ", r.id";
    }

    private ReportSummaryResponse mapSummary(ResultSet rs, int rowNum) throws SQLException {
        return new ReportSummaryResponse(
                rs.getObject("id", UUID.class), rs.getString("internal_identifier"),
                rs.getString("external_identifier"), rs.getString("fse_identifier"),
                rs.getString("practice_identifier"), rs.getString("patient_identifier"),
                rs.getString("patient_last_name") + " " + rs.getString("patient_first_name"),
                rs.getObject("assigned_signer_id", UUID.class), rs.getString("signer_username"),
                rs.getString("signer_fiscal_code"), rs.getObject("source_system_id", UUID.class),
                rs.getString("source_system_code"), rs.getString("document_type"), rs.getString("department"),
                rs.getObject("produced_at", OffsetDateTime.class), rs.getObject("modified_at", OffsetDateTime.class),
                rs.getObject("signed_at", OffsetDateTime.class), ReportState.valueOf(rs.getString("state")));
    }

    private ReportDetailResponse mapDetail(ResultSet rs, int rowNum) throws SQLException {
        return new ReportDetailResponse(
                rs.getObject("id", UUID.class), rs.getString("internal_identifier"),
                rs.getString("external_identifier"), rs.getString("fse_identifier"),
                new PracticeResponse(rs.getObject("practice_id", UUID.class), rs.getString("practice_identifier"),
                        rs.getString("practice_external_reference"), rs.getString("practice_description")),
                new PatientMetadataResponse(rs.getObject("patient_metadata_id", UUID.class), rs.getString("patient_identifier"),
                        rs.getString("patient_first_name"), rs.getString("patient_last_name"),
                        rs.getString("patient_fiscal_code"), rs.getObject("patient_birth_date", LocalDate.class)),
                rs.getObject("assigned_signer_id", UUID.class), rs.getString("signer_username"),
                rs.getString("signer_fiscal_code"), rs.getObject("source_system_id", UUID.class),
                rs.getString("source_system_code"), rs.getString("document_type"), rs.getString("department"),
                rs.getObject("produced_at", OffsetDateTime.class), rs.getObject("modified_at", OffsetDateTime.class),
                rs.getObject("signed_at", OffsetDateTime.class), rs.getBoolean("pdf_a3_conversion"),
                rs.getBoolean("visible_signature"), rs.getBoolean("multiple_signature"),
                rs.getBoolean("send_unsigned"), rs.getBoolean("create_cda"), rs.getBoolean("passthrough"),
                rs.getLong("workflow_version"), rs.getObject("first_previewed_at", OffsetDateTime.class),
                ReportState.valueOf(rs.getString("state")));
    }

    private record Filter(String sql, Map<String, Object> params) {
    }
}
