package it.signflow.signatures;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.equalTo;
import static org.hamcrest.Matchers.startsWith;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.sql.Connection;
import java.util.List;
import java.util.Map;
import java.util.UUID;
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
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.request.RequestPostProcessor;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

@SpringBootTest
@AutoConfigureMockMvc
@Testcontainers
@ActiveProfiles("test")
class MockSignatureWorkflowIntegrationTest {
    private static final UUID SUCCESS_A = UUID.fromString("eeeeeeee-eeee-eeee-eeee-eeeeeeeeeee1");
    private static final UUID RETRY = UUID.fromString("eeeeeeee-eeee-eeee-eeee-eeeeeeeeeee2");
    private static final UUID FAILURE = UUID.fromString("eeeeeeee-eeee-eeee-eeee-eeeeeeeeeee3");
    private static final UUID SUCCESS_B = UUID.fromString("eeeeeeee-eeee-eeee-eeee-eeeeeeeeeee4");
    private static final List<UUID> REPORTS = List.of(SUCCESS_A, RETRY, FAILURE, SUCCESS_B);
    private static final String ROOT = "/api/signer/signatures";

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

    @BeforeEach
    void reset() throws Exception {
        try (Connection connection = dataSource.getConnection()) {
            connection.setAutoCommit(false);
            connection.createStatement().execute("select set_config('signflow.workflow_transition_allowed','true',true)");
            connection.createStatement().executeUpdate("delete from signature_batch_operations");
            connection.createStatement().executeUpdate("delete from signature_attempts");
            connection.createStatement().executeUpdate("delete from signature_batches");
            connection.createStatement().executeUpdate("delete from provider_sessions");
            connection.createStatement().executeUpdate("""
                    delete from report_workflow_events where report_id in (
                    'eeeeeeee-eeee-eeee-eeee-eeeeeeeeeee1','eeeeeeee-eeee-eeee-eeee-eeeeeeeeeee2',
                    'eeeeeeee-eeee-eeee-eeee-eeeeeeeeeee3','eeeeeeee-eeee-eeee-eeee-eeeeeeeeeee4')
                    """);
            connection.createStatement().executeUpdate("""
                    delete from clinical_documents where report_id in (
                    'eeeeeeee-eeee-eeee-eeee-eeeeeeeeeee1','eeeeeeee-eeee-eeee-eeee-eeeeeeeeeee2',
                    'eeeeeeee-eeee-eeee-eeee-eeeeeeeeeee3','eeeeeeee-eeee-eeee-eeee-eeeeeeeeeee4')
                    """);
            connection.createStatement().executeUpdate("""
                    update reports set state='APPROVED', workflow_version=0, signed_at=null,
                        signature_kind=null, signature_artifact_notice=null
                    where id in (
                    'eeeeeeee-eeee-eeee-eeee-eeeeeeeeeee1','eeeeeeee-eeee-eeee-eeee-eeeeeeeeeee2',
                    'eeeeeeee-eeee-eeee-eeee-eeeeeeeeeee3','eeeeeeee-eeee-eeee-eeee-eeeeeeeeeee4')
                    """);
            int index = 0;
            for (UUID report : REPORTS) {
                connection.createStatement().executeUpdate("""
                        insert into clinical_documents
                            (id, report_id, sha256, mime_type, size_bytes, version, original_filename,
                             object_key, uploaded_by, status)
                        values ('00000000-0000-0000-0000-00000000000%1$d', '%2$s',
                                '%3$s', 'application/pdf', 10, 1,
                                'referto-firma-mock-totalmente-fittizio.pdf',
                                'mock-signature-test/%2$s.pdf', 'demo.producer', 'ACTIVE')
                        """.formatted(++index, report, String.valueOf(index).repeat(64)));
            }
            connection.commit();
        }
    }

