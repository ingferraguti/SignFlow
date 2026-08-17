package it.signflow.reports;

import static org.hamcrest.Matchers.containsInAnyOrder;
import static org.hamcrest.Matchers.equalTo;
import static org.hamcrest.Matchers.hasSize;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fasterxml.jackson.databind.ObjectMapper;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.util.UUID;
import javax.sql.DataSource;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
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
class SignerReportIntegrationTest {
    private static final UUID DIRECT_REPORT = UUID.fromString("cccccccc-cccc-cccc-cccc-ccccccccccc2");
    private static final UUID GROUP_REPORT = UUID.fromString("cccccccc-cccc-cccc-cccc-ccccccccccc3");
    private static final UUID FOREIGN_REPORT = UUID.fromString("cccccccc-cccc-cccc-cccc-ccccccccccc5");
    private static final UUID PARTITION_REPORT = UUID.fromString("cccccccc-cccc-cccc-cccc-ccccccccccc6");
    private static final String ROOT = "/api/signer";
    private static final String ACCESS_KEY = "signer-test-access";
    private static final String SECRET_KEY = "signer-test-secret";

    @Container
    static final PostgreSQLContainer<?> POSTGRES = new PostgreSQLContainer<>("postgres:16-alpine");

    @Container
    static final GenericContainer<?> MINIO = new GenericContainer<>(
            DockerImageName.parse("minio/minio:RELEASE.2025-04-22T22-12-26Z"))
            .withExposedPorts(9000)
            .withEnv("MINIO_ROOT_USER", ACCESS_KEY)
            .withEnv("MINIO_ROOT_PASSWORD", SECRET_KEY)
            .withCommand("server", "/data")
            .waitingFor(Wait.forHttp("/minio/health/ready").forPort(9000));

    @DynamicPropertySource
    static void properties(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", POSTGRES::getJdbcUrl);
        registry.add("spring.datasource.username", POSTGRES::getUsername);
        registry.add("spring.datasource.password", POSTGRES::getPassword);
        registry.add("signflow.documents.endpoint", SignerReportIntegrationTest::minioEndpoint);
        registry.add("signflow.documents.public-endpoint", SignerReportIntegrationTest::minioEndpoint);
        registry.add("signflow.documents.access-key", () -> ACCESS_KEY);
        registry.add("signflow.documents.secret-key", () -> SECRET_KEY);
        registry.add("signflow.documents.bucket", () -> "signer-test-documents");
        registry.add("signflow.documents.demo-enabled", () -> false);
    }

    @Autowired MockMvc mockMvc;
    @Autowired ObjectMapper objectMapper;
    @Autowired ClinicalDocumentService documentService;
    @Autowired DataSource dataSource;

    @BeforeEach
    void restorePreviewableState() throws Exception {
        try (var connection = dataSource.getConnection()) {
            connection.setAutoCommit(false);
            try (var statement = connection.prepareStatement(
                    "select set_config('signflow.workflow_transition_allowed', 'true', true)")) {
                statement.execute();
            }
            try (var statement = connection.prepareStatement("""
                    update reports set state='READY_TO_SIGN', workflow_version=0, first_previewed_at=null
                    where id=?
                    """)) {
                statement.setObject(1, DIRECT_REPORT);
                statement.executeUpdate();
            }
            try (var statement = connection.prepareStatement(
                    "delete from report_workflow_events where report_id=?")) {
                statement.setObject(1, DIRECT_REPORT);
                statement.executeUpdate();
            }
            connection.commit();
        }
    }

