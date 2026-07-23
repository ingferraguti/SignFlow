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
