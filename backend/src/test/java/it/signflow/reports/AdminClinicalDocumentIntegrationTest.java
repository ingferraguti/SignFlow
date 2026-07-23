package it.signflow.reports;

import static org.hamcrest.Matchers.equalTo;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.minio.MinioClient;
import io.minio.StatObjectArgs;
import java.net.HttpURLConnection;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.HexFormat;
import java.util.UUID;
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
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.mock.web.MockMultipartFile;
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
class AdminClinicalDocumentIntegrationTest {
    private static final UUID REPORT_ID = UUID.fromString("cccccccc-cccc-cccc-cccc-ccccccccccc2");
    private static final String ROOT = "/api/admin/reports/" + REPORT_ID + "/documents";
    private static final String ACCESS_KEY = "test-access-key";
    private static final String SECRET_KEY = "test-secret-key";
    private static final String BUCKET = "test-clinical-documents";

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
        registry.add("signflow.documents.endpoint", AdminClinicalDocumentIntegrationTest::minioEndpoint);
        registry.add("signflow.documents.public-endpoint", AdminClinicalDocumentIntegrationTest::minioEndpoint);
        registry.add("signflow.documents.access-key", () -> ACCESS_KEY);
        registry.add("signflow.documents.secret-key", () -> SECRET_KEY);
        registry.add("signflow.documents.bucket", () -> BUCKET);
        registry.add("signflow.documents.max-size-bytes", () -> 1024);
        registry.add("signflow.documents.temporary-url-seconds", () -> 60);
        registry.add("signflow.documents.demo-enabled", () -> false);
    }

    @Autowired MockMvc mockMvc;
    @Autowired ObjectMapper objectMapper;
    @Autowired JdbcClient jdbcClient;

    @Test
    void uploadsHashesStoresDownloadsAndCreatesExpiringUrlInPrivateBucket() throws Exception {
        byte[] pdf = DemoClinicalDocumentInitializer.demoPdf();
        JsonNode uploaded = upload("referto-fittizio.pdf", MediaType.APPLICATION_PDF_VALUE, pdf);
        String id = uploaded.get("id").asText();
        String objectKey = uploaded.get("objectIdentifier").asText();
        String expectedHash = HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(pdf));

        org.junit.jupiter.api.Assertions.assertAll(
                () -> org.junit.jupiter.api.Assertions.assertEquals(expectedHash, uploaded.get("sha256").asText()),
                () -> org.junit.jupiter.api.Assertions.assertEquals(pdf.length, uploaded.get("sizeBytes").asLong()),
                () -> org.junit.jupiter.api.Assertions.assertEquals("application/pdf", uploaded.get("mimeType").asText()),
                () -> org.junit.jupiter.api.Assertions.assertEquals("demo.admin", uploaded.get("uploadedBy").asText()),
                () -> org.junit.jupiter.api.Assertions.assertFalse(uploaded.get("objectIdentifier").asText().contains("referto-fittizio")));

        MinioClient client = minioClient();
        org.junit.jupiter.api.Assertions.assertEquals(pdf.length,
                client.statObject(StatObjectArgs.builder().bucket(BUCKET).object(objectKey).build()).size());

        mockMvc.perform(get(ROOT + "/" + id + "/content?disposition=attachment").with(adminJwt()))
                .andExpect(status().isOk()).andExpect(content().contentType(MediaType.APPLICATION_PDF))
                .andExpect(header().string("X-Content-Type-Options", "nosniff"))
                .andExpect(header().string("Content-Disposition", org.hamcrest.Matchers.containsString("attachment")))
                .andExpect(content().bytes(pdf));

        String unsignedUrl = minioEndpoint() + "/" + BUCKET + "/" + objectKey;
        org.junit.jupiter.api.Assertions.assertEquals(HttpURLConnection.HTTP_FORBIDDEN, httpStatus(unsignedUrl));

        MvcResult urlResult = mockMvc.perform(get(ROOT + "/" + id + "/temporary-url").with(adminJwt()))
                .andExpect(status().isOk()).andExpect(jsonPath("$.expiresAt").exists()).andReturn();
        String signedUrl = objectMapper.readTree(urlResult.getResponse().getContentAsString()).get("url").asText();
        HttpResponse<byte[]> signedDownload = HttpClient.newHttpClient().send(
                HttpRequest.newBuilder(URI.create(signedUrl)).GET().build(), HttpResponse.BodyHandlers.ofByteArray());
        org.junit.jupiter.api.Assertions.assertEquals(200, signedDownload.statusCode());
        org.junit.jupiter.api.Assertions.assertArrayEquals(pdf, signedDownload.body());
    }

    @Test
    void versionsListsAndLogicallyDeletesWithoutRemovingStoredObject() throws Exception {
        JsonNode first = upload("allegato-a.pdf", MediaType.APPLICATION_PDF_VALUE, DemoClinicalDocumentInitializer.demoPdf());
        JsonNode second = upload("allegato-b.pdf", MediaType.APPLICATION_PDF_VALUE, DemoClinicalDocumentInitializer.demoPdf());
        org.junit.jupiter.api.Assertions.assertTrue(second.get("version").asInt() > first.get("version").asInt());

        String id = second.get("id").asText();
        String objectKey = second.get("objectIdentifier").asText();
        mockMvc.perform(delete(ROOT + "/" + id).with(adminJwt())).andExpect(status().isNoContent());
        mockMvc.perform(get(ROOT + "/" + id + "/content").with(adminJwt())).andExpect(status().isNotFound());
        mockMvc.perform(get(ROOT + "?includeDeleted=true").with(adminJwt()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[?(@.id == '" + id + "')].status", equalTo(java.util.List.of("DELETED"))))
                .andExpect(jsonPath("$[?(@.id == '" + id + "')].deletedBy", equalTo(java.util.List.of("demo.admin"))));
        org.junit.jupiter.api.Assertions.assertNotNull(
                minioClient().statObject(StatObjectArgs.builder().bucket(BUCKET).object(objectKey).build()));
    }

    @Test
    void rejectsFakePdfUnsafeNamesWrongMimeOversizeAndInvalidDisposition() throws Exception {
        MockMultipartFile fake = new MockMultipartFile("file", "fake.pdf", MediaType.APPLICATION_PDF_VALUE,
                "not a pdf".getBytes(StandardCharsets.UTF_8));
        mockMvc.perform(multipart(ROOT).file(fake).with(adminJwt())).andExpect(status().isBadRequest());

        MockMultipartFile unsafe = new MockMultipartFile("file", "../unsafe.pdf", MediaType.APPLICATION_PDF_VALUE,
                DemoClinicalDocumentInitializer.demoPdf());
        mockMvc.perform(multipart(ROOT).file(unsafe).with(adminJwt())).andExpect(status().isBadRequest());

        MockMultipartFile wrongMime = new MockMultipartFile("file", "wrong.pdf", MediaType.TEXT_PLAIN_VALUE,
                DemoClinicalDocumentInitializer.demoPdf());
        mockMvc.perform(multipart(ROOT).file(wrongMime).with(adminJwt())).andExpect(status().isBadRequest());

        byte[] oversized = new byte[1200];
        System.arraycopy("%PDF-1.4\n".getBytes(StandardCharsets.US_ASCII), 0, oversized, 0, 9);
        System.arraycopy("%%EOF".getBytes(StandardCharsets.US_ASCII), 0, oversized, oversized.length - 5, 5);
        MockMultipartFile large = new MockMultipartFile("file", "large.pdf", MediaType.APPLICATION_PDF_VALUE, oversized);
        mockMvc.perform(multipart(ROOT).file(large).with(adminJwt())).andExpect(status().isPayloadTooLarge());

        JsonNode valid = upload("validazione.pdf", MediaType.APPLICATION_PDF_VALUE, DemoClinicalDocumentInitializer.demoPdf());
        mockMvc.perform(get(ROOT + "/" + valid.get("id").asText() + "/content?disposition=unsafe").with(adminJwt()))
                .andExpect(status().isBadRequest());
    }

    @Test
    void blocksSignerAndAnonymousAccessAndKeepsBinaryOutOfPostgres() throws Exception {
        mockMvc.perform(get(ROOT).with(signerJwt())).andExpect(status().isForbidden());
        mockMvc.perform(get(ROOT)).andExpect(status().isUnauthorized());
        MockMultipartFile file = new MockMultipartFile("file", "blocked.pdf", MediaType.APPLICATION_PDF_VALUE,
                DemoClinicalDocumentInitializer.demoPdf());
        mockMvc.perform(multipart(ROOT).file(file).with(signerJwt())).andExpect(status().isForbidden());
        mockMvc.perform(delete(ROOT + "/00000000-0000-0000-0000-000000000001").with(signerJwt()))
                .andExpect(status().isForbidden());

        mockMvc.perform(get("/api/admin/reports/00000000-0000-0000-0000-000000000001/documents").with(adminJwt()))
                .andExpect(status().isNotFound());
        mockMvc.perform(get("/api/admin/reports/" + REPORT_ID).with(adminJwt()))
                .andExpect(status().isOk()).andExpect(jsonPath("$.id", equalTo(REPORT_ID.toString())));
        Integer binaryColumns = jdbcClient.sql("""
                select count(*) from information_schema.columns
                where table_name='clinical_documents' and data_type='bytea'
                """).query(Integer.class).single();
        org.junit.jupiter.api.Assertions.assertEquals(0, binaryColumns);
    }

    private JsonNode upload(String name, String mime, byte[] content) throws Exception {
        MockMultipartFile file = new MockMultipartFile("file", name, mime, content);
        MvcResult result = mockMvc.perform(multipart(ROOT).file(file).with(adminJwt()))
                .andExpect(status().isCreated()).andExpect(jsonPath("$.status", equalTo("ACTIVE"))).andReturn();
        return objectMapper.readTree(result.getResponse().getContentAsString());
    }

    private static String minioEndpoint() {
        return "http://" + MINIO.getHost() + ":" + MINIO.getMappedPort(9000);
    }

    private MinioClient minioClient() {
        return MinioClient.builder().endpoint(minioEndpoint()).credentials(ACCESS_KEY, SECRET_KEY).build();
    }

    private int httpStatus(String url) throws Exception {
        HttpResponse<Void> response = HttpClient.newHttpClient().send(
                HttpRequest.newBuilder(URI.create(url)).GET().build(), HttpResponse.BodyHandlers.discarding());
        return response.statusCode();
    }

    private RequestPostProcessor adminJwt() {
        return jwt().jwt(token -> token.claim("preferred_username", "demo.admin"))
                .authorities(new SimpleGrantedAuthority("ROLE_ADMINISTRATOR"));
    }

    private RequestPostProcessor signerJwt() {
        return jwt().jwt(token -> token.claim("preferred_username", "demo.signer"))
                .authorities(new SimpleGrantedAuthority("ROLE_SIGNER"));
    }
}