    @Test
    void exposesOnlyReportsAssignedToTheAuthenticatedNaturalPerson() throws Exception {
        mockMvc.perform(get(ROOT + "/reports?size=20").with(signerJwt("demo.signer")))
                .andExpect(status().isOk()).andExpect(jsonPath("$.total", equalTo(9)))
                .andExpect(jsonPath("$.items[*].internalIdentifier", containsInAnyOrder(
                        "RPT-INT-001", "RPT-INT-002", "RPT-INT-004",
                        "RPT-MOCK-OK-001", "RPT-MOCK-RETRY-001", "RPT-MOCK-FAIL-001", "RPT-MOCK-OK-002",
                        "RPT-FSE-MOCK-OK-001", "RPT-FSE-MOCK-RETRY-001")));
        mockMvc.perform(get(ROOT + "/reports/" + GROUP_REPORT).with(signerJwt("demo.signer")))
                .andExpect(status().isNotFound());
        mockMvc.perform(get(ROOT + "/reports/" + PARTITION_REPORT).with(signerJwt("demo.signer")))
                .andExpect(status().isNotFound());
        mockMvc.perform(get(ROOT + "/reports/" + FOREIGN_REPORT).with(signerJwt("demo.signer")))
                .andExpect(status().isNotFound());

        mockMvc.perform(get(ROOT + "/reports?size=20").with(signerJwt("other.signer")))
                .andExpect(status().isOk()).andExpect(jsonPath("$.total", equalTo(2)))
                .andExpect(jsonPath("$.items[*].internalIdentifier", containsInAnyOrder("RPT-INT-005", "RPT-INT-006")));
        mockMvc.perform(get(ROOT + "/reports/cccccccc-cccc-cccc-cccc-ccccccccccc1")
                        .with(signerJwt("other.signer"))).andExpect(status().isNotFound());

        mockMvc.perform(get(ROOT + "/reports?size=20").with(signerJwt("demo.signer.alt")))
                .andExpect(status().isOk()).andExpect(jsonPath("$.total", equalTo(9)))
                .andExpect(jsonPath("$.items[*].internalIdentifier", containsInAnyOrder(
                        "RPT-INT-001", "RPT-INT-002", "RPT-INT-004",
                        "RPT-MOCK-OK-001", "RPT-MOCK-RETRY-001", "RPT-MOCK-FAIL-001", "RPT-MOCK-OK-002",
                        "RPT-FSE-MOCK-OK-001", "RPT-FSE-MOCK-RETRY-001")));
    }

    @Test
    void supportsSimpleAdvancedDateStateAndPagingSearches() throws Exception {
        mockMvc.perform(get(ROOT + "/reports?query=Bruno").with(signerJwt("demo.signer")))
                .andExpect(status().isOk()).andExpect(jsonPath("$.total", equalTo(5)));
        mockMvc.perform(get(ROOT + "/reports?patient=Dario&documentType=PDF-REF&department=Diagnostica")
                        .with(signerJwt("demo.signer")))
                .andExpect(status().isOk()).andExpect(jsonPath("$.total", equalTo(0)));
        mockMvc.perform(get(ROOT + "/reports?state=MISSING_SIGNER&producedFrom=2026-07-01&producedTo=2026-07-31")
                        .with(signerJwt("demo.signer")))
                .andExpect(status().isOk()).andExpect(jsonPath("$.items", hasSize(0)));
        mockMvc.perform(get(ROOT + "/reports?signedFrom=2026-07-01&signedTo=2026-07-01&page=0&size=1")
                        .with(signerJwt("demo.signer")))
                .andExpect(status().isOk()).andExpect(jsonPath("$.total", equalTo(1)))
                .andExpect(jsonPath("$.items[0].internalIdentifier", equalTo("RPT-INT-001")));
        mockMvc.perform(get(ROOT + "/reports?query=nessun-risultato").with(signerJwt("demo.signer")))
                .andExpect(status().isOk()).andExpect(jsonPath("$.items", hasSize(0)));
        for (String query : java.util.List.of("?page=-1", "?size=101", "?state=UNKNOWN",
                "?producedFrom=2026-07-10&producedTo=2026-07-01")) {
            mockMvc.perform(get(ROOT + "/reports" + query).with(signerJwt("demo.signer")))
                    .andExpect(status().isBadRequest());
        }
    }

    @Test
    void opensRealPdfAndTransitionsOnlyReadyReportToPreviewed() throws Exception {
        ClinicalDocumentResponse uploaded = documentService.upload(DIRECT_REPORT, "referto-fittizio.pdf",
                DemoClinicalDocumentInitializer.demoPdf(), "demo.admin");
        String path = ROOT + "/reports/" + DIRECT_REPORT + "/documents/" + uploaded.id();

        String request = "{\"expectedVersion\":0,\"operationKey\":\"preview-ready-report\"}";
        MvcResult preview = mockMvc.perform(post(path + "/preview").with(signerJwt("demo.signer"))
                        .contentType(MediaType.APPLICATION_JSON).content(request))
                .andExpect(status().isOk()).andExpect(jsonPath("$.reportState", equalTo("PREVIEWED")))
                .andExpect(jsonPath("$.workflowVersion", equalTo(1))).andReturn();
        mockMvc.perform(post(path + "/preview").with(signerJwt("demo.signer"))
                        .contentType(MediaType.APPLICATION_JSON).content(request))
                .andExpect(status().isOk()).andExpect(jsonPath("$.reportState", equalTo("PREVIEWED")))
                .andExpect(jsonPath("$.workflowVersion", equalTo(1)));
        String url = objectMapper.readTree(preview.getResponse().getContentAsString()).get("url").asText();
        HttpResponse<byte[]> pdf = HttpClient.newHttpClient().send(
                HttpRequest.newBuilder(URI.create(url)).GET().build(), HttpResponse.BodyHandlers.ofByteArray());
        org.junit.jupiter.api.Assertions.assertEquals(200, pdf.statusCode());
        org.junit.jupiter.api.Assertions.assertArrayEquals(DemoClinicalDocumentInitializer.demoPdf(), pdf.body());
        mockMvc.perform(get(ROOT + "/reports/" + DIRECT_REPORT).with(signerJwt("demo.signer")))
                .andExpect(status().isOk()).andExpect(jsonPath("$.state", equalTo("PREVIEWED")));
        mockMvc.perform(get(path + "/content").with(signerJwt("demo.signer")))
                .andExpect(status().isOk()).andExpect(content().contentType(MediaType.APPLICATION_PDF));
        mockMvc.perform(get(ROOT + "/reports/" + FOREIGN_REPORT + "/documents")
                        .with(signerJwt("demo.signer"))).andExpect(status().isNotFound());
    }

