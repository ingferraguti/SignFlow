package it.signflow.technicalconfig;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;

@Repository
public class TechnicalConfigurationRepository {
    private final JdbcClient jdbcClient;

    public TechnicalConfigurationRepository(JdbcClient jdbcClient) {
        this.jdbcClient = jdbcClient;
    }

    public List<SourceSystemResponse> sourceSystems() {
        return jdbcClient.sql("""
                select s.*, c.code company_code from source_systems s
                join companies c on c.id = s.company_id order by s.code
                """).query(this::mapSourceSystem).list();
    }

    public Optional<SourceSystemResponse> sourceSystem(UUID id) {
        return jdbcClient.sql("""
                select s.*, c.code company_code from source_systems s
                join companies c on c.id = s.company_id where s.id = :id
                """).param("id", id).query(this::mapSourceSystem).optional();
    }

    public UUID createSourceSystem(SourceSystemRequest request) {
        UUID id = UUID.randomUUID();
        jdbcClient.sql("""
                insert into source_systems (id, code, company_id, description, active, cda_type,
                    pdf_a3_conversion, visible_signature, multiple_signature, send_unsigned, create_cda, passthrough)
                values (:id, :code, :companyId, :description, :active, :cdaType,
                    :pdfA3Conversion, :visibleSignature, :multipleSignature, :sendUnsigned, :createCda, :passthrough)
                """).param("id", id).param("code", request.code().toUpperCase())
                .param("companyId", request.companyId()).param("description", request.description().trim())
                .param("active", request.active()).param("cdaType", request.cdaType().trim().toUpperCase())
                .param("pdfA3Conversion", request.pdfA3Conversion()).param("visibleSignature", request.visibleSignature())
                .param("multipleSignature", request.multipleSignature()).param("sendUnsigned", request.sendUnsigned())
                .param("createCda", request.createCda()).param("passthrough", request.passthrough()).update();
        return id;
    }

    public int updateSourceSystem(UUID id, SourceSystemRequest request) {
        return jdbcClient.sql("""
                update source_systems set code=:code, company_id=:companyId, description=:description,
                    active=:active, cda_type=:cdaType, pdf_a3_conversion=:pdfA3Conversion,
                    visible_signature=:visibleSignature, multiple_signature=:multipleSignature,
                    send_unsigned=:sendUnsigned, create_cda=:createCda, passthrough=:passthrough, updated_at=now()
                where id=:id
                """).param("id", id).param("code", request.code().toUpperCase())
                .param("companyId", request.companyId()).param("description", request.description().trim())
                .param("active", request.active()).param("cdaType", request.cdaType().trim().toUpperCase())
                .param("pdfA3Conversion", request.pdfA3Conversion()).param("visibleSignature", request.visibleSignature())
                .param("multipleSignature", request.multipleSignature()).param("sendUnsigned", request.sendUnsigned())
                .param("createCda", request.createCda()).param("passthrough", request.passthrough()).update();
    }

    public List<SignatureProviderResponse> signatureProviders() {
        return jdbcClient.sql("select * from signature_providers order by code").query(this::mapProvider).list();
    }

    public Optional<SignatureProviderResponse> signatureProvider(UUID id) {
        return jdbcClient.sql("select * from signature_providers where id=:id").param("id", id).query(this::mapProvider).optional();
    }

    public UUID createSignatureProvider(SignatureProviderRequest request) {
        UUID id = UUID.randomUUID();
        jdbcClient.sql("""
                insert into signature_providers (id, code, name, adapter_type, base_url, authentication_mode,
                    credential_reference, supports_visible_signature, supports_multiple_signature, active)
                values (:id, :code, :name, :adapterType, :baseUrl, :authenticationMode,
                    :credentialReference, :supportsVisibleSignature, :supportsMultipleSignature, :active)
                """).param("id", id).param("code", request.code().toUpperCase()).param("name", request.name().trim())
                .param("adapterType", request.adapterType().trim().toUpperCase()).param("baseUrl", value(request.baseUrl()))
                .param("authenticationMode", request.authenticationMode().name())
                .param("credentialReference", value(request.credentialReference()))
                .param("supportsVisibleSignature", request.supportsVisibleSignature())
                .param("supportsMultipleSignature", request.supportsMultipleSignature()).param("active", request.active()).update();
        return id;
    }