    @Test
    void successfulSingleSignatureProducesOnlyMockArtifact() throws Exception {
        String session = session();
        JsonNode result = postJson(ROOT + "/single", Map.of("reportId", SUCCESS_A,
                "providerSessionId", session, "operationKey", "single-success"));
        assertThat(result.path("state").asText()).isEqualTo("COMPLETED");
        assertThat(result.path("attempts").get(0).path("state").asText()).isEqualTo("SUCCEEDED");
        assertThat(result.path("attempts").get(0).path("artifactNotice").asText()).startsWith("MOCK ONLY");
        String artifact = result.path("attempts").get(0).path("artifactId").asText();
        mockMvc.perform(get(ROOT + "/artifacts/" + artifact).with(signerJwt()))
                .andExpect(status().isOk())
                .andExpect(header().string("X-SignFlow-Signature-Type", "MOCK-NON-LEGAL"))
                .andExpect(header().string("Content-Disposition", startsWith("attachment")));
        assertThat(jdbc.sql("select signature_kind from reports where id=:id").param("id", SUCCESS_A)
                .query(String.class).single()).isEqualTo("MOCK");
        assertThat(jdbc.sql("""
                select count(*) from provider_sessions
                where provider_session_reference is not null and challenge_reference is not null
                  and correlation_id is not null
                """).query(Integer.class).single()).isEqualTo(1);
        assertThat(jdbc.sql("""
                select count(*) from information_schema.columns
                where table_name='provider_sessions' and column_name in ('authorization_code','otp','password')
                """).query(Integer.class).single()).isZero();
    }

    @Test
    void failedSingleSignatureHasPerDocumentError() throws Exception {
        JsonNode result = postJson(ROOT + "/single", Map.of("reportId", FAILURE,
                "providerSessionId", session(), "operationKey", "single-failure"));
        assertThat(result.path("state").asText()).isEqualTo("FAILED");
        assertThat(result.path("attempts").get(0).path("state").asText()).isEqualTo("FAILED");
        assertThat(result.path("attempts").get(0).path("errorCode").asText()).isEqualTo("MOCK_PLANNED_FAILURE");
        assertThat(state(FAILURE)).isEqualTo("SIGN_ERROR");
    }

    @Test
    void filteredBatchCompletesSuccessfully() throws Exception {
        String session = session();
        JsonNode draft = postJson(ROOT + "/batches", Map.of("selectionMode", "FILTERED",
                "filters", Map.of("query", "RPT-MOCK-OK"), "operationKey", "filtered-success"));
        assertThat(draft.path("totalCount").asInt()).isEqualTo(2);
        String batch = draft.path("id").asText();
        postJson(ROOT + "/batches/" + batch + "/confirm",
                Map.of("providerSessionId", session, "operationKey", "confirm-success"));
        JsonNode result = postJson(ROOT + "/batches/" + batch + "/start",
                Map.of("providerSessionId", session, "operationKey", "start-success"));
        assertThat(result.path("state").asText()).isEqualTo("COMPLETED");
        assertThat(result.path("successCount").asInt()).isEqualTo(2);
    }

    @Test
    void manualBatchCanCompletePartially() throws Exception {
        String session = session();
        JsonNode draft = postJson(ROOT + "/batches", Map.of("selectionMode", "MANUAL",
                "reportIds", List.of(SUCCESS_A, FAILURE), "operationKey", "manual-partial"));
        String batch = draft.path("id").asText();
        postJson(ROOT + "/batches/" + batch + "/confirm",
                Map.of("providerSessionId", session, "operationKey", "confirm-partial"));
        JsonNode result = postJson(ROOT + "/batches/" + batch + "/start",
                Map.of("providerSessionId", session, "operationKey", "start-partial"));
        assertThat(result.path("state").asText()).isEqualTo("PARTIAL_SUCCESS");
        assertThat(result.path("successCount").asInt()).isEqualTo(1);
        assertThat(result.path("failureCount").asInt()).isEqualTo(1);
    }

    @Test
    void controlledRetryTurnsPlannedFailureIntoSuccess() throws Exception {
        String session = session();
        JsonNode failed = postJson(ROOT + "/single", Map.of("reportId", RETRY,
                "providerSessionId", session, "operationKey", "single-retry"));
        String batch = failed.path("id").asText();
        String attempt = failed.path("attempts").get(0).path("id").asText();
        JsonNode result = postJson(ROOT + "/batches/" + batch + "/attempts/" + attempt + "/retry",
                Map.of("providerSessionId", session, "operationKey", "retry-once"));
        assertThat(result.path("state").asText()).isEqualTo("COMPLETED");
        assertThat(result.path("attempts").get(0).path("retryCount").asInt()).isEqualTo(1);
        assertThat(result.path("attempts").get(0).path("state").asText()).isEqualTo("SUCCEEDED");
    }