    @Test
    void exposesHomeProfileLegendAndEnforcesAuthenticationAndRole() throws Exception {
        mockMvc.perform(get(ROOT + "/home").with(signerJwt("demo.signer")))
                .andExpect(status().isOk()).andExpect(jsonPath("$.total", equalTo(9)));
        mockMvc.perform(get(ROOT + "/profile").with(signerJwt("demo.signer")))
                .andExpect(status().isOk()).andExpect(jsonPath("$.username", equalTo("demo.signer")))
                .andExpect(jsonPath("$.partitionCode", equalTo("LOCAL")))
                .andExpect(jsonPath("$.groups[0]", equalTo("LOCAL-SIGNERS")))
                .andExpect(jsonPath("$.authenticationAccounts", hasSize(2)))
                .andExpect(jsonPath("$.digitalSignatures", hasSize(2)));
        mockMvc.perform(get(ROOT + "/states").with(signerJwt("demo.signer")))
                .andExpect(status().isOk()).andExpect(jsonPath("$", hasSize(19)))
                .andExpect(jsonPath("$[18].code", equalTo("CONSERVATION_REJECTED")));
        mockMvc.perform(get("/api/ui-texts").with(signerJwt("demo.signer")))
                .andExpect(status().isOk()).andExpect(jsonPath("$['menu.signerHome']", equalTo("Home firmatario")));
        mockMvc.perform(get(ROOT + "/reports").with(adminJwt())).andExpect(status().isForbidden());
        mockMvc.perform(get(ROOT + "/reports")).andExpect(status().isUnauthorized());
    }

    @Test
    void preferredDigitalSignatureIsProfileSpecificAndRestrictedToTheNaturalPerson() throws Exception {
        String secondary = "88888888-8888-8888-8888-888888888889";
        String changedProfile = mockMvc.perform(post(ROOT + "/profile/preferred-signature/" + secondary)
                        .with(signerJwt("demo.signer")))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsString();
        boolean secondaryPreferred = false;
        for (var signature : objectMapper.readTree(changedProfile).path("digitalSignatures")) {
            if (secondary.equals(signature.path("id").asText())) secondaryPreferred = signature.path("preferred").asBoolean();
        }
        org.junit.jupiter.api.Assertions.assertTrue(secondaryPreferred);
        mockMvc.perform(post(ROOT + "/profile/preferred-signature/" + secondary)
                        .with(signerJwt("other.signer")))
                .andExpect(status().isBadRequest());
        String primaryPerson = objectMapper.readTree(mockMvc.perform(get(ROOT + "/profile")
                        .with(signerJwt("demo.signer"))).andExpect(status().isOk()).andReturn()
                .getResponse().getContentAsString()).path("naturalPersonId").asText();
        String alternatePerson = objectMapper.readTree(mockMvc.perform(get(ROOT + "/profile")
                        .with(signerJwt("demo.signer.alt"))).andExpect(status().isOk()).andReturn()
                .getResponse().getContentAsString()).path("naturalPersonId").asText();
        org.junit.jupiter.api.Assertions.assertEquals(primaryPerson, alternatePerson);
    }

    private static String minioEndpoint() {
        return "http://" + MINIO.getHost() + ":" + MINIO.getMappedPort(9000);
    }

    private static RequestPostProcessor signerJwt(String username) {
        return jwt().jwt(token -> token.claim("preferred_username", username))
                .authorities(new SimpleGrantedAuthority("ROLE_SIGNER"));
    }

    private static RequestPostProcessor adminJwt() {
        return jwt().jwt(token -> token.claim("preferred_username", "demo.admin"))
                .authorities(new SimpleGrantedAuthority("ROLE_ADMINISTRATOR"));
    }
}
