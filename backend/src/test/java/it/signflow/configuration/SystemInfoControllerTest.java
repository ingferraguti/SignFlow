package it.signflow.configuration;

import static org.hamcrest.Matchers.equalTo;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.web.servlet.MockMvc;

@WebMvcTest(SystemInfoController.class)
@TestPropertySource(properties = "signflow.system.version=unit-test")
class SystemInfoControllerTest {
    @Autowired
    private MockMvc mockMvc;

    @Test
    void returnsSystemInfo() throws Exception {
        mockMvc.perform(get("/api/system/info"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.applicationName", equalTo("SignFlow")))
                .andExpect(jsonPath("$.version", equalTo("unit-test")))
                .andExpect(jsonPath("$.status", equalTo("UP")));
    }

    @TestConfiguration
    @EnableConfigurationProperties(SystemInfoProperties.class)
    static class TestConfig {
        @Bean
        SystemInfoController controller(SystemInfoProperties properties) {
            return new SystemInfoController(properties);
        }
    }
}
