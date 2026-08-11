package it.signflow.reports;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.equalTo;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fasterxml.jackson.databind.ObjectMapper;
import java.sql.Connection;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
import javax.sql.DataSource;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.RequestPostProcessor;
import org.springframework.web.server.ResponseStatusException;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

@SpringBootTest
@AutoConfigureMockMvc
@Testcontainers
@ActiveProfiles("test")
class ReportReviewIntegrationTest {
    private static final UUID REPORT = UUID.fromString("cccccccc-cccc-cccc-cccc-ccccccccccc2");
    private static final UUID DOCUMENT = UUID.fromString("dddddddd-dddd-dddd-dddd-ddddddddddd9");
    private static final UUID APPROVER = UUID.fromString("55555555-5555-5555-5555-555555555554");
    private static final String SIGNER_ROOT = "/api/signer/reports/" + REPORT;
    private static final String APPROVER_ROOT = "/api/approver/reports/" + REPORT;

    @Container
    static final PostgreSQLContainer<?> POSTGRES = new PostgreSQLContainer<>("postgres:16-alpine");

    @DynamicPropertySource
    static void properties(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", POSTGRES::getJdbcUrl);
        registry.add("spring.datasource.username", POSTGRES::getUsername);
        registry.add("spring.datasource.password", POSTGRES::getPassword);
    }

    @Autowired MockMvc mockMvc;
    @Autowired ObjectMapper objectMapper;
    @Autowired JdbcClient jdbc;
    @Autowired DataSource dataSource;
    @Autowired ReportReviewService reviewService;

    @BeforeEach
    void reset() throws Exception {
        try (Connection connection = dataSource.getConnection()) {
            connection.setAutoCommit(false);
            connection.createStatement().execute("select set_config('signflow.workflow_transition_allowed','true',true)");
            connection.createStatement().executeUpdate("delete from report_review_decisions where report_id='" + REPORT + "'");
            connection.createStatement().executeUpdate("delete from report_workflow_events where report_id='" + REPORT + "'");
            connection.createStatement().executeUpdate("delete from clinical_documents where report_id='" + REPORT + "'");
            connection.createStatement().executeUpdate("""
                    update reports set state='PREVIEWED', workflow_version=0, first_previewed_at=now(),
                        assigned_signer_id='55555555-5555-5555-5555-555555555552',
                        assigned_approver_id='55555555-5555-5555-5555-555555555554',
                        produced_by='demo.producer', review_separation_required=true,
                        counter_signature_required=true, counter_signer_id=null,
                        counter_signature_prepared_at=null
                    where id='cccccccc-cccc-cccc-cccc-ccccccccccc2'
                    """);
            connection.createStatement().executeUpdate("""
                    insert into clinical_documents
                        (id, report_id, sha256, mime_type, size_bytes, version, original_filename,
                         object_key, uploaded_by, status)
                    values ('dddddddd-dddd-dddd-dddd-ddddddddddd9',
                            'cccccccc-cccc-cccc-cccc-ccccccccccc2',
                            'cccccccccccccccccccccccccccccccccccccccccccccccccccccccccccccccc',
                            'application/pdf', 10, 1, 'referto-revisione-totalmente-fittizio.pdf',
                            'review-demo/report.pdf', 'demo.uploader', 'ACTIVE')
                    """);
            connection.commit();
        }
    }

