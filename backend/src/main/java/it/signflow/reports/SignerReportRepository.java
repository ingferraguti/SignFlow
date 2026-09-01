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
public class SignerReportRepository {
    private static final String JOINS = """
            from reports r
            join practices pr on pr.id=r.practice_id
            join patient_metadata pm on pm.id=r.patient_metadata_id
            left join application_users assigned on assigned.id=r.assigned_signer_id
            left join lateral (select normalized_value from natural_person_identifiers pi
                where pi.natural_person_id=assigned.natural_person_id and pi.active=true
                order by pi.verified desc, pi.created_at limit 1) signer_identifier on true
            join source_systems ss on ss.id=r.source_system_id
            join fse_document_types dt on dt.code=r.document_type
            """;
    private static final String VISIBLE = """
            exists (
                select 1 from application_users me
                join natural_persons np on np.id=me.natural_person_id and np.active=true
                join application_users owner on owner.id=r.assigned_signer_id
                where me.username=:username and me.active=true
                  and owner.natural_person_id=me.natural_person_id
                  and exists (select 1 from application_user_roles aur join roles role on role.id=aur.role_id
                      where aur.user_id=me.id and role.code='SIGNER')
            )
            """;
    private static final String SUMMARY = """
            select r.*, pr.practice_identifier, pm.patient_identifier, pm.first_name patient_first_name,
                   pm.last_name patient_last_name, assigned.username signer_username,
                   signer_identifier.normalized_value signer_fiscal_code, ss.code source_system_code,
                   (dt.active and exists (select 1 from clinical_documents cd where cd.report_id=r.id and cd.status='ACTIVE')
                    and ((dt.approval_required and r.state='APPROVED')
                      or (not dt.approval_required and dt.preview_required and r.state='PREVIEWED')
                      or (not dt.approval_required and not dt.preview_required and r.state in ('RECEIVED','PARSED','PREVIEWED')))) signature_eligible
            """;

    private final JdbcClient jdbcClient;

    public SignerReportRepository(JdbcClient jdbcClient) {
        this.jdbcClient = jdbcClient;
    }

    public boolean activeSignerExists(String username) {
        return jdbcClient.sql("""
                select count(*) from application_users u join natural_persons np on np.id=u.natural_person_id
                where u.username=:username and u.active=true and np.active=true
                  and exists (select 1 from application_user_roles aur join roles r on r.id=aur.role_id
                      where aur.user_id=u.id and r.code='SIGNER')
                """)
                .param("username", username).query(Integer.class).single() > 0;
    }

    public PageResponse<ReportSummaryResponse> search(String username, SignerCriteria criteria) {
        Filter filter = filter(username, criteria);
        long total = jdbcClient.sql("select count(*) " + JOINS + filter.sql())
                .params(filter.params()).query(Long.class).single();
        Map<String, Object> params = new HashMap<>(filter.params());
        params.put("limit", criteria.size());
        params.put("offset", criteria.page() * criteria.size());
        List<ReportSummaryResponse> items = jdbcClient.sql(SUMMARY + JOINS + filter.sql()
                        + " order by r.produced_at desc, r.id limit :limit offset :offset")
                .params(params).query(this::mapSummary).list();
        return new PageResponse<>(items, criteria.page(), criteria.size(), total);
    }

    public Optional<ReportDetailResponse> findVisible(String username, UUID reportId) {
        return jdbcClient.sql("""
                select r.*, pr.practice_identifier, pr.external_reference practice_external_reference,
                       pr.description practice_description, pm.patient_identifier,
                       pm.first_name patient_first_name, pm.last_name patient_last_name,
                       pm.fiscal_code patient_fiscal_code, pm.birth_date patient_birth_date,
                       assigned.username signer_username, signer_identifier.normalized_value signer_fiscal_code,
                       ss.code source_system_code,
                       (dt.active and exists (select 1 from clinical_documents cd where cd.report_id=r.id and cd.status='ACTIVE')
                        and ((dt.approval_required and r.state='APPROVED')
                          or (not dt.approval_required and dt.preview_required and r.state='PREVIEWED')
                          or (not dt.approval_required and not dt.preview_required and r.state in ('RECEIVED','PARSED','PREVIEWED')))) signature_eligible
                """ + JOINS + " where r.id=:reportId and " + VISIBLE)
                .param("reportId", reportId).param("username", username).query(this::mapDetail).optional();
    }

