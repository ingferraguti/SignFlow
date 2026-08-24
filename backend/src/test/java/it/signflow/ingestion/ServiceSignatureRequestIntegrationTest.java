package it.signflow.ingestion;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.equalTo;
import static org.hamcrest.Matchers.hasItem;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.minio.MinioClient;
import io.minio.StatObjectArgs;
import it.signflow.signatures.DigitalSignatureEngine;
import it.signflow.signatures.TestSignatureFixtures;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.mock.web.MockMultipartFile;
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
class ServiceSignatureRequestIntegrationTest {
    private static final String ROOT = "/api/integration/signature-requests";
    private static final String ACCESS_KEY = "integration-request-access";
    private static final String SECRET_KEY = "integration-request-secret";
    private static final String BUCKET = "integration-request-documents";
    private static byte[] PDF;
    private static TestSignatureFixtures.TestKeyMaterial KEY_MATERIAL;

    @BeforeAll
    static void fixtures() {
        PDF = TestSignatureFixtures.fictionalPdf();
        KEY_MATERIAL = TestSignatureFixtures.testKeyMaterial();
    }

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
        registry.add("signflow.documents.endpoint", ServiceSignatureRequestIntegrationTest::minioEndpoint);
        registry.add("signflow.documents.public-endpoint", ServiceSignatureRequestIntegrationTest::minioEndpoint);
        registry.add("signflow.documents.access-key", () -> ACCESS_KEY);
        registry.add("signflow.documents.secret-key", () -> SECRET_KEY);
        registry.add("signflow.documents.bucket", () -> BUCKET);
        registry.add("signflow.documents.max-size-bytes", () -> 1_048_576);
        registry.add("signflow.documents.demo-enabled", () -> false);
    }

    @Autowired MockMvc mockMvc;
    @Autowired ObjectMapper objectMapper;
    @Autowired JdbcClient jdbc;
    @Autowired ServiceSignatureRequestService service;
    @Autowired DigitalSignatureEngine signatureEngine;

    @Test
    void exposesTheControlledHealthcareAndAdministrativeCatalog() throws Exception {
        mockMvc.perform(get(ROOT + "/document-types").with(ingestion()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[*].documentType", hasItem("HEALTHCARE")))
                .andExpect(jsonPath("$[*].documentType", hasItem("ADMINISTRATIVE")))
                .andExpect(jsonPath("$[?(@.documentType == 'HEALTHCARE')].subtypes[*].code",
                        hasItem("LABORATORY_REPORT")))
                .andExpect(jsonPath("$[?(@.documentType == 'ADMINISTRATIVE')].subtypes[*].code",
                        hasItem("CONTRACT")));
    }

    @Test
    void receivesAHealthcarePdfResolvesTheSignerStoresPrivatelyAndAuditsIntake() throws Exception {
        JsonNode created = submit(metadata("SVC-HEALTH-001", "HEALTHCARE", "LABORATORY_REPORT"),
                PDF, "health-001", "corr-health-001", 201);
        UUID requestId = UUID.fromString(created.get("requestId").asText());

        assertThat(created.get("status").asText()).isEqualTo("PENDING_SIGNATURE");
        assertThat(created.at("/signer/naturalPersonId").asText()).isNotBlank();
        assertThat(created.at("/sourceDocument/originalFilename").asText()).isEqualTo("documento-fittizio.pdf");
        assertThat(created.at("/normalizedDocument/profile").asText()).isEqualTo("PDF/A-3B");
        assertThat(created.at("/normalizedDocument/pdfaPart").asText()).isEqualTo("3");
        assertThat(created.at("/normalizedDocument/pdfaConformance").asText()).isEqualTo("B");
        assertThat(created.at("/normalizedDocument/validator").asText()).startsWith("veraPDF");
        assertThat(created.at("/signature/signed").asBoolean()).isFalse();
        assertThat(created.at("/conservation/status").asText()).isEqualTo("NOT_REQUESTED");
        assertThat(created.get("idempotent").asBoolean()).isFalse();

        var artifacts = jdbc.sql("""
                select d.artifact_type,d.object_key,d.size_bytes from service_signature_request_documents d
                where d.request_id=:id order by d.artifact_type
                """).param("id", requestId).query((rs, row) -> Map.of("type", rs.getString("artifact_type"),
                        "key", rs.getString("object_key"), "size", rs.getLong("size_bytes"))).list();
        assertThat(artifacts).hasSize(2);
        assertThat(artifacts).allSatisfy(value -> assertThat(value.get("key").toString())
                .doesNotContain("documento-fittizio"));
        var original = artifacts.stream().filter(value -> value.get("type").equals("ORIGINAL")).findFirst().orElseThrow();
        assertThat(minioClient().statObject(StatObjectArgs.builder().bucket(BUCKET)
                .object(original.get("key").toString()).build()).size()).isEqualTo(PDF.length);
        assertThat(jdbc.sql("select count(*) from service_signature_request_events where request_id=:id")
                .param("id", requestId).query(Long.class).single()).isEqualTo(1);
        assertThat(jdbc.sql("""
                select count(*) from audit_events where entity_type='SIGNATURE_REQUEST' and entity_id=:id
                """).param("id", requestId.toString()).query(Long.class).single()).isEqualTo(1);
        assertThat(jdbc.sql("""
                select count(*) from information_schema.columns
                where table_name in ('service_signature_requests','service_signature_request_documents')
                  and data_type='bytea'
                """).query(Long.class).single()).isZero();

        mockMvc.perform(get(ROOT + "/{id}", requestId).header("X-Source-System", "LIS-DEMO")
                        .with(ingestion()))
                .andExpect(status().isOk()).andExpect(jsonPath("$.requestId", equalTo(requestId.toString())))
                .andExpect(header().string("Cache-Control", "no-store"));
        mockMvc.perform(get(ROOT + "/{id}", requestId).header("X-Source-System", "DOC-DEMO")
                        .with(ingestion()))
                .andExpect(status().isNotFound());
    }

    @Test
    void acceptsAdministrativeSubtypesAndEnforcesIdempotency() throws Exception {
        String first = metadata("SVC-ADMIN-001", "ADMINISTRATIVE", "CONTRACT");
        JsonNode created = submit(first, PDF, "admin-001", "corr-admin-001", 201);
        JsonNode repeated = submit(first, PDF, "admin-001", "corr-admin-repeat", 200);
        assertThat(repeated.get("requestId").asText()).isEqualTo(created.get("requestId").asText());
        assertThat(repeated.get("correlationId").asText()).isEqualTo("corr-admin-001");
        assertThat(repeated.get("idempotent").asBoolean()).isTrue();

        submit(metadata("SVC-ADMIN-001", "ADMINISTRATIVE", "RESOLUTION"), PDF,
                "admin-001", "corr-admin-conflict", 409);
        assertThat(jdbc.sql("select count(*) from service_signature_requests where external_request_id='SVC-ADMIN-001'")
                .query(Long.class).single()).isEqualTo(1);
    }

    @Test
    void acceptsPdfAliasesImagesAndTextAndAlwaysStoresValidatedPdfA3() throws Exception {
        MvcResult alias = mockMvc.perform(multipart(ROOT).file(jsonPart(metadata(
                                "SVC-PDF-ALIAS-001", "HEALTHCARE", "LABORATORY_REPORT")))
                        .file(new MockMultipartFile("file", "versione.pdf", "application/x-pdf", PDF))
                        .header("X-Source-System", "LIS-DEMO").header("X-Idempotency-Key", "pdf-alias-001")
                        .with(ingestion())).andExpect(status().isCreated())
                .andExpect(jsonPath("$.sourceDocument.contentType", equalTo("application/pdf")))
                .andExpect(jsonPath("$.normalizedDocument.profile", equalTo("PDF/A-3B"))).andReturn();
        assertThat(objectMapper.readTree(alias.getResponse().getContentAsString())
                .at("/normalizedDocument/sha256").asText()).hasSize(64);

        byte[] png = smallPng();
        mockMvc.perform(multipart(ROOT).file(jsonPart(metadata(
                                "SVC-PNG-001", "ADMINISTRATIVE", "CONTRACT")))
                        .file(new MockMultipartFile("file", "scansione.png", "image/png", png))
                        .header("X-Source-System", "LIS-DEMO").header("X-Idempotency-Key", "png-001")
                        .with(ingestion())).andExpect(status().isCreated())
                .andExpect(jsonPath("$.sourceDocument.contentType", equalTo("image/png")))
                .andExpect(jsonPath("$.normalizedDocument.pdfaConformance", equalTo("B")));

        mockMvc.perform(multipart(ROOT).file(jsonPart(metadata(
                                "SVC-TEXT-001", "ADMINISTRATIVE", "TECHNICAL_REPORT")))
                        .file(new MockMultipartFile("file", "nota.txt", "text/plain; charset=UTF-8",
                                "Documento fittizio".getBytes(StandardCharsets.UTF_8)))
                        .header("X-Source-System", "LIS-DEMO").header("X-Idempotency-Key", "text-001")
                        .with(ingestion())).andExpect(status().isCreated())
                .andExpect(jsonPath("$.sourceDocument.contentType", equalTo("text/plain")))
                .andExpect(jsonPath("$.normalizedDocument.profile", equalTo("PDF/A-3B")));
    }

    @Test
    void completesVerifiedSignatureDownloadAndConservationLifecycle() throws Exception {
        JsonNode created = submit(metadata("SVC-LIFECYCLE-001", "HEALTHCARE", "LABORATORY_REPORT"),
                PDF, "lifecycle-001", "corr-lifecycle-001", 201);
        UUID requestId = UUID.fromString(created.get("requestId").asText());
        mockMvc.perform(get(ROOT + "/{id}/signed-document", requestId)
                        .header("X-Source-System", "LIS-DEMO").with(ingestion()))
                .andExpect(status().isConflict());

        MvcResult signableResult = mockMvc.perform(get("/api/adapters/signature-requests/{id}/signable-document", requestId)
                        .with(token("demo.signature-adapter", "SIGNATURE_ADAPTER")))
                .andExpect(status().isOk()).andExpect(header().exists("X-Document-SHA256")).andReturn();
        byte[] signable = signableResult.getResponse().getContentAsByteArray();
        var signed = signatureEngine.createTestPades(new DigitalSignatureEngine.PadesRequest(signable,
                KEY_MATERIAL.pkcs12(), TestSignatureFixtures.PASSWORD, "normalizzato.pdf",
                "Firma fittizia di test", "Ambiente test", "corr-lifecycle-001"));

        mockMvc.perform(multipart("/api/adapters/signature-requests/{id}/signed-document", requestId)
                        .file(new MockMultipartFile("file", "firmato.pdf", MediaType.APPLICATION_PDF_VALUE,
                                signed.signedPdf())).header("X-Idempotency-Key", "signed-result-001")
                        .with(token("demo.signature-adapter", "SIGNATURE_ADAPTER")))
                .andExpect(status().isOk()).andExpect(jsonPath("$.status", equalTo("SIGNED")))
                .andExpect(jsonPath("$.signature.signed", equalTo(true)))
                .andExpect(jsonPath("$.signature.validation", equalTo("TECHNICALLY_VALID")))
                .andExpect(jsonPath("$.signature.signedDocumentAvailable", equalTo(true)));
        mockMvc.perform(multipart("/api/adapters/signature-requests/{id}/signed-document", requestId)
                        .file(new MockMultipartFile("file", "firmato.pdf", MediaType.APPLICATION_PDF_VALUE,
                                signed.signedPdf())).header("X-Idempotency-Key", "signed-result-001")
                        .with(token("demo.signature-adapter", "SIGNATURE_ADAPTER")))
                .andExpect(status().isOk()).andExpect(jsonPath("$.idempotent", equalTo(true)));

        MvcResult downloaded = mockMvc.perform(get(ROOT + "/{id}/signed-document", requestId)
                        .header("X-Source-System", "LIS-DEMO").with(ingestion()))
                .andExpect(status().isOk()).andExpect(header().string("Cache-Control", "no-store"))
                .andExpect(header().exists("X-Document-SHA256")).andReturn();
        assertThat(downloaded.getResponse().getContentAsByteArray()).isEqualTo(signed.signedPdf());

        conservation(requestId, "conservation-pending-001", """
                {"status":"PENDING","occurredAt":"2026-08-24T08:00:00Z"}
                """).andExpect(jsonPath("$.conservation.sent", equalTo(false)));
        conservation(requestId, "conservation-sent-001", """
                {"status":"SENT","remoteReference":"CONS-FICT-001","occurredAt":"2026-08-24T08:01:00Z"}
                """).andExpect(jsonPath("$.conservation.sent", equalTo(true)));
        String accepted = """
                {"status":"ACCEPTED","remoteReference":"CONS-FICT-001","occurredAt":"2026-08-24T08:02:00Z"}
                """;
        conservation(requestId, "conservation-accepted-001", accepted)
                .andExpect(jsonPath("$.conservation.status", equalTo("ACCEPTED")))
                .andExpect(jsonPath("$.conservation.sent", equalTo(true)));
        conservation(requestId, "conservation-accepted-001", accepted)
                .andExpect(jsonPath("$.idempotent", equalTo(true)));

        mockMvc.perform(get(ROOT + "/{id}", requestId).header("X-Source-System", "LIS-DEMO").with(ingestion()))
                .andExpect(status().isOk()).andExpect(jsonPath("$.signature.signed", equalTo(true)))
                .andExpect(jsonPath("$.conservation.status", equalTo("ACCEPTED")));
        assertThat(jdbc.sql("select count(*) from service_signature_request_events where request_id=:id")
                .param("id", requestId).query(Long.class).single()).isEqualTo(5);
        assertThat(jdbc.sql("select count(*) from audit_events where entity_type='SIGNATURE_REQUEST' and entity_id=:id")
                .param("id", requestId.toString()).query(Long.class).single()).isEqualTo(5);
    }

    @Test
    void rejectsUnsignedOrDifferentDocumentsFromTheSignatureAdapter() throws Exception {
        JsonNode created = submit(metadata("SVC-SIGNED-INVALID-001", "ADMINISTRATIVE", "CONTRACT"),
                PDF, "signed-invalid-001", "corr-signed-invalid-001", 201);
        UUID requestId = UUID.fromString(created.get("requestId").asText());
        byte[] signable = mockMvc.perform(get("/api/adapters/signature-requests/{id}/signable-document", requestId)
                        .with(token("demo.signature-adapter", "SIGNATURE_ADAPTER")))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsByteArray();
        mockMvc.perform(multipart("/api/adapters/signature-requests/{id}/signed-document", requestId)
                        .file(new MockMultipartFile("file", "unsigned.pdf", MediaType.APPLICATION_PDF_VALUE, signable))
                        .header("X-Idempotency-Key", "unsigned-result-001")
                        .with(token("demo.signature-adapter", "SIGNATURE_ADAPTER")))
                .andExpect(status().isUnprocessableEntity());

        var wrong = signatureEngine.createTestPades(new DigitalSignatureEngine.PadesRequest(
                TestSignatureFixtures.fictionalPdf(), KEY_MATERIAL.pkcs12(), TestSignatureFixtures.PASSWORD,
                "altro.pdf", "Firma fittizia di test", "Ambiente test", "corr-wrong-document"));
        mockMvc.perform(multipart("/api/adapters/signature-requests/{id}/signed-document", requestId)
                        .file(new MockMultipartFile("file", "wrong.pdf", MediaType.APPLICATION_PDF_VALUE,
                                wrong.signedPdf())).header("X-Idempotency-Key", "wrong-document-result-001")
                        .with(token("demo.signature-adapter", "SIGNATURE_ADAPTER")))
                .andExpect(status().isUnprocessableEntity());
        assertThat(jdbc.sql("select status from service_signature_requests where id=:id")
                .param("id", requestId).query(String.class).single()).isEqualTo("PENDING_SIGNATURE");
        assertThat(jdbc.sql("select count(*) from service_signature_request_documents where request_id=:id")
                .param("id", requestId).query(Long.class).single()).isEqualTo(2);
    }

    @Test
    void concurrentIdenticalSubmissionsCreateOneDurableRequest() throws Exception {
        var signer = new ServiceSignatureRequestInput.SignerIdentifier(PersonIdentifierScheme.IT_TAX_CODE,
                "IT", "AGENZIA_ENTRATE", "DMSLGN80A01H501U");
        var input = new ServiceSignatureRequestInput("SVC-CONCURRENT-001", ServiceDocumentType.HEALTHCARE,
                "LABORATORY_REPORT", signer);
        CompletableFuture<ServiceSignatureRequestResponse> left = CompletableFuture.supplyAsync(() -> service.submit(
                input, pdfPart(PDF), "LIS-DEMO", "concurrent-001", "corr-concurrent-left", "demo.integration"));
        CompletableFuture<ServiceSignatureRequestResponse> right = CompletableFuture.supplyAsync(() -> service.submit(
                input, pdfPart(PDF), "LIS-DEMO", "concurrent-001", "corr-concurrent-right", "demo.integration"));
        ServiceSignatureRequestResponse first = left.get(30, TimeUnit.SECONDS);
        ServiceSignatureRequestResponse second = right.get(30, TimeUnit.SECONDS);

        assertThat(first.requestId()).isEqualTo(second.requestId());
        assertThat(List.of(first.idempotent(), second.idempotent())).containsExactlyInAnyOrder(false, true);
        assertThat(jdbc.sql("""
                select count(*) from service_signature_requests where external_request_id='SVC-CONCURRENT-001'
                """).query(Long.class).single()).isEqualTo(1);
    }

    @Test
    void rejectsMismatchedSubtypeUnknownSignerAndInvalidPdf() throws Exception {
        submit(metadata("SVC-INVALID-001", "ADMINISTRATIVE", "LABORATORY_REPORT"), PDF,
                "invalid-001", "corr-invalid-type", 422);
        submit(metadata("SVC-INVALID-002", "HEALTHCARE", "LABORATORY_REPORT")
                        .replace("DMSLGN80A01H501U", "UNKNOWN-SIGNER"), PDF,
                "invalid-002", "corr-invalid-signer", 422);
        submit(metadata("SVC-INVALID-003", "HEALTHCARE", "LABORATORY_REPORT"),
                "not a pdf".getBytes(StandardCharsets.UTF_8), "invalid-003", "corr-invalid-pdf", 400);

        String validMetadata = metadata("SVC-INVALID-004", "HEALTHCARE", "LABORATORY_REPORT");
        mockMvc.perform(multipart(ROOT).file(jsonPart(validMetadata))
                        .file(new MockMultipartFile("file", "../unsafe.pdf", MediaType.APPLICATION_PDF_VALUE, PDF))
                        .header("X-Source-System", "LIS-DEMO").with(ingestion()))
                .andExpect(status().isBadRequest());
        mockMvc.perform(multipart(ROOT).file(jsonPart(validMetadata))
                        .file(new MockMultipartFile("file", "wrong.pdf", MediaType.TEXT_PLAIN_VALUE, PDF))
                        .header("X-Source-System", "LIS-DEMO").with(ingestion()))
                .andExpect(status().isBadRequest());

        byte[] oversized = new byte[1_048_577];
        mockMvc.perform(multipart(ROOT).file(jsonPart(validMetadata))
                        .file(new MockMultipartFile("file", "large.pdf", MediaType.APPLICATION_PDF_VALUE, oversized))
                        .header("X-Source-System", "LIS-DEMO").with(ingestion()))
                .andExpect(status().isPayloadTooLarge());
    }

    @Test
    void requiresTheDedicatedMachineRoleOrAdministrator() throws Exception {
        MockMultipartFile request = jsonPart(metadata("SVC-AUTH-001", "HEALTHCARE", "LABORATORY_REPORT"));
        MockMultipartFile file = pdfPart(PDF);
        mockMvc.perform(multipart(ROOT).file(request).file(file).header("X-Source-System", "LIS-DEMO"))
                .andExpect(status().isUnauthorized());
        mockMvc.perform(multipart(ROOT).file(request).file(file).header("X-Source-System", "LIS-DEMO")
                        .with(token("demo.signer", "SIGNER")))
                .andExpect(status().isForbidden());
        mockMvc.perform(get(ROOT + "/document-types").with(token("demo.admin", "ADMINISTRATOR")))
                .andExpect(status().isOk());
        mockMvc.perform(get("/api/adapters/signature-requests/{id}/signable-document", UUID.randomUUID())
                        .with(ingestion())).andExpect(status().isForbidden());
        mockMvc.perform(multipart(ROOT).file(jsonPart(metadata(
                                "SVC-MISSING-FILE-001", "HEALTHCARE", "LABORATORY_REPORT")))
                        .header("X-Source-System", "LIS-DEMO").with(ingestion()))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message", equalTo("Missing multipart part: file")));
    }

    private JsonNode submit(String metadata, byte[] pdf, String idempotency, String correlation,
                            int expectedStatus) throws Exception {
        MvcResult result = mockMvc.perform(multipart(ROOT).file(jsonPart(metadata)).file(pdfPart(pdf))
                        .header("X-Source-System", "LIS-DEMO")
                        .header("X-Idempotency-Key", idempotency)
                        .header("X-Correlation-ID", correlation).with(ingestion()))
                .andExpect(status().is(expectedStatus)).andReturn();
        return objectMapper.readTree(result.getResponse().getContentAsString());
    }

    private String metadata(String externalId, String type, String subtype) {
        return """
                {"externalRequestId":"%s","documentType":"%s","documentSubtype":"%s",
                 "signer":{"scheme":"IT_TAX_CODE","issuingCountry":"IT",
                 "issuer":"AGENZIA_ENTRATE","value":"DMSLGN80A01H501U"}}
                """.formatted(externalId, type, subtype);
    }

    private MockMultipartFile jsonPart(String value) {
        return new MockMultipartFile("request", "", MediaType.APPLICATION_JSON_VALUE,
                value.getBytes(StandardCharsets.UTF_8));
    }

    private MockMultipartFile pdfPart(byte[] value) {
        return new MockMultipartFile("file", "documento-fittizio.pdf", MediaType.APPLICATION_PDF_VALUE, value);
    }

    private org.springframework.test.web.servlet.ResultActions conservation(UUID requestId, String key, String body)
            throws Exception {
        return mockMvc.perform(post("/api/adapters/signature-requests/{id}/conservation-status", requestId)
                .contentType(MediaType.APPLICATION_JSON).content(body).header("X-Idempotency-Key", key)
                .with(token("demo.conservation-adapter", "CONSERVATION_ADAPTER"))).andExpect(status().isOk());
    }

    private byte[] smallPng() throws Exception {
        var image = new java.awt.image.BufferedImage(320, 240, java.awt.image.BufferedImage.TYPE_INT_RGB);
        var graphics = image.createGraphics(); graphics.setColor(java.awt.Color.WHITE);
        graphics.fillRect(0, 0, 320, 240); graphics.setColor(java.awt.Color.BLACK);
        graphics.drawString("Scansione totalmente fittizia", 40, 120); graphics.dispose();
        var output = new java.io.ByteArrayOutputStream(); javax.imageio.ImageIO.write(image, "png", output);
        return output.toByteArray();
    }

    private static RequestPostProcessor ingestion() { return token("demo.integration", "INGESTION"); }
    private static RequestPostProcessor token(String username, String role) {
        return jwt().jwt(value -> value.subject(username).claim("preferred_username", username)
                        .claim("realm_access", Map.of("roles", List.of(role))))
                .authorities(new SimpleGrantedAuthority("ROLE_" + role));
    }
    private static String minioEndpoint() { return "http://" + MINIO.getHost() + ":" + MINIO.getMappedPort(9000); }
    private static MinioClient minioClient() { return MinioClient.builder().endpoint(minioEndpoint())
            .credentials(ACCESS_KEY, SECRET_KEY).build(); }
}
