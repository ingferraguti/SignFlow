package it.signflow.reports;

import static org.hamcrest.Matchers.containsInAnyOrder;
import static org.hamcrest.Matchers.equalTo;
import static org.hamcrest.Matchers.hasSize;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

@SpringBootTest
@AutoConfigureMockMvc
@Testcontainers
@ActiveProfiles("test")
class AdminReportIntegrationTest {
    private static final String ROOT = "/api/admin/reports";

    @Container
    static final PostgreSQLContainer<?> POSTGRES = new PostgreSQLContainer<>("postgres:16-alpine");

    @DynamicPropertySource
    static void postgresProperties(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", POSTGRES::getJdbcUrl);
        registry.add("spring.datasource.username", POSTGRES::getUsername);
        registry.add("spring.datasource.password", POSTGRES::getPassword);
    }

    @Autowired MockMvc mockMvc;

    @Test
    void supportsEveryExactIdentifierAndAppliesDocumentedPrecedence() throws Exception {
        expectOnly("?internalIdentifier=RPT-INT-001", "RPT-INT-001");
        expectOnly("?externalIdentifier=RPT-EXT-002", "RPT-INT-002");
        expectOnly("?fseIdentifier=FSE-DEMO-003", "RPT-INT-003");

        expectOnly("?internalIdentifier=RPT-INT-001&externalIdentifier=RPT-EXT-002&state=INCOMPLETE", "RPT-INT-001");
        expectOnly("?externalIdentifier=RPT-EXT-002&fseIdentifier=FSE-DEMO-003&department=Medicina", "RPT-INT-002");
    }

    @Test
    void supportsPatientSignerStateSourceSystemAndDepartmentFilters() throws Exception {
        expectIdentifiers("?patient=Bruno", "RPT-INT-003", "RPT-INT-004", "RPT-MOCK-FAIL-001", "RPT-MOCK-OK-002",
                "RPT-FSE-MOCK-OK-001", "RPT-FSE-MOCK-RETRY-001");
        expectIdentifiers("?patient=PAT-DEMO-001", "RPT-INT-001", "RPT-INT-002", "RPT-MOCK-OK-001", "RPT-MOCK-RETRY-001");
        expectIdentifiers("?signer=demo.signer", "RPT-INT-001", "RPT-INT-002", "RPT-INT-004",
                "RPT-MOCK-OK-001", "RPT-MOCK-RETRY-001", "RPT-MOCK-FAIL-001", "RPT-MOCK-OK-002",
                "RPT-FSE-MOCK-OK-001", "RPT-FSE-MOCK-RETRY-001");
        expectIdentifiers("?signerFiscalCode=DMSLGN80A01H501U", "RPT-INT-001", "RPT-INT-002", "RPT-INT-004",
                "RPT-MOCK-OK-001", "RPT-MOCK-RETRY-001", "RPT-MOCK-FAIL-001", "RPT-MOCK-OK-002",
                "RPT-FSE-MOCK-OK-001", "RPT-FSE-MOCK-RETRY-001");
        expectOnly("?state=MISSING_SIGNER", "RPT-INT-003");
        expectIdentifiers("?sourceSystemId=66666666-6666-6666-6666-666666666661",
                "RPT-INT-001", "RPT-INT-002", "RPT-INT-003", "RPT-INT-004", "RPT-INT-005", "RPT-INT-006",
                "RPT-MOCK-OK-001", "RPT-MOCK-RETRY-001", "RPT-MOCK-FAIL-001", "RPT-MOCK-OK-002",
                "RPT-FSE-MOCK-OK-001", "RPT-FSE-MOCK-RETRY-001");
        expectIdentifiers("?department=Cardiologia", "RPT-INT-001", "RPT-INT-002");
    }

    @Test
    void supportsProductionModificationAndSignatureDateIntervals() throws Exception {
        expectIdentifiers("?producedFrom=2026-07-05&producedTo=2026-07-10", "RPT-INT-002", "RPT-INT-003");
        expectOnly("?modifiedFrom=2026-07-15&modifiedTo=2026-07-15", "RPT-INT-004");
        expectOnly("?signedFrom=2026-07-01&signedTo=2026-07-01", "RPT-INT-001");
    }

