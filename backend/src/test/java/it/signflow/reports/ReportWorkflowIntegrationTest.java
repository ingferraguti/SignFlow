package it.signflow.reports;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.hamcrest.Matchers.equalTo;
import static org.hamcrest.Matchers.hasItem;
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
import org.springframework.dao.DataAccessException;
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
class ReportWorkflowIntegrationTest {
    private static final UUID SIGNED = UUID.fromString("cccccccc-cccc-cccc-cccc-ccccccccccc1");
    private static final UUID READY = UUID.fromString("cccccccc-cccc-cccc-cccc-ccccccccccc2");
    private static final UUID MISSING_SIGNER = UUID.fromString("cccccccc-cccc-cccc-cccc-ccccccccccc3");
    private static final UUID INCOMPLETE = UUID.fromString("cccccccc-cccc-cccc-cccc-ccccccccccc4");
    private static final UUID DEMO_SIGNER = UUID.fromString("55555555-5555-5555-5555-555555555552");
    private static final String ROOT = "/api/admin/reports";

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
    @Autowired JdbcClient jdbcClient;
    @Autowired DataSource dataSource;
    @Autowired ReportWorkflowService workflowService;

    @BeforeEach
    void resetWorkflowFixtures() throws Exception {
        try (Connection connection = dataSource.getConnection()) {
            connection.setAutoCommit(false);
            connection.createStatement().execute("select set_config('signflow.workflow_transition_allowed', 'true', true)");
            connection.createStatement().executeUpdate("delete from report_workflow_events");
            connection.createStatement().executeUpdate("""
                    delete from clinical_documents where report_id in (
                        'cccccccc-cccc-cccc-cccc-ccccccccccc2',
                        'cccccccc-cccc-cccc-cccc-ccccccccccc3',
                        'cccccccc-cccc-cccc-cccc-ccccccccccc4')
                    """);
            connection.createStatement().executeUpdate("""
                    update reports set workflow_version=0, first_previewed_at=null,
                        state=case id
                            when 'cccccccc-cccc-cccc-cccc-ccccccccccc2' then 'READY_TO_SIGN'
                            when 'cccccccc-cccc-cccc-cccc-ccccccccccc3' then 'MISSING_SIGNER'
                            when 'cccccccc-cccc-cccc-cccc-ccccccccccc4' then 'INCOMPLETE'
                            else state end,
                        assigned_signer_id=case id
                            when 'cccccccc-cccc-cccc-cccc-ccccccccccc3' then null
                            else assigned_signer_id end
                    where id in (
                        'cccccccc-cccc-cccc-cccc-ccccccccccc2',
                        'cccccccc-cccc-cccc-cccc-ccccccccccc3',
                        'cccccccc-cccc-cccc-cccc-ccccccccccc4')
                    """);
            connection.createStatement().executeUpdate("""
                    insert into clinical_documents
                        (id, report_id, sha256, mime_type, size_bytes, version, original_filename,
                         object_key, uploaded_by, status)
                    values ('dddddddd-dddd-dddd-dddd-ddddddddddd2',
                            'cccccccc-cccc-cccc-cccc-ccccccccccc2',
                            'aaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaa',
                            'application/pdf', 10, 1, 'referto-pronto-totalmente-fittizio.pdf',
                            'workflow-demo/ready.pdf', 'workflow.test', 'ACTIVE')
                    """);
            connection.commit();
        }
    }

    @Test
    void assignsSignerDetectsIncompleteReportAndMovesItToReadyWhenComplete() throws Exception {
        mockMvc.perform(get(ROOT + "/" + MISSING_SIGNER + "/workflow").with(adminJwt()))
                .andExpect(status().isOk()).andExpect(jsonPath("$.state", equalTo("MISSING_SIGNER")))
                .andExpect(jsonPath("$.missingFields", hasItem("SIGNER")))
                .andExpect(jsonPath("$.missingFields", hasItem("ACTIVE_DOCUMENT")));

        mockMvc.perform(post(ROOT + "/" + MISSING_SIGNER + "/workflow/assign-signer").with(adminJwt())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(Map.of("signerId", DEMO_SIGNER, "expectedVersion", 0,
                                "operationKey", "assign-demo-signer"))))
                .andExpect(status().isOk()).andExpect(jsonPath("$.toState", equalTo("INCOMPLETE")))
                .andExpect(jsonPath("$.resultingVersion", equalTo(1)))
                .andExpect(jsonPath("$.missingFields", hasItem("ACTIVE_DOCUMENT")));

