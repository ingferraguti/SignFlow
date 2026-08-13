package it.signflow.identity;

import static org.hamcrest.Matchers.equalTo;
import static org.hamcrest.Matchers.greaterThanOrEqualTo;
import static org.hamcrest.Matchers.hasItem;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.util.List;
import java.util.Map;
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
import org.springframework.jdbc.core.simple.JdbcClient;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

@SpringBootTest
@AutoConfigureMockMvc
@Testcontainers
@ActiveProfiles("test")
class AdminApplicationUserIntegrationTest {
    @Container
    static final PostgreSQLContainer<?> POSTGRES = new PostgreSQLContainer<>("postgres:16-alpine");

    @DynamicPropertySource
    static void postgresProperties(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", POSTGRES::getJdbcUrl);
        registry.add("spring.datasource.username", POSTGRES::getUsername);
        registry.add("spring.datasource.password", POSTGRES::getPassword);
    }

    @Autowired
    private MockMvc mockMvc;
    @Autowired private ObjectMapper objectMapper;
    @Autowired private JdbcClient jdbc;

    @Test
    void administratorCanSearchDemoUsersAndOrganizationOptions() throws Exception {
        mockMvc.perform(get("/api/admin/users").with(adminJwt()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.total", greaterThanOrEqualTo(2)))
                .andExpect(jsonPath("$.items[*].username", hasItem("demo.admin")))
                .andExpect(jsonPath("$.items[*].username", hasItem("demo.signer")));

        mockMvc.perform(get("/api/admin/organization/roles").with(adminJwt()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[*].code", hasItem("ADMINISTRATOR")))
                .andExpect(jsonPath("$[*].code", hasItem("SIGNER")));
    }

    @Test
    void signerCannotAccessAdministrativeUsers() throws Exception {
        mockMvc.perform(get("/api/admin/users").with(signerJwt()))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.status", equalTo(403)));

        mockMvc.perform(get("/api/admin/organization/manage/groups").with(signerJwt()))
                .andExpect(status().isForbidden());

        mockMvc.perform(get("/api/admin/ui-texts").with(signerJwt()))
                .andExpect(status().isForbidden());
    }

    @Test
    void administratorCanManagePartitionsCompaniesAndGroups() throws Exception {
        String partitionBody = """
                {"code":"TEST-PART","name":"Test partition","active":true}
                """;
        String partition = mockMvc.perform(post("/api/admin/organization/manage/partitions")
                        .with(adminJwt()).contentType(MediaType.APPLICATION_JSON).content(partitionBody))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code", equalTo("TEST-PART")))
                .andReturn().getResponse().getContentAsString();
        String partitionId = partition.replaceAll("(?s).*?\"id\":\"([^\"]+)\".*", "$1");

        String companyBody = """
                {"code":"TEST-COMP","name":"Test company","partitionId":"%s","active":true}
                """.formatted(partitionId);
        mockMvc.perform(post("/api/admin/organization/manage/companies")
                        .with(adminJwt()).contentType(MediaType.APPLICATION_JSON).content(companyBody))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.partitionId", equalTo(partitionId)));

        String groupBody = """
                {"code":"TEST-GROUP","name":"Test group","partitionId":"%s","active":true}
                """.formatted(partitionId);
        String group = mockMvc.perform(post("/api/admin/organization/manage/groups")
                        .with(adminJwt()).contentType(MediaType.APPLICATION_JSON).content(groupBody))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();
        String groupId = group.replaceAll("(?s).*?\"id\":\"([^\"]+)\".*", "$1");

        mockMvc.perform(put("/api/admin/organization/manage/groups/" + groupId)
                        .with(adminJwt()).contentType(MediaType.APPLICATION_JSON)
                        .content(groupBody.replace("Test group", "Updated group")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.name", equalTo("Updated group")));
        mockMvc.perform(post("/api/admin/organization/manage/groups/" + groupId + "/deactivate").with(adminJwt()))
                .andExpect(status().isOk()).andExpect(jsonPath("$.active", equalTo(false)));
    }

    @Test
    void administratorCanConfigureMenuAndButtonTexts() throws Exception {
        mockMvc.perform(put("/api/admin/ui-texts").with(adminJwt())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"menu.configuration\":\"Amministrazione\",\"button.search\":\"Trova\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$['menu.configuration']", equalTo("Amministrazione")))
                .andExpect(jsonPath("$['button.search']", equalTo("Trova")));

        mockMvc.perform(put("/api/admin/ui-texts").with(adminJwt())
                        .contentType(MediaType.APPLICATION_JSON).content("{\"unknown.key\":\"value\"}"))
                .andExpect(status().isBadRequest());
    }

    @Test
    void organizationManagementValidatesInput() throws Exception {
        mockMvc.perform(post("/api/admin/organization/manage/groups").with(adminJwt())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"code\":\"invalid code\",\"name\":\"\",\"active\":true}"))
                .andExpect(status().isBadRequest());
    }

    @Test
    void administratorCanCreateUpdateAndDeactivateApplicationUserWithoutPassword() throws Exception {
        String body = """
                {
                  "username": "demo.created",
                  "oidcSubject": "demo.created",
                  "firstName": "Created",
                  "lastName": "User",
                  "email": "demo.created@signflow.local",
                  "fiscalCode": "DMCREA80A01H501U",
                  "signerFiscalCode": "DMCREA80A01H501U",
                  "counterSignerFiscalCode": "",
                  "active": true,
                  "partitionId": "11111111-1111-1111-1111-111111111111",
                  "companyId": "22222222-2222-2222-2222-222222222221",
                  "roleIds": ["33333333-3333-3333-3333-333333333332"],
                  "groupIds": ["44444444-4444-4444-4444-444444444442"]
                }
                """;

        String created = mockMvc.perform(post("/api/admin/users")
                        .with(adminJwt())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.username", equalTo("demo.created")))
                .andExpect(jsonPath("$.roles[0].code", equalTo("SIGNER")))
                .andReturn().getResponse().getContentAsString();

        String id = created.replaceAll("(?s).*?\"id\":\"([^\"]+)\".*", "$1");
        String updatedBody = body.replace("Created", "Updated").replace("\"active\": true", "\"active\": false");

        mockMvc.perform(put("/api/admin/users/" + id)
                        .with(adminJwt())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(updatedBody))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.firstName", equalTo("Updated")))
                .andExpect(jsonPath("$.active", equalTo(false)));

        mockMvc.perform(post("/api/admin/users/" + id + "/activate").with(adminJwt()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.active", equalTo(true)));

        mockMvc.perform(post("/api/admin/users/" + id + "/deactivate").with(adminJwt()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.active", equalTo(false)));
    }

    @Test
    void multipleAuthenticationProfilesLinkToOneEuropeanNaturalPersonAndCorrectionsRequireReason() throws Exception {
        String template = """
                {"username":"%s","oidcSubject":"%s","firstName":"Persona","lastName":"Fittizia",
                 "email":"%s@signflow.invalid","identifierScheme":"EIDAS_PERSON_IDENTIFIER",
                 "issuingCountry":"DE","identifierIssuer":"DEMO_EIDAS_NODE",
                 "personalIdentifier":"DE/IT/FICTIONAL-IDENTITY-009",
                 "authenticationIssuer":"%s","authenticationMethod":"%s","active":true,
                 "partitionId":"11111111-1111-1111-1111-111111111111",
                 "companyId":"22222222-2222-2222-2222-222222222221",
                 "roleIds":["33333333-3333-3333-3333-333333333332"],"groupIds":[]}
                """;
        String firstBody = template.formatted("fictional.eu.oidc", "eu-subject-oidc", "eu.oidc",
                "https://issuer-one.invalid", "OIDC");
        String secondBody = template.formatted("fictional.eu.ldap", "eu-subject-oidc", "eu.ldap",
                "ldap://directory-two.invalid", "LDAP");
        String first = mockMvc.perform(post("/api/admin/users").with(adminJwt())
                        .contentType(MediaType.APPLICATION_JSON).content(firstBody))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsString();
        String second = mockMvc.perform(post("/api/admin/users").with(adminJwt())
                        .contentType(MediaType.APPLICATION_JSON).content(secondBody))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsString();
        org.junit.jupiter.api.Assertions.assertEquals(objectMapper.readTree(first).path("naturalPersonId").asText(),
                objectMapper.readTree(second).path("naturalPersonId").asText());

        String secondId = objectMapper.readTree(second).path("id").asText();
        String correction = secondBody.replace("FICTIONAL-IDENTITY-009", "FICTIONAL-IDENTITY-010");
        mockMvc.perform(put("/api/admin/users/" + secondId).with(adminJwt())
                        .contentType(MediaType.APPLICATION_JSON).content(correction))
                .andExpect(status().isBadRequest());
        mockMvc.perform(put("/api/admin/users/" + secondId).with(adminJwt())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(correction.replace("\"active\":true",
                                "\"identityCorrectionReason\":\"Rettifica amministrativa fittizia verificata\",\"active\":true")))
                .andExpect(status().isOk());
        org.junit.jupiter.api.Assertions.assertEquals(1, jdbc.sql("""
                select count(*) from natural_person_identity_events
                where application_user_id=:id and event_type='IDENTITY_CORRECTED'
                """).param("id", java.util.UUID.fromString(secondId)).query(Integer.class).single());
    }

    private static org.springframework.test.web.servlet.request.RequestPostProcessor adminJwt() {
        return jwt().jwt(token -> token
                .claim("preferred_username", "demo.admin")
                .claim("realm_access", Map.of("roles", List.of("ADMINISTRATOR"))))
                .authorities(new SimpleGrantedAuthority("ROLE_ADMINISTRATOR"));
    }

    private static org.springframework.test.web.servlet.request.RequestPostProcessor signerJwt() {
        return jwt().jwt(token -> token
                .claim("preferred_username", "demo.signer")
                .claim("realm_access", Map.of("roles", List.of("SIGNER"))))
                .authorities(new SimpleGrantedAuthority("ROLE_SIGNER"));
    }
}