    @Test
    void returnsPaginatedSearchAndCompleteReportDetail() throws Exception {
        mockMvc.perform(get(ROOT + "?page=0&size=2&sortBy=internalIdentifier&direction=asc").with(adminJwt()))
                .andExpect(status().isOk()).andExpect(jsonPath("$.total", equalTo(12)))
                .andExpect(jsonPath("$.items", hasSize(2)))
                .andExpect(jsonPath("$.items[0].internalIdentifier", equalTo("RPT-FSE-MOCK-OK-001")));

        mockMvc.perform(get(ROOT + "/cccccccc-cccc-cccc-cccc-ccccccccccc1").with(adminJwt()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.internalIdentifier", equalTo("RPT-INT-001")))
                .andExpect(jsonPath("$.practice.practiceIdentifier", equalTo("PRACTICE-DEMO-001")))
                .andExpect(jsonPath("$.patient.patientIdentifier", equalTo("PAT-DEMO-001")))
                .andExpect(jsonPath("$.signerUsername", equalTo("demo.signer")))
                .andExpect(jsonPath("$.sourceSystemCode", equalTo("LIS-DEMO")))
                .andExpect(jsonPath("$.pdfA3Conversion", equalTo(true)))
                .andExpect(jsonPath("$.state", equalTo("SIGNED")));
    }

    @Test
    void validatesPagingOrderingStateDatesAndParameterTypes() throws Exception {
        for (String query : List.of(
                "?page=-1", "?size=101", "?sortBy=unknown", "?direction=sideways", "?state=UNKNOWN",
                "?producedFrom=2026-07-10&producedTo=2026-07-01", "?sourceSystemId=not-a-uuid")) {
            mockMvc.perform(get(ROOT + query).with(adminJwt())).andExpect(status().isBadRequest());
        }
    }

    @Test
    void exposesEveryRequiredStateAndRejectsSignerAccess() throws Exception {
        Set<String> states = Arrays.stream(ReportState.values()).map(Enum::name).collect(Collectors.toSet());
        org.assertj.core.api.Assertions.assertThat(states).contains(
                "RECEIVED", "PARSED", "INCOMPLETE", "MISSING_SIGNER", "READY_TO_SIGN", "PREVIEWED",
                "REVIEW_PENDING", "APPROVED", "SIGNING", "SIGNED", "SIGN_ERROR", "FSE_SENT",
                "FSE_ACCEPTED", "FSE_REJECTED", "CONSERVATION_SENT", "CONSERVATION_ACCEPTED",
                "CONSERVATION_REJECTED");

        mockMvc.perform(get(ROOT).with(signerJwt())).andExpect(status().isForbidden())
                .andExpect(jsonPath("$.status", equalTo(403)));
        mockMvc.perform(get(ROOT + "/cccccccc-cccc-cccc-cccc-ccccccccccc1").with(signerJwt()))
                .andExpect(status().isForbidden());
    }

    private void expectOnly(String query, String identifier) throws Exception {
        mockMvc.perform(get(ROOT + query).with(adminJwt())).andExpect(status().isOk())
                .andExpect(jsonPath("$.total", equalTo(1)))
                .andExpect(jsonPath("$.items[0].internalIdentifier", equalTo(identifier)));
    }

    private void expectIdentifiers(String query, String... identifiers) throws Exception {
        mockMvc.perform(get(ROOT + query).with(adminJwt())).andExpect(status().isOk())
                .andExpect(jsonPath("$.total", equalTo(identifiers.length)))
                .andExpect(jsonPath("$.items[*].internalIdentifier", containsInAnyOrder(identifiers)));
    }

    private static org.springframework.test.web.servlet.request.RequestPostProcessor adminJwt() {
        return jwt().jwt(token -> token.claim("preferred_username", "demo.admin")
                .claim("realm_access", Map.of("roles", List.of("ADMINISTRATOR"))))
                .authorities(new SimpleGrantedAuthority("ROLE_ADMINISTRATOR"));
    }

    private static org.springframework.test.web.servlet.request.RequestPostProcessor signerJwt() {
        return jwt().jwt(token -> token.claim("preferred_username", "demo.signer")
                .claim("realm_access", Map.of("roles", List.of("SIGNER"))))
                .authorities(new SimpleGrantedAuthority("ROLE_SIGNER"));
    }
}