    @Test
    void repeatedSingleSubmissionIsIdempotent() throws Exception {
        String session = session();
        Map<String, Object> body = Map.of("reportId", SUCCESS_A, "providerSessionId", session,
                "operationKey", "same-single-operation");
        String first = postJson(ROOT + "/single", body).path("id").asText();
        String second = postJson(ROOT + "/single", body).path("id").asText();
        assertThat(second).isEqualTo(first);
        assertThat(jdbc.sql("select count(*) from signature_batches").query(Integer.class).single()).isEqualTo(1);
        assertThat(jdbc.sql("select count(*) from signature_attempts").query(Integer.class).single()).isEqualTo(1);
    }

    @Test
    void linkedLoginProfilesShareBatchesSessionsAndPersonScopedIdempotency() throws Exception {
        String session = session();
        Map<String, Object> body = Map.of("selectionMode", "MANUAL",
                "reportIds", List.of(SUCCESS_A), "operationKey", "same-person-operation");
        JsonNode created = postJson(ROOT + "/batches", body);
        MvcResult replay = mockMvc.perform(post(ROOT + "/batches").with(signerJwt("demo.signer.alt"))
                        .contentType(MediaType.APPLICATION_JSON).content(objectMapper.writeValueAsString(body)))
                .andExpect(status().isOk()).andReturn();
        assertThat(objectMapper.readTree(replay.getResponse().getContentAsByteArray()).path("id").asText())
                .isEqualTo(created.path("id").asText());
        mockMvc.perform(post(ROOT + "/batches/" + created.path("id").asText() + "/confirm")
                        .with(signerJwt("demo.signer.alt")).contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of("providerSessionId", session,
                                "operationKey", "alternate-confirm"))))
                .andExpect(status().isOk()).andExpect(jsonPath("$.state", equalTo("CONFIRMED")));
    }

    @Test
    void confirmedBatchCanBeCancelledBeforeStart() throws Exception {
        String session = session();
        JsonNode draft = postJson(ROOT + "/batches", Map.of("selectionMode", "MANUAL",
                "reportIds", List.of(SUCCESS_A), "operationKey", "cancel-draft"));
        String batch = draft.path("id").asText();
        postJson(ROOT + "/batches/" + batch + "/confirm",
                Map.of("providerSessionId", session, "operationKey", "cancel-confirm"));
        JsonNode cancelled = postJson(ROOT + "/batches/" + batch + "/cancel",
                Map.of("operationKey", "cancel-before-start"));
        assertThat(cancelled.path("state").asText()).isEqualTo("CANCELLED");
        assertThat(cancelled.path("attempts").get(0).path("state").asText()).isEqualTo("CANCELLED");
        assertThat(state(SUCCESS_A)).isEqualTo("APPROVED");
    }

    private String session() throws Exception {
        return postJson(ROOT + "/provider-sessions", Map.of("authorizationCode", "000000"))
                .path("id").asText();
    }

    private JsonNode postJson(String path, Object body) throws Exception {
        MvcResult result = mockMvc.perform(post(path).with(signerJwt()).contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(body)))
                .andExpect(status().isOk()).andReturn();
        return objectMapper.readTree(result.getResponse().getContentAsByteArray());
    }

    private String state(UUID report) {
        return jdbc.sql("select state from reports where id=:id").param("id", report)
                .query(String.class).single();
    }

    private static RequestPostProcessor signerJwt() {
        return signerJwt("demo.signer");
    }

    private static RequestPostProcessor signerJwt(String username) {
        return jwt().jwt(token -> token.claim("preferred_username", username)
                        .claim("realm_access", Map.of("roles", List.of("SIGNER"))))
                .authorities(new SimpleGrantedAuthority("ROLE_SIGNER"));
    }
}