        addActiveDocument(MISSING_SIGNER);
        mockMvc.perform(post(ROOT + "/" + MISSING_SIGNER + "/workflow/evaluate-readiness").with(adminJwt())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(Map.of("expectedVersion", 1, "operationKey", "complete-report"))))
                .andExpect(status().isOk()).andExpect(jsonPath("$.toState", equalTo("READY_TO_SIGN")))
                .andExpect(jsonPath("$.resultingVersion", equalTo(2)));

        mockMvc.perform(post(ROOT + "/" + MISSING_SIGNER + "/workflow/assign-signer").with(adminJwt())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(Map.of("expectedVersion", 2, "operationKey", "clear-demo-signer"))))
                .andExpect(status().isOk()).andExpect(jsonPath("$.toState", equalTo("MISSING_SIGNER")))
                .andExpect(jsonPath("$.resultingVersion", equalTo(3)));
        mockMvc.perform(get(ROOT + "/" + MISSING_SIGNER + "/workflow").with(adminJwt()))
                .andExpect(status().isOk()).andExpect(jsonPath("$.history.length()", equalTo(3)));
    }

    @Test
    void replaysTheSameOperationOnceAndRejectsKeyReuseWithDifferentPayload() throws Exception {
        String request = json(Map.of("expectedVersion", 0, "operationKey", "stable-readiness-key"));
        mockMvc.perform(post(ROOT + "/" + INCOMPLETE + "/workflow/evaluate-readiness").with(adminJwt())
                        .contentType(MediaType.APPLICATION_JSON).content(request))
                .andExpect(status().isOk()).andExpect(jsonPath("$.idempotent", equalTo(false)))
                .andExpect(jsonPath("$.resultingVersion", equalTo(1)));
        mockMvc.perform(post(ROOT + "/" + INCOMPLETE + "/workflow/evaluate-readiness").with(adminJwt())
                        .contentType(MediaType.APPLICATION_JSON).content(request))
                .andExpect(status().isOk()).andExpect(jsonPath("$.idempotent", equalTo(true)))
                .andExpect(jsonPath("$.resultingVersion", equalTo(1)));
        mockMvc.perform(post(ROOT + "/" + INCOMPLETE + "/workflow/assign-signer").with(adminJwt())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(Map.of("signerId", DEMO_SIGNER, "expectedVersion", 0,
                                "operationKey", "stable-readiness-key"))))
                .andExpect(status().isConflict());
        assertThat(jdbcClient.sql("select count(*) from report_workflow_events where report_id=:id")
                .param("id", INCOMPLETE).query(Integer.class).single()).isEqualTo(1);
    }

    @Test
    void requiresCorrectionReasonRejectsForbiddenTransitionsAndBlocksDirectSqlStateChanges() throws Exception {
        mockMvc.perform(post(ROOT + "/" + READY + "/workflow/admin-correction").with(adminJwt())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(Map.of("targetState", "INCOMPLETE", "expectedVersion", 0,
                                "operationKey", "missing-reason"))))
                .andExpect(status().isBadRequest());

        jdbcClient.sql("delete from clinical_documents where report_id=:id").param("id", READY).update();
        mockMvc.perform(post(ROOT + "/" + READY + "/workflow/admin-correction").with(adminJwt())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(Map.of("targetState", "INCOMPLETE", "reason",
                                "Riallineamento amministrativo del referto dimostrativo", "expectedVersion", 0,
                                "operationKey", "document-removed-correction"))))
                .andExpect(status().isOk()).andExpect(jsonPath("$.toState", equalTo("INCOMPLETE")));
        assertThat(jdbcClient.sql("select reason from report_workflow_events where report_id=:id")
                .param("id", READY).query(String.class).single()).contains("Riallineamento");

        mockMvc.perform(post(ROOT + "/" + SIGNED + "/workflow/assign-signer").with(adminJwt())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(Map.of("signerId", DEMO_SIGNER, "expectedVersion", 0,
                                "operationKey", "forbidden-signed-assignment"))))
                .andExpect(status().isConflict());
        assertThatThrownBy(() -> jdbcClient.sql("update reports set state='SIGNED' where id=:id")
                .param("id", INCOMPLETE).update()).isInstanceOf(DataAccessException.class);
    }

    @Test
    void allowsOnlyOneOfTwoConcurrentUpdatesForTheSameVersion() throws Exception {
        CountDownLatch ready = new CountDownLatch(2);
        CountDownLatch start = new CountDownLatch(1);
        try (var executor = Executors.newFixedThreadPool(2)) {
            var first = executor.submit(() -> concurrentReadiness("concurrent-a", ready, start));
            var second = executor.submit(() -> concurrentReadiness("concurrent-b", ready, start));
            ready.await();
            start.countDown();
            List<Object> results = List.of(first.get(), second.get());
            assertThat(results.stream().filter(ReportWorkflowOperationResponse.class::isInstance)).hasSize(1);
            assertThat(results.stream().filter(ResponseStatusException.class::isInstance)).hasSize(1);
        }
        assertThat(jdbcClient.sql("select workflow_version from reports where id=:id")
                .param("id", INCOMPLETE).query(Long.class).single()).isEqualTo(1);
        assertThat(jdbcClient.sql("select count(*) from report_workflow_events where report_id=:id")
                .param("id", INCOMPLETE).query(Integer.class).single()).isEqualTo(1);
    }

    private Object concurrentReadiness(String key, CountDownLatch ready, CountDownLatch start) {
        try {
            ready.countDown();
            start.await();
            return workflowService.evaluateReadiness(INCOMPLETE, new EvaluateReadinessRequest(0, key), "demo.admin");
        } catch (ResponseStatusException exception) {
            return exception;
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException(exception);
        }
    }

    private void addActiveDocument(UUID reportId) {
        jdbcClient.sql("""
                insert into clinical_documents
                    (id, report_id, sha256, mime_type, size_bytes, version, original_filename,
                     object_key, uploaded_by, status)
                values (:id, :reportId,
                        'bbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbb',
                        'application/pdf', 10, 1, 'referto-completo-totalmente-fittizio.pdf',
                        :objectKey, 'workflow.test', 'ACTIVE')
                """).param("id", UUID.randomUUID()).param("reportId", reportId)
                .param("objectKey", "workflow-demo/" + reportId + ".pdf").update();
    }

    private String json(Object value) throws Exception {
        return objectMapper.writeValueAsString(value);
    }

    private static RequestPostProcessor adminJwt() {
        return jwt().jwt(token -> token.claim("preferred_username", "demo.admin")
                        .claim("realm_access", Map.of("roles", List.of("ADMINISTRATOR"))))
                .authorities(new SimpleGrantedAuthority("ROLE_ADMINISTRATOR"));
    }
}
