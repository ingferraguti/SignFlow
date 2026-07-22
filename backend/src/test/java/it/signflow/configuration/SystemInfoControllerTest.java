package it.signflow.configuration;

import static org.hamcrest.Matchers.equalTo;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Import;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.web.servlet.MockMvc;

@WebMvcTest(SystemInfoController.class)
@EnableConfigurationProperties(SystemInfoProperties.class)
@Import(SecurityConfiguration.class)
@TestPropertySource(properties = "signflow.system.version=unit-test")
class SystemInfoControllerTest {
    @Autowired
    private MockMvc mockMvc;

    @MockBean
    private JwtDecoder jwtDecoder;

    @Test
    void returnsSystemInfo() throws Exception {
        mockMvc.perform(get("/api/system/info").with(jwt()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.applicationName", equalTo("SignFlow")))
                .andExpect(jsonPath("$.version", equalTo("unit-test")))
                .andExpect(jsonPath("$.status", equalTo("UP")));
    }

    @Test
    void returnsSafeNotFoundResponseForUnknownApiPath() throws Exception {
        mockMvc.perform(get("/api/not-found").with(jwt()))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.status", equalTo(404)))
                .andExpect(jsonPath("$.message", equalTo("Resource not found")))
                .andExpect(jsonPath("$.path", equalTo("/api/not-found")));
    }
}
