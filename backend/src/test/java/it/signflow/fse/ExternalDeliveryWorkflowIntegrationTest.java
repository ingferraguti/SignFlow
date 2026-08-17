package it.signflow.fse;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.equalTo;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import it.signflow.reports.ClinicalDocumentService;
import it.signflow.reports.DemoClinicalDocumentInitializer;
import java.sql.Connection;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import javax.sql.DataSource;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
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
class ExternalDeliveryWorkflowIntegrationTest {
    private static final UUID ACCEPTED = UUID.fromString("ffffffff-ffff-ffff-ffff-fffffffffff1");
    private static final UUID RETRY = UUID.fromString("ffffffff-ffff-ffff-ffff-fffffffffff2");
    private static final String ROOT = "/api/admin/external-deliveries";
    private static final String ACCESS_KEY = "external-test-access";
    private static final String SECRET_KEY = "external-test-secret";
    private static final String BUCKET = "external-test-receipts";

    @Container static final PostgreSQLContainer<?> POSTGRES = new PostgreSQLContainer<>("postgres:16-alpine");
    @Container static final GenericContainer<?> MINIO = new GenericContainer<>(
            DockerImageName.parse("minio/minio:RELEASE.2025-04-22T22-12-26Z"))
            .withExposedPorts(9000).withEnv("MINIO_ROOT_USER", ACCESS_KEY).withEnv("MINIO_ROOT_PASSWORD", SECRET_KEY)
            .withCommand("server", "/data").waitingFor(Wait.forHttp("/minio/health/ready").forPort(9000));

    @DynamicPropertySource static void properties(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", POSTGRES::getJdbcUrl);
        registry.add("spring.datasource.username", POSTGRES::getUsername);
        registry.add("spring.datasource.password", POSTGRES::getPassword);
        registry.add("signflow.documents.endpoint", ExternalDeliveryWorkflowIntegrationTest::minioEndpoint);
        registry.add("signflow.documents.public-endpoint", ExternalDeliveryWorkflowIntegrationTest::minioEndpoint);
        registry.add("signflow.documents.access-key", () -> ACCESS_KEY);
        registry.add("signflow.documents.secret-key", () -> SECRET_KEY);
        registry.add("signflow.documents.bucket", () -> BUCKET);
        registry.add("signflow.documents.demo-enabled", () -> false);
        registry.add("signflow.external-delivery.allow-mock-signatures", () -> true);
        registry.add("signflow.external-delivery.demo-enabled", () -> false);
    }

    @Autowired MockMvc mockMvc;
    @Autowired ObjectMapper objectMapper;
    @Autowired JdbcClient jdbc;
    @Autowired DataSource dataSource;
    @Autowired ClinicalDocumentService documents;

    @BeforeEach void reset() throws Exception {
        try (Connection connection = dataSource.getConnection()) {
            connection.setAutoCommit(false);
            connection.createStatement().execute("select set_config('signflow.workflow_transition_allowed','true',true)");
            connection.createStatement().executeUpdate("delete from external_delivery_receipts");
            connection.createStatement().executeUpdate("delete from external_delivery_attempts");
            connection.createStatement().executeUpdate("delete from external_delivery_commands");
            connection.createStatement().executeUpdate("delete from external_delivery_operations");
            connection.createStatement().executeUpdate("delete from report_workflow_events where report_id in ('ffffffff-ffff-ffff-ffff-fffffffffff1','ffffffff-ffff-ffff-ffff-fffffffffff2')");
            connection.createStatement().executeUpdate("delete from clinical_documents where report_id in ('ffffffff-ffff-ffff-ffff-fffffffffff1','ffffffff-ffff-ffff-ffff-fffffffffff2')");
            connection.createStatement().executeUpdate("""
                    update reports set state='SIGNED', workflow_version=0, department='Medicina generale'
                    where id in ('ffffffff-ffff-ffff-ffff-fffffffffff1','ffffffff-ffff-ffff-ffff-fffffffffff2')
                    """);
            connection.createStatement().executeUpdate("""
                    update mock_external_delivery_scenarios set fse_timeouts_before_result=0,
                      conservation_timeouts_before_result=0,
                      fse_rejections_before_acceptance=case when report_id='ffffffff-ffff-ffff-ffff-fffffffffff2' then 1 else 0 end,
                      conservation_rejections_before_acceptance=case when report_id='ffffffff-ffff-ffff-ffff-fffffffffff2' then 1 else 0 end
                    """);
            connection.commit();
        }
        documents.upload(ACCEPTED, "referto-fse-fittizio.pdf", DemoClinicalDocumentInitializer.demoPdf(), "demo.producer");
        documents.upload(RETRY, "referto-fse-retry-fittizio.pdf", DemoClinicalDocumentInitializer.demoPdf(), "demo.producer");
    }