    @Test
    void crossesPreviewReviewAndApprovalWithViewTimeline() throws Exception {
        requestReview("request-review");
        mockMvc.perform(post(APPROVER_ROOT + "/documents/" + DOCUMENT + "/preview").with(approverJwt())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(Map.of("expectedVersion", 1, "operationKey", "view-review"))))
                .andExpect(status().isOk()).andExpect(jsonPath("$.state", equalTo("REVIEW_PENDING")))
                .andExpect(jsonPath("$.workflowVersion", equalTo(2)));
        mockMvc.perform(post(APPROVER_ROOT + "/approve").with(approverJwt())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(Map.of("expectedVersion", 2, "operationKey", "approve-review"))))
                .andExpect(status().isOk()).andExpect(jsonPath("$.state", equalTo("APPROVED")))
                .andExpect(jsonPath("$.version", equalTo(3)));
        mockMvc.perform(get(APPROVER_ROOT + "/review").with(approverJwt()))
                .andExpect(status().isOk()).andExpect(jsonPath("$.timeline.length()", equalTo(3)))
                .andExpect(jsonPath("$.timeline[0].decisionType", equalTo("APPROVED")));
    }

    @Test
    void rejectsOnlyWithReasonAndRecordsTheDecision() throws Exception {
        requestReview("request-before-reject");
        mockMvc.perform(post(APPROVER_ROOT + "/reject").with(approverJwt())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(Map.of("expectedVersion", 1, "operationKey", "reject-no-reason"))))
                .andExpect(status().isBadRequest());
        mockMvc.perform(post(APPROVER_ROOT + "/reject").with(approverJwt())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(Map.of("expectedVersion", 1, "operationKey", "reject-with-reason",
                                "reason", "Metadato dimostrativo da verificare"))))
                .andExpect(status().isOk()).andExpect(jsonPath("$.state", equalTo("PREVIEWED")));
        assertThat(jdbc.sql("""
                select reason from report_review_decisions
                where report_id=:reportId and decision_type='REJECTED'
                """).param("reportId", REPORT).query(String.class).single()).contains("dimostrativo");
    }

    @Test
    void replaysApprovalIdempotentlyAndAllowsAdminReturn() throws Exception {
        requestReview("request-idempotent");
        String approval = json(Map.of("expectedVersion", 1, "operationKey", "stable-approval"));
        mockMvc.perform(post(APPROVER_ROOT + "/approve").with(approverJwt()).contentType(MediaType.APPLICATION_JSON)
                        .content(approval)).andExpect(status().isOk()).andExpect(jsonPath("$.idempotent", equalTo(false)));
        mockMvc.perform(post(APPROVER_ROOT + "/approve").with(approverJwt()).contentType(MediaType.APPLICATION_JSON)
                        .content(approval)).andExpect(status().isOk()).andExpect(jsonPath("$.idempotent", equalTo(true)))
                .andExpect(jsonPath("$.version", equalTo(2)));
        mockMvc.perform(post("/api/admin/reports/" + REPORT + "/review/return").with(adminJwt())
                        .contentType(MediaType.APPLICATION_JSON).content(json(Map.of("expectedVersion", 2,
                                "operationKey", "admin-return", "reason", "Nuova revisione amministrativa richiesta"))))
                .andExpect(status().isOk()).andExpect(jsonPath("$.state", equalTo("REVIEW_PENDING")));
    }

    @Test
    void enforcesRoleSeparationAndEndpointPermissions() throws Exception {
        jdbc.sql("update clinical_documents set uploaded_by='demo.approver' where id=:id")
                .param("id", DOCUMENT).update();
        mockMvc.perform(post("/api/admin/reports/" + REPORT + "/review/configure").with(adminJwt())
                        .contentType(MediaType.APPLICATION_JSON).content(json(Map.of(
                                "approverId", APPROVER, "separationRequired", true,
                                "counterSignatureRequired", false, "expectedVersion", 0,
                                "operationKey", "invalid-uploader-approver"))))
                .andExpect(status().isUnprocessableEntity());
        mockMvc.perform(get("/api/approver/reports").with(signerJwt())).andExpect(status().isForbidden());
        mockMvc.perform(post(SIGNER_ROOT + "/review/request").with(approverJwt())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(Map.of("expectedVersion", 0, "operationKey", "wrong-role"))))
                .andExpect(status().isForbidden());
    }

    @Test
    void preparesCounterSignatureWithoutExecutingDigitalSignature() throws Exception {
        requestReview("request-before-counter-signature");
        mockMvc.perform(post(APPROVER_ROOT + "/approve").with(approverJwt())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(Map.of("expectedVersion", 1, "operationKey", "approve-before-counter"))))
                .andExpect(status().isOk()).andExpect(jsonPath("$.state", equalTo("APPROVED")));
        mockMvc.perform(post("/api/admin/reports/" + REPORT + "/review/prepare-counter-signature")
                        .with(adminJwt()).contentType(MediaType.APPLICATION_JSON)
                        .content(json(Map.of("expectedVersion", 2, "operationKey", "prepare-counter",
                                "counterSignerId", APPROVER))))
                .andExpect(status().isOk()).andExpect(jsonPath("$.state", equalTo("APPROVED")))
                .andExpect(jsonPath("$.review.counterSignaturePreparedAt").exists())
                .andExpect(jsonPath("$.review.timeline[0].decisionType",
                        equalTo("COUNTER_SIGNATURE_PREPARED")));
        assertThat(jdbc.sql("select count(*) from reports where id=:id and signed_at is not null")
                .param("id", REPORT).query(Integer.class).single()).isZero();
    }

    @Test
    void onlyOneConcurrentApprovalWinsForTheSameVersion() throws Exception {
        reviewService.requestReview(REPORT, new ReviewActionRequest(0, "concurrent-request"), "demo.signer");
        CountDownLatch ready = new CountDownLatch(2); CountDownLatch start = new CountDownLatch(1);
        try (var executor = Executors.newFixedThreadPool(2)) {
            var first = executor.submit(() -> concurrentApproval("approve-a", ready, start));
            var second = executor.submit(() -> concurrentApproval("approve-b", ready, start));
            ready.await(); start.countDown();
            List<Object> results = List.of(first.get(), second.get());
            assertThat(results.stream().filter(ReviewOperationResponse.class::isInstance)).hasSize(1);
            assertThat(results.stream().filter(ResponseStatusException.class::isInstance)).hasSize(1);
        }
        assertThat(jdbc.sql("select state from reports where id=:id").param("id", REPORT)
                .query(String.class).single()).isEqualTo("APPROVED");
        assertThat(jdbc.sql("""
                select count(*) from report_review_decisions
                where report_id=:id and decision_type='APPROVED'
                """).param("id", REPORT).query(Integer.class).single()).isEqualTo(1);
    }

    private Object concurrentApproval(String key, CountDownLatch ready, CountDownLatch start) {
        try { ready.countDown(); start.await(); return reviewService.approve(REPORT,
                new ReviewActionRequest(1, key), "demo.approver"); }
        catch (ResponseStatusException exception) { return exception; }
        catch (InterruptedException exception) { Thread.currentThread().interrupt(); throw new IllegalStateException(exception); }
    }

    private void requestReview(String key) throws Exception {
        mockMvc.perform(post(SIGNER_ROOT + "/review/request").with(signerJwt())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(Map.of("expectedVersion", 0, "operationKey", key))))
                .andExpect(status().isOk()).andExpect(jsonPath("$.state", equalTo("REVIEW_PENDING")));
    }

    private String json(Object value) throws Exception { return objectMapper.writeValueAsString(value); }
    private static RequestPostProcessor adminJwt() { return token("demo.admin", "ADMINISTRATOR"); }
    private static RequestPostProcessor signerJwt() { return token("demo.signer", "SIGNER"); }
    private static RequestPostProcessor approverJwt() { return token("demo.approver", "APPROVER"); }
    private static RequestPostProcessor token(String username, String role) {
        return jwt().jwt(value -> value.claim("preferred_username", username)
                        .claim("realm_access", Map.of("roles", List.of(role))))
                .authorities(new SimpleGrantedAuthority("ROLE_" + role));
    }
}