    public SignerHomeResponse home(String username) {
        Map<String, Long> stats = jdbcClient.sql("""
                select count(*) total,
                       count(*) filter (where r.state='READY_TO_SIGN') ready,
                       count(*) filter (where r.state='REVIEW_PENDING') review,
                       count(*) filter (where r.state in ('INCOMPLETE','MISSING_SIGNER')) incomplete,
                       count(*) filter (where r.state in ('SIGNED','FSE_SENT','FSE_ACCEPTED','FSE_REJECTED',
                           'CONSERVATION_SENT','CONSERVATION_ACCEPTED','CONSERVATION_REJECTED')) signed
                """ + JOINS + " where " + VISIBLE)
                .param("username", username).query((rs, row) -> Map.of(
                        "total", rs.getLong("total"), "ready", rs.getLong("ready"),
                        "review", rs.getLong("review"), "incomplete", rs.getLong("incomplete"),
                        "signed", rs.getLong("signed"))).single();
        List<ReportSummaryResponse> recent = jdbcClient.sql(SUMMARY + JOINS + " where " + VISIBLE
                        + " order by r.produced_at desc, r.id limit 5")
                .param("username", username).query(this::mapSummary).list();
        return new SignerHomeResponse(stats.get("total"), stats.get("ready"), stats.get("review"),
                stats.get("incomplete"), stats.get("signed"), recent);
    }

    public Optional<SignerProfileResponse> profile(String username) {
        Optional<ProfileData> profile = jdbcClient.sql("""
                select u.id, u.username, u.first_name, u.last_name, u.email, u.signer_fiscal_code,
                       u.natural_person_id, pi.scheme, pi.issuing_country, pi.normalized_value,
                       p.code partition_code, p.name partition_name, c.code company_code, c.name company_name
                from application_users u join partitions p on p.id=u.partition_id
                join companies c on c.id=u.company_id
                left join lateral (select * from natural_person_identifiers x
                    where x.natural_person_id=u.natural_person_id and x.active=true
                    order by x.verified desc, x.created_at limit 1) pi on true
                where u.username=:username and u.active=true
                """).param("username", username).query((rs, row) -> new ProfileData(
                        rs.getObject("id", UUID.class), rs.getString("username"), rs.getString("first_name"),
                        rs.getString("last_name"), rs.getString("email"), rs.getString("signer_fiscal_code"),
                        rs.getObject("natural_person_id", UUID.class), rs.getString("scheme"),
                        rs.getString("issuing_country"), rs.getString("normalized_value"),
                        rs.getString("partition_code"), rs.getString("partition_name"),
                        rs.getString("company_code"), rs.getString("company_name"))).optional();
        return profile.map(data -> new SignerProfileResponse(data.id(), data.username(), data.firstName(),
                data.lastName(), data.email(), data.signerFiscalCode(), data.naturalPersonId(),
                data.identifierScheme(), data.issuingCountry(), mask(data.personalIdentifier()),
                data.partitionCode(), data.partitionName(), data.companyCode(), data.companyName(), groups(data.id()),
                authenticationAccounts(data.naturalPersonId(), data.username()),
                digitalSignatures(data.id(), data.naturalPersonId())));
    }

    public boolean setPreferredSignature(String username, UUID signatureId) {
        return jdbcClient.sql("""
                update application_users u set preferred_signature_account_id=:signatureId, updated_at=now()
                where u.username=:username and u.active=true and exists (
                    select 1 from signature_accounts sa where sa.id=:signatureId and sa.active=true
                      and sa.natural_person_id=u.natural_person_id)
                """).param("signatureId", signatureId).param("username", username).update() == 1;
    }