    @Test void signedMockDocumentCrossesFseAndConservationWithStoredReceiptsAndAudit() throws Exception {
        Map<String, Object> sendBody = command(0, "fse-accepted-send");
        JsonNode sent = postJson(ROOT + "/fse/reports/" + ACCEPTED, sendBody, 200);
        assertThat(sent.at("/operation/state").asText()).isEqualTo("FSE_SENT");
        assertThat(sent.at("/metadata/facilityCode").asText()).isEqualTo("PRESIDIO-DEMO");
        assertThat(sent.path("receipts")).hasSize(1);

        JsonNode repeated = postJson(ROOT + "/fse/reports/" + ACCEPTED, sendBody, 200);
        assertThat(repeated.at("/operation/id").asText()).isEqualTo(sent.at("/operation/id").asText());
        JsonNode fseAccepted = postJson(ROOT + "/" + sent.at("/operation/id").asText() + "/reconcile",
                command(1, "fse-accepted-reconcile"), 200);
        assertThat(fseAccepted.at("/operation/state").asText()).isEqualTo("FSE_ACCEPTED");

        JsonNode conservationSent = postJson(ROOT + "/conservation/reports/" + ACCEPTED,
                command(2, "conservation-send"), 200);
        JsonNode conserved = postJson(ROOT + "/" + conservationSent.at("/operation/id").asText() + "/reconcile",
                command(3, "conservation-reconcile"), 200);
        assertThat(conserved.at("/operation/state").asText()).isEqualTo("CONSERVATION_ACCEPTED");
        assertThat(state(ACCEPTED)).isEqualTo("CONSERVATION_ACCEPTED");

        String receipt = conserved.path("receipts").get(1).path("id").asText();
        mockMvc.perform(get(ROOT + "/receipts/" + receipt + "/download").with(adminJwt()))
                .andExpect(status().isOk()).andExpect(header().string("Content-Disposition", containsString("attachment")))
                .andExpect(header().string("X-Content-Type-Options", "nosniff"))
                .andExpect(content().string(containsString("\"mockOnly\":true")));
        mockMvc.perform(get("/api/admin/audit/reports/" + ACCEPTED + "/timeline").with(adminJwt()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[?(@.eventType == 'FSE_RECEIPT_STORED')]").isNotEmpty())
                .andExpect(jsonPath("$[?(@.eventType == 'CONSERVATION_DELIVERY_ATTEMPT')]").isNotEmpty());
    }

    @Test void rejectionCanBeRetriedAndRepeatedRetryIsIdempotent() throws Exception {
        JsonNode sent = postJson(ROOT + "/fse/reports/" + RETRY, command(0, "retry-send"), 200);
        JsonNode rejected = postJson(ROOT + "/" + sent.at("/operation/id").asText() + "/reconcile",
                command(1, "retry-reject"), 200);
        assertThat(rejected.at("/operation/state").asText()).isEqualTo("FSE_REJECTED");
        Map<String, Object> retryBody = command(2, "retry-once");
        JsonNode retried = postJson(ROOT + "/" + sent.at("/operation/id").asText() + "/retry", retryBody, 200);
        JsonNode replay = postJson(ROOT + "/" + sent.at("/operation/id").asText() + "/retry", retryBody, 200);
        assertThat(replay.at("/operation/version").asLong()).isEqualTo(retried.at("/operation/version").asLong());
        JsonNode accepted = postJson(ROOT + "/" + sent.at("/operation/id").asText() + "/reconcile",
                command(3, "retry-accepted"), 200);
        assertThat(accepted.at("/operation/state").asText()).isEqualTo("FSE_ACCEPTED");
        assertThat(jdbc.sql("select count(*) from external_delivery_commands where operation_id=:id and command_key='retry-once'")
                .param("id", UUID.fromString(sent.at("/operation/id").asText())).query(Integer.class).single()).isOne();
    }

    @Test void timeoutStaysReconcilableAndMissingFacilityMappingCreatesValidationError() throws Exception {
        jdbc.sql("update mock_external_delivery_scenarios set fse_timeouts_before_result=1 where report_id=:id")
                .param("id", ACCEPTED).update();
        JsonNode sent = postJson(ROOT + "/fse/reports/" + ACCEPTED, command(0, "timeout-send"), 200);
        JsonNode timeout = postJson(ROOT + "/" + sent.at("/operation/id").asText() + "/reconcile",
                command(1, "timeout-first-poll"), 200);
        assertThat(timeout.at("/operation/state").asText()).isEqualTo("TIMEOUT");
        assertThat(timeout.path("reconciliationAllowed").asBoolean()).isTrue();
        JsonNode accepted = postJson(ROOT + "/" + sent.at("/operation/id").asText() + "/reconcile",
                command(2, "timeout-second-poll"), 200);
        assertThat(accepted.at("/operation/state").asText()).isEqualTo("FSE_ACCEPTED");

        reset();
        jdbc.sql("update reports set department='Reparto senza mapping' where id=:id").param("id", ACCEPTED).update();
        JsonNode invalid = postJson(ROOT + "/fse/reports/" + ACCEPTED, command(0, "missing-mapping"), 200);
        assertThat(invalid.at("/operation/state").asText()).isEqualTo("FSE_VALIDATION_ERROR");
        assertThat(invalid.at("/operation/errorCode").asText()).isEqualTo("FSE_METADATA_INVALID");
        assertThat(invalid.path("receipts")).isEmpty();
    }

    @Test void concurrentReconciliationAllowsOnlyOneDecisionAndAccessIsAdministratorOnly() throws Exception {
        JsonNode sent = postJson(ROOT + "/fse/reports/" + ACCEPTED, command(0, "concurrent-send"), 200);
        String path = ROOT + "/" + sent.at("/operation/id").asText() + "/reconcile";
        CompletableFuture<Integer> first = CompletableFuture.supplyAsync(() -> postStatus(path, command(1, "concurrent-a")));
        CompletableFuture<Integer> second = CompletableFuture.supplyAsync(() -> postStatus(path, command(1, "concurrent-b")));
        assertThat(List.of(first.get(), second.get())).containsExactlyInAnyOrder(200, 409);
        mockMvc.perform(get(ROOT).with(signerJwt())).andExpect(status().isForbidden());
        mockMvc.perform(get(ROOT)).andExpect(status().isUnauthorized());
    }

    private JsonNode postJson(String path, Object body, int expectedStatus) throws Exception {
        MvcResult result = mockMvc.perform(post(path).with(adminJwt()).contentType("application/json")
                        .content(objectMapper.writeValueAsBytes(body)))
                .andExpect(status().is(expectedStatus)).andReturn();
        return objectMapper.readTree(result.getResponse().getContentAsByteArray());
    }
    private int postStatus(String path, Object body) {
        try { return mockMvc.perform(post(path).with(adminJwt()).contentType("application/json")
                .content(objectMapper.writeValueAsBytes(body))).andReturn().getResponse().getStatus(); }
        catch (Exception exception) { throw new RuntimeException(exception); }
    }
    private Map<String, Object> command(long version, String key) { return Map.of("expectedVersion", version, "operationKey", key); }
    private String state(UUID id) { return jdbc.sql("select state from reports where id=:id").param("id", id).query(String.class).single(); }
    private static String minioEndpoint() { return "http://" + MINIO.getHost() + ":" + MINIO.getMappedPort(9000); }
    private RequestPostProcessor adminJwt() { return jwt().jwt(token -> token.claim("preferred_username", "demo.admin"))
            .authorities(new SimpleGrantedAuthority("ROLE_ADMINISTRATOR")); }
    private RequestPostProcessor signerJwt() { return jwt().jwt(token -> token.claim("preferred_username", "demo.signer"))
            .authorities(new SimpleGrantedAuthority("ROLE_SIGNER")); }
}
