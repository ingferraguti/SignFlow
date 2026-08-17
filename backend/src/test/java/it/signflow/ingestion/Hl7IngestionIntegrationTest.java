package it.signflow.ingestion;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.equalTo;
import static org.hamcrest.Matchers.greaterThanOrEqualTo;
import static org.hamcrest.Matchers.hasItem;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.minio.MinioClient;
import io.minio.StatObjectArgs;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;
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
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.containers.wait.strategy.Wait;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.utility.DockerImageName;

@SpringBootTest
@AutoConfigureMockMvc
@Testcontainers
@ActiveProfiles("test")
class Hl7IngestionIntegrationTest {
    private static final String ACCESS_KEY = "ingestion-test-access";
    private static final String SECRET_KEY = "ingestion-test-secret";
    private static final String BUCKET = "ingestion-test-documents";

    @Container static final PostgreSQLContainer<?> POSTGRES = new PostgreSQLContainer<>("postgres:16-alpine");
    @Container static final GenericContainer<?> MINIO = new GenericContainer<>(
            DockerImageName.parse("minio/minio:RELEASE.2025-04-22T22-12-26Z"))
            .withExposedPorts(9000).withEnv("MINIO_ROOT_USER", ACCESS_KEY)
            .withEnv("MINIO_ROOT_PASSWORD", SECRET_KEY).withCommand("server", "/data")
            .waitingFor(Wait.forHttp("/minio/health/ready").forPort(9000));

    @DynamicPropertySource
    static void properties(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", POSTGRES::getJdbcUrl);
        registry.add("spring.datasource.username", POSTGRES::getUsername);
        registry.add("spring.datasource.password", POSTGRES::getPassword);
        registry.add("signflow.documents.endpoint", Hl7IngestionIntegrationTest::minioEndpoint);
        registry.add("signflow.documents.public-endpoint", Hl7IngestionIntegrationTest::minioEndpoint);
        registry.add("signflow.documents.access-key", () -> ACCESS_KEY);
        registry.add("signflow.documents.secret-key", () -> SECRET_KEY);
        registry.add("signflow.documents.bucket", () -> BUCKET);
        registry.add("signflow.documents.demo-enabled", () -> false);
        registry.add("signflow.ingestion.demo-enabled", () -> false);
        registry.add("signflow.ingestion.mllp-enabled", () -> false);
    }

    @Autowired MockMvc mockMvc;
    @Autowired ObjectMapper objectMapper;
    @Autowired JdbcClient jdbc;
    @Autowired ReportIngestionService service;

