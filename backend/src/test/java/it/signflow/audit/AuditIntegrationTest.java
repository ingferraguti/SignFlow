package it.signflow.audit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.hamcrest.Matchers.equalTo;
import static org.hamcrest.Matchers.greaterThanOrEqualTo;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.dao.DataAccessException;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.RequestPostProcessor;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

@SpringBootTest
@AutoConfigureMockMvc
@Testcontainers
@ActiveProfiles("test")
class AuditIntegrationTest {
    @Container static final PostgreSQLContainer<?> POSTGRES = new PostgreSQLContainer<>("postgres:16-alpine");

    @DynamicPropertySource
    static void properties(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", POSTGRES::getJdbcUrl);
        registry.add("spring.datasource.username", POSTGRES::getUsername);
        registry.add("spring.datasource.password", POSTGRES::getPassword);
    }

    @Autowired MockMvc mockMvc;
    @Autowired JdbcClient jdbc;
    @Autowired AuditService service;

    @Test
    void listsFiltersAndPaginatesFictionalAuditEvents() throws Exception {
        mockMvc.perform(get("/api/admin/audit/events").param("eventType", "FSE_OPERATION_RESERVED")
                        .param("page", "0").param("size", "1").with(admin()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.total", equalTo(1)))
                .andExpect(jsonPath("$.items[0].actorId", equalTo("demo.fse-adapter")))
                .andExpect(jsonPath("$.items[0].metadata.environment", equalTo("FICTIONAL")));
    }

    @Test
    void recordsLoginAndSensitiveSearchWithCorrelationIds() throws Exception {
        mockMvc.perform(post("/api/session-audit/login").header("X-Correlation-ID", "login-test-12").with(admin()))
                .andExpect(status().isOk()).andExpect(header().string("X-Correlation-ID", "login-test-12"));
        mockMvc.perform(get("/api/admin/reports").param("internalIdentifier", "RPT-NOT-REAL").with(admin()))
                .andExpect(status().isOk());
        mockMvc.perform(get("/api/admin/audit/events").param("eventType", "LOGIN").param("correlationId", "login-test-12").with(admin()))
                .andExpect(status().isOk()).andExpect(jsonPath("$.total", equalTo(1)));
        mockMvc.perform(get("/api/admin/audit/events").param("eventType", "ADMIN_SENSITIVE_SEARCH").with(admin()))
                .andExpect(status().isOk()).andExpect(jsonPath("$.total", greaterThanOrEqualTo(1)));
    }

    @Test
    void preventsUpdateAndArbitraryDelete() {
        UUID id = jdbc.sql("select id from audit_events limit 1").query(UUID.class).single();
        assertThatThrownBy(() -> jdbc.sql("update audit_events set outcome='FAILURE' where id=:id").param("id", id).update())
                .isInstanceOf(DataAccessException.class).rootCause().hasMessageContaining("append-only");
        assertThatThrownBy(() -> jdbc.sql("delete from audit_events where id=:id").param("id", id).update())
                .isInstanceOf(DataAccessException.class).rootCause().hasMessageContaining("append-only");
    }

    @Test
    void rejectsSensitiveOrNonScalarMetadata() {
        AuditRecordCommand base = new AuditRecordCommand("TEST_EVENT", "TECHNICAL", "test.adapter", "test-correlation",
                "REPORT", "fictional-report", "SUCCESS", Map.of("accessToken", "must-not-be-recorded"), null);
        assertThatThrownBy(() -> service.record(base)).isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Sensitive metadata key");
        AuditRecordCommand nested = new AuditRecordCommand("TEST_EVENT", "TECHNICAL", "test.adapter", "test-correlation-2",
                "REPORT", "fictional-report", "SUCCESS", Map.of("nested", List.of("not", "allowed")), null);
        assertThatThrownBy(() -> service.record(nested)).isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("scalar");
    }

    @Test
    void auditsDocumentUploadViaDatabaseTriggerAndExposesReportTimeline() throws Exception {
        UUID documentId = UUID.randomUUID();
        jdbc.sql("""
                insert into clinical_documents (id, report_id, sha256, mime_type, size_bytes, version,
                    original_filename, object_key, uploaded_by, status)
                values (:id, 'eeeeeeee-eeee-eeee-eeee-eeeeeeeeeee1',
                    'aaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaa',
                    'application/pdf', 42, 99, 'fictional-audit.pdf', :objectKey, 'demo.admin', 'ACTIVE')
                """).param("id", documentId).param("objectKey", "audit-test/" + documentId).update();
        mockMvc.perform(get("/api/admin/audit/reports/eeeeeeee-eeee-eeee-eeee-eeeeeeeeeee1/timeline").with(admin()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[?(@.eventType == 'DOCUMENT_UPLOADED')].entityId").isNotEmpty());
    }

    @Test
    void restrictsExportToAdministratorsAndReturnsCsvWithoutMetadataPayloads() throws Exception {
        mockMvc.perform(get("/api/admin/audit/events/export").with(signer())).andExpect(status().isForbidden());
        mockMvc.perform(get("/api/admin/audit/events/export").with(admin()))
                .andExpect(status().isOk()).andExpect(content().contentType("text/csv"))
                .andExpect(header().string("Content-Disposition", org.hamcrest.Matchers.containsString("signflow-audit.csv")))
                .andExpect(content().string(org.hamcrest.Matchers.containsString("correlation_id")));
    }

    @Test
    void validatesAndUpdatesRetentionPolicy() throws Exception {
        mockMvc.perform(put("/api/admin/audit/retention").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"retentionDays\":14}").with(admin()))
                .andExpect(status().isBadRequest());
        mockMvc.perform(put("/api/admin/audit/retention").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"retentionDays\":730}").with(admin()))
                .andExpect(status().isOk()).andExpect(jsonPath("$.retentionDays", equalTo(730)));
        assertThat(jdbc.sql("select count(*) from audit_events where event_type='AUDIT_RETENTION_CHANGED'").query(Long.class).single()).isPositive();
    }

    private RequestPostProcessor admin() {
        return jwt().jwt(token -> token.subject("demo-admin").claim("preferred_username", "demo.admin")
                .claim("realm_access", Map.of("roles", List.of("ADMINISTRATOR"))))
                .authorities(new SimpleGrantedAuthority("ROLE_ADMINISTRATOR"));
    }

    private RequestPostProcessor signer() {
        return jwt().jwt(token -> token.subject("demo-signer").claim("preferred_username", "demo.signer")
                .claim("realm_access", Map.of("roles", List.of("SIGNER"))))
                .authorities(new SimpleGrantedAuthority("ROLE_SIGNER"));
    }
}