    public int updateSignatureProvider(UUID id, SignatureProviderRequest request) {
        return jdbcClient.sql("""
                update signature_providers set code=:code, name=:name, adapter_type=:adapterType,
                    base_url=:baseUrl, authentication_mode=:authenticationMode, credential_reference=:credentialReference,
                    supports_visible_signature=:supportsVisibleSignature,
                    supports_multiple_signature=:supportsMultipleSignature, active=:active, updated_at=now()
                where id=:id
                """).param("id", id).param("code", request.code().toUpperCase()).param("name", request.name().trim())
                .param("adapterType", request.adapterType().trim().toUpperCase()).param("baseUrl", value(request.baseUrl()))
                .param("authenticationMode", request.authenticationMode().name())
                .param("credentialReference", value(request.credentialReference()))
                .param("supportsVisibleSignature", request.supportsVisibleSignature())
                .param("supportsMultipleSignature", request.supportsMultipleSignature()).param("active", request.active()).update();
    }

    public List<SignatureAccountResponse> signatureAccounts() {
        return jdbcClient.sql("""
                select a.*, u.username application_username, p.code provider_code from signature_accounts a
                join application_users u on u.id=a.application_user_id
                join signature_providers p on p.id=a.signature_provider_id order by a.account_alias
                """).query(this::mapAccount).list();
    }

    public Optional<SignatureAccountResponse> signatureAccount(UUID id) {
        return jdbcClient.sql("""
                select a.*, u.username application_username, p.code provider_code from signature_accounts a
                join application_users u on u.id=a.application_user_id
                join signature_providers p on p.id=a.signature_provider_id where a.id=:id
                """).param("id", id).query(this::mapAccount).optional();
    }

    public UUID createSignatureAccount(SignatureAccountRequest request) {
        UUID id = UUID.randomUUID();
        jdbcClient.sql("""
                insert into signature_accounts (id, application_user_id, signature_provider_id, account_alias,
                    provider_username, certificate_alias, active)
                values (:id, :applicationUserId, :signatureProviderId, :accountAlias,
                    :providerUsername, :certificateAlias, :active)
                """).param("id", id).param("applicationUserId", request.applicationUserId())
                .param("signatureProviderId", request.signatureProviderId()).param("accountAlias", request.accountAlias().trim())
                .param("providerUsername", value(request.providerUsername())).param("certificateAlias", value(request.certificateAlias()))
                .param("active", request.active()).update();
        return id;
    }

    public int updateSignatureAccount(UUID id, SignatureAccountRequest request) {
        return jdbcClient.sql("""
                update signature_accounts set application_user_id=:applicationUserId,
                    signature_provider_id=:signatureProviderId, account_alias=:accountAlias,
                    provider_username=:providerUsername, certificate_alias=:certificateAlias,
                    active=:active, updated_at=now() where id=:id
                """).param("id", id).param("applicationUserId", request.applicationUserId())
                .param("signatureProviderId", request.signatureProviderId()).param("accountAlias", request.accountAlias().trim())
                .param("providerUsername", value(request.providerUsername())).param("certificateAlias", value(request.certificateAlias()))
                .param("active", request.active()).update();
    }

    public List<FseFacilityMappingResponse> fseFacilityMappings() {
        return jdbcClient.sql("""
                select f.*, c.code company_code, s.code source_system_code from fse_facility_mappings f
                join companies c on c.id=f.company_id join source_systems s on s.id=f.source_system_id
                order by f.facility_code, f.operating_unit
                """).query(this::mapFseMapping).list();
    }

    public Optional<FseFacilityMappingResponse> fseFacilityMapping(UUID id) {
        return jdbcClient.sql("""
                select f.*, c.code company_code, s.code source_system_code from fse_facility_mappings f
                join companies c on c.id=f.company_id join source_systems s on s.id=f.source_system_id where f.id=:id
                """).param("id", id).query(this::mapFseMapping).optional();
    }