    @Test
    void restIngestionExecutesConfiguredPipelinePersistsRawAndBuildsAnAuditableReport() throws Exception {
        String raw = DemoHl7IngestionInitializer.oru(
                "LIS-DEMO", "REST-ORU-001", "RPT-REST-ORU-001", true);
        JsonNode body = ingest(raw, "corr-rest-oru-001", 201);
        UUID messageId = UUID.fromString(body.get("messageId").asText());
        UUID reportId = UUID.fromString(body.get("reportId").asText());

        assertThat(body.get("status").asText()).isEqualTo("PROCESSED");
        assertThat(body.get("reportState").asText()).isEqualTo("READY_TO_SIGN");
        assertThat(body.get("documentId").isNull()).isFalse();
        assertThat(jdbc.sql("select state from reports where id=:id").param("id", reportId)
                .query(String.class).single()).isEqualTo("READY_TO_SIGN");
        assertThat(jdbc.sql("select count(*) from report_workflow_events where report_id=:id")
                .param("id", reportId).query(Long.class).single()).isEqualTo(2);

        Map<String, Object> stored = jdbc.sql("""
                select raw_object_key,cda_builder_called,document_normalizer_called,
                       pdf_a3_converter_called,passthrough_applied
                from hl7_messages where id=:id
                """).param("id", messageId).query((rs, row) -> Map.<String, Object>of(
                        "key", rs.getString(1), "cda", rs.getBoolean(2),
                        "normalizer", rs.getBoolean(3), "converter", rs.getBoolean(4),
                        "passthrough", rs.getBoolean(5))).single();
        assertThat(stored).containsEntry("cda", true).containsEntry("normalizer", true)
                .containsEntry("converter", true).containsEntry("passthrough", false);
        assertThat(minioClient().statObject(StatObjectArgs.builder().bucket(BUCKET)
                .object((String) stored.get("key")).build()).size()).isEqualTo(raw.getBytes().length);

        MvcResult detail = mockMvc.perform(get("/api/admin/monitoring/messages/{id}", messageId).with(admin()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.cdaBuilderCalled", equalTo(true)))
                .andExpect(jsonPath("$.documentNormalizerCalled", equalTo(true)))
                .andExpect(jsonPath("$.pdfA3ConverterCalled", equalTo(true)))
                .andReturn();
        String masked = detail.getResponse().getContentAsString();
        assertThat(masked).contains("CONTENUTO MASCHERATO").doesNotContain("TSTCHR85A41H501Q")
                .doesNotContain("Fittizia").doesNotContain("JVBER");

        mockMvc.perform(get("/api/admin/audit/reports/{id}/timeline", reportId).with(admin()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[*].eventType", hasItem("HL7_MESSAGE_RECEIVED")))
                .andExpect(jsonPath("$[*].eventType", hasItem("HL7_MESSAGE_PROCESSED")))
                .andExpect(jsonPath("$[*].actorType", hasItem("TECHNICAL")));
    }

    @Test
    void supportsPassthroughMissingSignerIncompleteAndVisibleDiscardReasons() throws Exception {
        JsonNode passthrough = ingest(DemoHl7IngestionInitializer.mdm(
                "DOC-DEMO", "REST-MDM-001", "RPT-REST-MDM-001"), "corr-rest-mdm-001", 201);
        assertThat(passthrough.get("reportState").asText()).isEqualTo("MISSING_SIGNER");
        UUID passthroughMessage = UUID.fromString(passthrough.get("messageId").asText());
        mockMvc.perform(get("/api/admin/monitoring/messages/{id}", passthroughMessage).with(admin()))
                .andExpect(status().isOk()).andExpect(jsonPath("$.passthroughApplied", equalTo(true)))
                .andExpect(jsonPath("$.cdaBuilderCalled", equalTo(false)))
                .andExpect(jsonPath("$.documentNormalizerCalled", equalTo(false)))
                .andExpect(jsonPath("$.pdfA3ConverterCalled", equalTo(false)));
        mockMvc.perform(get("/api/admin/monitoring/reports-without-signer").with(admin()))
                .andExpect(status().isOk()).andExpect(jsonPath("$.total", greaterThanOrEqualTo(1)))
                .andExpect(jsonPath("$.items[*].reportId", hasItem(passthrough.get("reportId").asText())));

        String incompleteRaw = DemoHl7IngestionInitializer.oru(
                "LIS-DEMO", "REST-INCOMPLETE-001", "RPT-REST-INCOMPLETE-001", true)
                .replace("PAT-HL7-DEMO-001^^^DEMO^MR", "");
        assertThat(ingest(incompleteRaw, "corr-rest-incomplete-001", 201)
                .get("reportState").asText()).isEqualTo("INCOMPLETE");

        JsonNode discarded = ingest(DemoHl7IngestionInitializer.oru(
                "UNKNOWN-DEMO", "REST-REJECT-001", "RPT-REST-REJECT-001", false),
                "corr-rest-reject-001", 422);
        assertThat(discarded.get("errorCode").asText()).isEqualTo("SOURCE_SYSTEM_NOT_FOUND");
        mockMvc.perform(get("/api/admin/monitoring/messages").param("status", "DISCARDED")
                        .param("correlationId", "corr-rest-reject-001").with(admin()))
                .andExpect(status().isOk()).andExpect(jsonPath("$.total", equalTo(1)))
                .andExpect(jsonPath("$.items[0].errorCode", equalTo("SOURCE_SYSTEM_NOT_FOUND")));
    }

    @Test
    void exactRetriesAreIdempotentAndChangedDuplicatesAreDiscarded() throws Exception {
        String raw = DemoHl7IngestionInitializer.oru(
                "LIS-DEMO", "REST-IDEMPOTENT-001", "RPT-REST-IDEMPOTENT-001", true);
        JsonNode first = ingest(raw, "corr-idempotent-first", 201);
        JsonNode repeated = ingest(raw, "corr-idempotent-repeat", 200);
        assertThat(repeated.get("idempotent").asBoolean()).isTrue();
        assertThat(repeated.get("messageId").asText()).isEqualTo(first.get("messageId").asText());
        assertThat(repeated.get("reportId").asText()).isEqualTo(first.get("reportId").asText());

        JsonNode conflict = ingest(raw.replace("Fittizia^Chiara", "Fittizia^Cora"),
                "corr-idempotent-conflict", 422);
        assertThat(conflict.get("errorCode").asText()).isEqualTo("DUPLICATE_CONFLICT");
        assertThat(jdbc.sql("select count(*) from reports where external_identifier='RPT-REST-IDEMPOTENT-001'")
                .query(Long.class).single()).isEqualTo(1);
    }

    @Test
    void concurrentDuplicateSubmissionCreatesExactlyOneReport() throws Exception {
        String raw = DemoHl7IngestionInitializer.oru(
                "LIS-DEMO", "REST-CONCURRENT-001", "RPT-REST-CONCURRENT-001", true);
        CompletableFuture<IngestionResult> left = CompletableFuture.supplyAsync(() -> service.ingestHl7(
                raw, null, "concurrent-left", "corr-concurrent-left", IngestionTransport.REST));
        CompletableFuture<IngestionResult> right = CompletableFuture.supplyAsync(() -> service.ingestHl7(
                raw, null, "concurrent-right", "corr-concurrent-right", IngestionTransport.REST));
        IngestionResult first = left.get(30, TimeUnit.SECONDS);
        IngestionResult second = right.get(30, TimeUnit.SECONDS);

        assertThat(List.of(first.messageId(), second.messageId())).containsOnly(first.messageId());
        assertThat(List.of(first.idempotent(), second.idempotent())).containsExactlyInAnyOrder(false, true);
        assertThat(jdbc.sql("select count(*) from reports where external_identifier='RPT-REST-CONCURRENT-001'")
                .query(Long.class).single()).isEqualTo(1);
    }

    @Test
    void ingestionEndpointRequiresTheDedicatedRoleOrAdministratorRole() throws Exception {
        String raw = DemoHl7IngestionInitializer.oru(
                "LIS-DEMO", "REST-AUTH-001", "RPT-REST-AUTH-001", true);
        mockMvc.perform(post("/api/ingestion/hl7").contentType("application/hl7-v2").content(raw))
                .andExpect(status().isUnauthorized());
        mockMvc.perform(post("/api/ingestion/hl7").contentType("application/hl7-v2").content(raw)
                        .with(token("demo.signer", "SIGNER")))
                .andExpect(status().isForbidden());
        mockMvc.perform(post("/api/ingestion/hl7").contentType("application/hl7-v2").content(raw)
                        .header("X-Correlation-ID", "corr-auth-ingestion").with(token("demo.ingestion", "INGESTION")))
                .andExpect(status().isCreated());
    }

    private JsonNode ingest(String raw, String correlation, int expectedStatus) throws Exception {
        MvcResult result = mockMvc.perform(post("/api/ingestion/hl7")
                        .contentType("application/hl7-v2").content(raw)
                        .header("X-Correlation-ID", correlation).with(token("demo.ingestion", "INGESTION")))
                .andExpect(status().is(expectedStatus)).andReturn();
        return objectMapper.readTree(result.getResponse().getContentAsString());
    }

    private static RequestPostProcessor admin() { return token("demo.admin", "ADMINISTRATOR"); }
    private static RequestPostProcessor token(String username, String role) {
        return jwt().jwt(value -> value.subject(username).claim("preferred_username", username)
                        .claim("realm_access", Map.of("roles", List.of(role))))
                .authorities(new SimpleGrantedAuthority("ROLE_" + role));
    }
    private static String minioEndpoint() { return "http://" + MINIO.getHost() + ":" + MINIO.getMappedPort(9000); }
    private static MinioClient minioClient() { return MinioClient.builder().endpoint(minioEndpoint())
            .credentials(ACCESS_KEY, SECRET_KEY).build(); }
}