    private List<AuthenticationAccountResponse> authenticationAccounts(UUID personId, String currentUsername) {
        return jdbcClient.sql("""
                select u.username, ai.issuer, ai.authentication_method
                from authentication_identities ai join application_users u on u.id=ai.application_user_id
                where ai.natural_person_id=:personId and ai.active=true and u.active=true
                order by u.username, ai.issuer
                """).param("personId", personId).query((rs, row) -> new AuthenticationAccountResponse(
                        rs.getString("username"), rs.getString("issuer"),
                        rs.getString("authentication_method"), currentUsername.equals(rs.getString("username")))).list();
    }

    private List<DigitalSignatureOptionResponse> digitalSignatures(UUID userId, UUID personId) {
        return jdbcClient.sql("""
                select sa.id, sa.display_name, sp.code provider_code, sa.account_alias,
                       sa.certificate_alias, sa.signature_type, sa.qualified,
                       (sa.id=u.preferred_signature_account_id) preferred
                from signature_accounts sa join signature_providers sp on sp.id=sa.signature_provider_id
                join application_users u on u.id=:userId
                where sa.natural_person_id=:personId and sa.active=true and sp.active=true
                order by preferred desc, sa.display_name
                """).param("userId", userId).param("personId", personId)
                .query((rs, row) -> new DigitalSignatureOptionResponse(rs.getObject("id", UUID.class),
                        rs.getString("display_name"), rs.getString("provider_code"), rs.getString("account_alias"),
                        rs.getString("certificate_alias"), rs.getString("signature_type"), rs.getBoolean("qualified"),
                        rs.getBoolean("preferred"))).list();
    }

    private String mask(String value) {
        if (value == null || value.length() < 5) return value;
        return value.substring(0, 3) + "********" + value.substring(value.length() - 3);
    }

    private List<String> groups(UUID userId) {
        return jdbcClient.sql("""
                select g.code from user_groups g join application_user_groups ug on ug.group_id=g.id
                where ug.user_id=:userId and g.active=true order by g.code
                """).param("userId", userId).query(String.class).list();
    }

    private Filter filter(String username, SignerCriteria criteria) {
        StringBuilder sql = new StringBuilder(" where ").append(VISIBLE);
        Map<String, Object> params = new HashMap<>();
        params.put("username", username);
        if (criteria.query() != null) {
            sql.append("""
                     and (lower(r.internal_identifier) like :query or lower(coalesce(r.external_identifier,'')) like :query
                       or lower(pm.first_name) like :query or lower(pm.last_name) like :query
                       or lower(pm.first_name || ' ' || pm.last_name) like :query
                       or lower(pm.last_name || ' ' || pm.first_name) like :query
                       or lower(r.document_type) like :query or lower(r.department) like :query)
                    """);
            params.put("query", like(criteria.query()));
        }
        addPatient(sql, params, criteria.patient());
        addLike(sql, params, "documentType", criteria.documentType(), "r.document_type");
        addLike(sql, params, "department", criteria.department(), "r.department");
        if (criteria.state() != null) {
            sql.append(" and r.state=:state");
            params.put("state", criteria.state().name());
        }
        addDate(sql, params, "producedFrom", criteria.producedFrom(), "r.produced_at >= :producedFrom");
        addDate(sql, params, "producedTo", criteria.producedToExclusive(), "r.produced_at < :producedTo");
        addDate(sql, params, "signedFrom", criteria.signedFrom(), "r.signed_at >= :signedFrom");
        addDate(sql, params, "signedTo", criteria.signedToExclusive(), "r.signed_at < :signedTo");
        return new Filter(sql.toString(), params);
    }

    private void addPatient(StringBuilder sql, Map<String, Object> params, String value) {
        if (value == null) return;
        sql.append("""
                 and (lower(pm.patient_identifier) like :patient or lower(pm.first_name) like :patient
                   or lower(pm.last_name) like :patient or lower(pm.first_name || ' ' || pm.last_name) like :patient
                   or lower(pm.last_name || ' ' || pm.first_name) like :patient)
                """);
        params.put("patient", like(value));
    }

