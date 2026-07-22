package it.signflow.identity;

import static org.hamcrest.Matchers.contains;
import static org.hamcrest.Matchers.equalTo;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.annotation.Import;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import it.signflow.configuration.SecurityConfiguration;
import org.springframework.test.web.servlet.MockMvc;

@WebMvcTest(CurrentUserController.class)
@Import(SecurityConfiguration.class)
class AuthorizationIntegrationTest {
    @Autowired
    private MockMvc mockMvc;

    @MockBean
    private JwtDecoder jwtDecoder;

    @Test
    void rejectsProtectedApiWithoutAuthentication() throws Exception {
        mockMvc.perform(get("/api/auth/me"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.status", equalTo(401)))
                .andExpect(jsonPath("$.message", equalTo("Authentication required")));
    }

    @Test
    void rejectsSignerFromAdministratorApi() throws Exception {
        mockMvc.perform(get("/api/admin/future")
                        .with(jwt().jwt(token -> token
                                .claim("preferred_username", "demo.signer")
                                .claim("realm_access", Map.of("roles", List.of("SIGNER"))))))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.status", equalTo(403)))
                .andExpect(jsonPath("$.message", equalTo("Access denied")));
    }

    @Test
    void exposesCurrentUserAndMappedRoles() throws Exception {
        mockMvc.perform(get("/api/auth/me")
                        .with(jwt().jwt(token -> token
                                .claim("preferred_username", "demo.admin")
                                .claim("name", "Local Demo Administrator")
                                .claim("email", "demo.admin@signflow.local")
                                .claim("realm_access", Map.of("roles", List.of("offline_access", "ADMINISTRATOR"))))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.username", equalTo("demo.admin")))
                .andExpect(jsonPath("$.name", equalTo("Local Demo Administrator")))
                .andExpect(jsonPath("$.email", equalTo("demo.admin@signflow.local")))
                .andExpect(jsonPath("$.roles", contains("ADMINISTRATOR")));
    }
}