    public UUID createFseFacilityMapping(FseFacilityMappingRequest request) {
        UUID id = UUID.randomUUID();
        jdbcClient.sql("""
                insert into fse_facility_mappings (id, facility_code, facility_name, company_id,
                    operating_unit, department, source_system_id, active)
                values (:id, :facilityCode, :facilityName, :companyId,
                    :operatingUnit, :department, :sourceSystemId, :active)
                """).param("id", id).param("facilityCode", request.facilityCode().toUpperCase())
                .param("facilityName", request.facilityName().trim()).param("companyId", request.companyId())
                .param("operatingUnit", request.operatingUnit().trim()).param("department", request.department().trim())
                .param("sourceSystemId", request.sourceSystemId()).param("active", request.active()).update();
        return id;
    }

    public int updateFseFacilityMapping(UUID id, FseFacilityMappingRequest request) {
        return jdbcClient.sql("""
                update fse_facility_mappings set facility_code=:facilityCode, facility_name=:facilityName,
                    company_id=:companyId, operating_unit=:operatingUnit, department=:department,
                    source_system_id=:sourceSystemId, active=:active, updated_at=now() where id=:id
                """).param("id", id).param("facilityCode", request.facilityCode().toUpperCase())
                .param("facilityName", request.facilityName().trim()).param("companyId", request.companyId())
                .param("operatingUnit", request.operatingUnit().trim()).param("department", request.department().trim())
                .param("sourceSystemId", request.sourceSystemId()).param("active", request.active()).update();
    }

    public int delete(String table, UUID id) {
        return jdbcClient.sql("delete from " + table + " where id=:id").param("id", id).update();
    }

    private SourceSystemResponse mapSourceSystem(ResultSet rs, int rowNum) throws SQLException {
        return new SourceSystemResponse(rs.getObject("id", UUID.class), rs.getString("code"),
                rs.getObject("company_id", UUID.class), rs.getString("company_code"), rs.getString("description"),
                rs.getBoolean("active"), rs.getString("cda_type"), rs.getBoolean("pdf_a3_conversion"),
                rs.getBoolean("visible_signature"), rs.getBoolean("multiple_signature"), rs.getBoolean("send_unsigned"),
                rs.getBoolean("create_cda"), rs.getBoolean("passthrough"));
    }

    private SignatureProviderResponse mapProvider(ResultSet rs, int rowNum) throws SQLException {
        return new SignatureProviderResponse(rs.getObject("id", UUID.class), rs.getString("code"), rs.getString("name"),
                rs.getString("adapter_type"), rs.getString("base_url"),
                SignatureAuthenticationMode.valueOf(rs.getString("authentication_mode")), rs.getString("credential_reference"),
                rs.getBoolean("supports_visible_signature"), rs.getBoolean("supports_multiple_signature"), rs.getBoolean("active"));
    }

    private SignatureAccountResponse mapAccount(ResultSet rs, int rowNum) throws SQLException {
        return new SignatureAccountResponse(rs.getObject("id", UUID.class), rs.getObject("application_user_id", UUID.class),
                rs.getString("application_username"), rs.getObject("signature_provider_id", UUID.class),
                rs.getString("provider_code"), rs.getString("account_alias"), rs.getString("provider_username"),
                rs.getString("certificate_alias"), rs.getBoolean("active"));
    }

    private FseFacilityMappingResponse mapFseMapping(ResultSet rs, int rowNum) throws SQLException {
        return new FseFacilityMappingResponse(rs.getObject("id", UUID.class), rs.getString("facility_code"),
                rs.getString("facility_name"), rs.getObject("company_id", UUID.class), rs.getString("company_code"),
                rs.getString("operating_unit"), rs.getString("department"), rs.getObject("source_system_id", UUID.class),
                rs.getString("source_system_code"), rs.getBoolean("active"));
    }

    private String value(String text) {
        return text == null ? "" : text.trim();
    }
}