    private void addLike(StringBuilder sql, Map<String, Object> params, String name, String value, String column) {
        if (value != null) {
            sql.append(" and lower(").append(column).append(") like :").append(name);
            params.put(name, like(value));
        }
    }

    private void addDate(StringBuilder sql, Map<String, Object> params, String name, OffsetDateTime value, String expression) {
        if (value != null) {
            sql.append(" and ").append(expression);
            params.put(name, value);
        }
    }

    private String like(String value) {
        return "%" + value.toLowerCase() + "%";
    }

    private ReportSummaryResponse mapSummary(ResultSet rs, int rowNum) throws SQLException {
        return new ReportSummaryResponse(rs.getObject("id", UUID.class), rs.getString("internal_identifier"),
                rs.getString("external_identifier"), rs.getString("fse_identifier"), rs.getString("practice_identifier"),
                rs.getString("patient_identifier"), rs.getString("patient_last_name") + " " + rs.getString("patient_first_name"),
                rs.getObject("assigned_signer_id", UUID.class), rs.getString("signer_username"),
                rs.getString("signer_fiscal_code"), rs.getObject("source_system_id", UUID.class),
                rs.getString("source_system_code"), rs.getString("document_type"), rs.getString("department"),
                rs.getObject("produced_at", OffsetDateTime.class), rs.getObject("modified_at", OffsetDateTime.class),
                rs.getObject("signed_at", OffsetDateTime.class), ReportState.valueOf(rs.getString("state")),
                rs.getBoolean("signature_eligible"));
    }

    private ReportDetailResponse mapDetail(ResultSet rs, int rowNum) throws SQLException {
        return new ReportDetailResponse(rs.getObject("id", UUID.class), rs.getString("internal_identifier"),
                rs.getString("external_identifier"), rs.getString("fse_identifier"),
                new PracticeResponse(rs.getObject("practice_id", UUID.class), rs.getString("practice_identifier"),
                        rs.getString("practice_external_reference"), rs.getString("practice_description")),
                new PatientMetadataResponse(rs.getObject("patient_metadata_id", UUID.class), rs.getString("patient_identifier"),
                        rs.getString("patient_first_name"), rs.getString("patient_last_name"),
                        rs.getString("patient_fiscal_code"), rs.getObject("patient_birth_date", LocalDate.class)),
                rs.getObject("assigned_signer_id", UUID.class), rs.getString("signer_username"), rs.getString("signer_fiscal_code"),
                rs.getObject("source_system_id", UUID.class), rs.getString("source_system_code"), rs.getString("document_type"),
                rs.getString("department"), rs.getObject("produced_at", OffsetDateTime.class),
                rs.getObject("modified_at", OffsetDateTime.class), rs.getObject("signed_at", OffsetDateTime.class),
                rs.getBoolean("pdf_a3_conversion"), rs.getBoolean("visible_signature"), rs.getBoolean("multiple_signature"),
                rs.getBoolean("send_unsigned"), rs.getBoolean("create_cda"), rs.getBoolean("passthrough"),
                rs.getLong("workflow_version"), rs.getObject("first_previewed_at", OffsetDateTime.class),
                rs.getString("signature_kind"), rs.getString("signature_artifact_notice"),
                ReportState.valueOf(rs.getString("state")), rs.getBoolean("signature_eligible"));
    }

    public record SignerCriteria(String query, String patient, String documentType, String department,
                                 ReportState state, OffsetDateTime producedFrom, OffsetDateTime producedToExclusive,
                                 OffsetDateTime signedFrom, OffsetDateTime signedToExclusive, int page, int size) {
    }

    private record Filter(String sql, Map<String, Object> params) {
    }

    private record ProfileData(UUID id, String username, String firstName, String lastName, String email,
                               String signerFiscalCode, UUID naturalPersonId, String identifierScheme,
                               String issuingCountry, String personalIdentifier, String partitionCode, String partitionName,
                               String companyCode, String companyName) {
    }
}
