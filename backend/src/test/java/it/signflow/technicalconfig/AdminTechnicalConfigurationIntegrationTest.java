package it.signflow.technicalconfig;

import static org.hamcrest.Matchers.equalTo;
import static org.hamcrest.Matchers.hasItem;
import static org.hamcrest.Matchers.not;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
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
import org.springframework.jdbc.core.simple.JdbcClient;
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
class AdminTechnicalConfigurationIntegrationTest {
    private static final String ROOT = "/api/admin/technical-config";

    @Container
    static final PostgreSQLContainer<?> POSTGRES = new PostgreSQLContainer<>("postgres:16-alpine");

    @DynamicPropertySource
    static void postgresProperties(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", POSTGRES::getJdbcUrl);
        registry.add("spring.datasource.username", POSTGRES::getUsername);
        registry.add("spring.datasource.password", POSTGRES::getPassword);
    }

    @Autowired MockMvc mockMvc;
    @Autowired JdbcClient jdbcClient;

    @Test
    void demoTechnicalConfigurationsAreAvailableAndContainNoPasswordStorage() throws Exception {
        mockMvc.perform(get(ROOT + "/source-systems").with(adminJwt())).andExpect(status().isOk())
                .andExpect(jsonPath("$[*].code", hasItem("LIS-DEMO")));
        mockMvc.perform(get(ROOT + "/signature-providers").with(adminJwt())).andExpect(status().isOk())
                .andExpect(jsonPath("$[*].code", hasItem("MOCK-REMOTE")))
                .andExpect(jsonPath("$[0].password").doesNotExist());
        mockMvc.perform(get(ROOT + "/signature-accounts").with(adminJwt())).andExpect(status().isOk())
                .andExpect(jsonPath("$[*].accountAlias", hasItem("demo-signer")));
        mockMvc.perform(get(ROOT + "/fse-facility-mappings").with(adminJwt())).andExpect(status().isOk())
                .andExpect(jsonPath("$[*].facilityCode", hasItem("PRESIDIO-DEMO")));
        mockMvc.perform(get(ROOT + "/fse-document-types").with(adminJwt())).andExpect(status().isOk())
                .andExpect(jsonPath("$[*].code", hasItem("REF")))
                .andExpect(jsonPath("$[*].code", hasItem("LDO")))
                .andExpect(jsonPath("$[?(@.code == 'REF')].approvalRequired", hasItem(true)))
                .andExpect(jsonPath("$[?(@.code == 'REF')].previewRequired", hasItem(true)));

        Integer passwordColumns = jdbcClient.sql("""
                select count(*) from information_schema.columns
                where table_name in ('signature_providers', 'signature_accounts')
                  and column_name like '%password%'
                """).query(Integer.class).single();
        org.assertj.core.api.Assertions.assertThat(passwordColumns).isZero();
    }

    @Test
    void signerReceivesForbiddenOnEveryTechnicalAdministrationResource() throws Exception {
        for (String resource : List.of("source-systems", "signature-providers", "signature-accounts", "fse-facility-mappings")) {
            mockMvc.perform(get(ROOT + "/" + resource).with(signerJwt()))
                    .andExpect(status().isForbidden()).andExpect(jsonPath("$.status", equalTo(403)));
        }
    }

    @Test
    void rejectsIncoherentSourcePipelineAndInvalidProviderAuthentication() throws Exception {
        mockMvc.perform(post(ROOT + "/source-systems").with(adminJwt()).contentType(MediaType.APPLICATION_JSON)
                        .content(sourceSystemBody("BAD-PIPELINE", true, true)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message", equalTo("createCda and passthrough cannot both be true")));

        mockMvc.perform(post(ROOT + "/signature-providers").with(adminJwt()).contentType(MediaType.APPLICATION_JSON)
                        .content(providerBody("BAD-AUTH").replace("\"OTP\"", "\"PASSWORD\"")))
                .andExpect(status().isBadRequest());

        mockMvc.perform(post(ROOT + "/signature-providers").with(adminJwt()).contentType(MediaType.APPLICATION_JSON)
                        .content(providerBody("BAD-URL").replace("http://provider.test", "javascript:alert(1)")))
                .andExpect(status().isBadRequest());

        mockMvc.perform(post(ROOT + "/signature-providers").with(adminJwt()).contentType(MediaType.APPLICATION_JSON)
                        .content(providerBody("NO-SECRET-REF").replace("\"OTP\"", "\"API_KEY_REFERENCE\"")
                                .replace("secret://providers/crud", "")))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message", equalTo("credentialReference is required for API_KEY_REFERENCE; secrets must remain external")));
    }

    @Test
    void configuresCdaInjectionOnlyForSelectedFseDocumentTypes() throws Exception {
        String sourceId = create(ROOT + "/source-systems", sourceSystemBody("CDA-BY-TYPE", true, false));
        String configuration = """
                [{"documentTypeCode":"REF","cdaInjectionEnabled":true},
                 {"documentTypeCode":"LDO","cdaInjectionEnabled":false}]
                """;
        mockMvc.perform(put(ROOT + "/source-systems/" + sourceId + "/fse-document-types")
                        .with(adminJwt()).contentType(MediaType.APPLICATION_JSON).content(configuration))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[?(@.documentTypeCode == 'REF')].cdaInjectionEnabled", hasItem(true)))
                .andExpect(jsonPath("$[?(@.documentTypeCode == 'LDO')].cdaInjectionEnabled", hasItem(false)));

        mockMvc.perform(put(ROOT + "/source-systems/" + sourceId + "/fse-document-types")
                        .with(adminJwt()).contentType(MediaType.APPLICATION_JSON)
                        .content("[{\"documentTypeCode\":\"XYZ\",\"cdaInjectionEnabled\":true}]"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message", equalTo("unsupported FSE document type: XYZ")));
        mockMvc.perform(delete(ROOT + "/source-systems/" + sourceId).with(adminJwt()))
                .andExpect(status().isNoContent());
    }

    @Test
    void configuresSignaturePolicyForAnFseDocumentType() throws Exception {
        mockMvc.perform(put(ROOT + "/fse-document-types/REF/signature-policy").with(adminJwt())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"approvalRequired\":false,\"previewRequired\":false}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code", equalTo("REF")))
                .andExpect(jsonPath("$.approvalRequired", equalTo(false)))
                .andExpect(jsonPath("$.previewRequired", equalTo(false)));
        mockMvc.perform(put(ROOT + "/fse-document-types/XYZ/signature-policy").with(adminJwt())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"approvalRequired\":false,\"previewRequired\":false}"))
                .andExpect(status().isNotFound());
    }

    @Test
    void rejectsNonSignerAccountsAndCrossCompanyFseMappings() throws Exception {
        String adminAccount = """
                {"applicationUserId":"55555555-5555-5555-5555-555555555551",
                 "signatureProviderId":"77777777-7777-7777-7777-777777777771","accountAlias":"admin-account",
                 "providerUsername":"demo.admin","certificateAlias":"CERT-ADMIN","active":true}
                """;
        mockMvc.perform(post(ROOT + "/signature-accounts").with(adminJwt()).contentType(MediaType.APPLICATION_JSON)
                        .content(adminAccount))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message", equalTo("signature accounts can only be assigned to users with the SIGNER role")));

        String crossCompanyMapping = """
                {"facilityCode":"CROSS-COMPANY","facilityName":"Cross company",
                 "companyId":"22222222-2222-2222-2222-222222222222","operatingUnit":"UO Test",
                 "department":"Test","sourceSystemId":"66666666-6666-6666-6666-666666666661","active":true}
                """;
        mockMvc.perform(post(ROOT + "/fse-facility-mappings").with(adminJwt()).contentType(MediaType.APPLICATION_JSON)
                        .content(crossCompanyMapping))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message", equalTo("sourceSystemId does not belong to companyId")));
    }

    @Test
    void administratorCanCrudAllTechnicalConfigurations() throws Exception {
        String sourceId = create(ROOT + "/source-systems", sourceSystemBody("CRUD-SOURCE", true, false));
        mockMvc.perform(put(ROOT + "/source-systems/" + sourceId).with(adminJwt()).contentType(MediaType.APPLICATION_JSON)
                        .content(sourceSystemBody("CRUD-SOURCE", false, true).replace("CRUD source", "Updated source")))
                .andExpect(status().isOk()).andExpect(jsonPath("$.description", equalTo("Updated source")))
                .andExpect(jsonPath("$.passthrough", equalTo(true)));

        String providerId = create(ROOT + "/signature-providers", providerBody("CRUD-PROVIDER"));
        mockMvc.perform(put(ROOT + "/signature-providers/" + providerId).with(adminJwt()).contentType(MediaType.APPLICATION_JSON)
                        .content(providerBody("CRUD-PROVIDER").replace("CRUD provider", "Updated provider")))
                .andExpect(status().isOk()).andExpect(jsonPath("$.name", equalTo("Updated provider")));

        String accountBody = """
                {"applicationUserId":"55555555-5555-5555-5555-555555555552",
                 "signatureProviderId":"%s","accountAlias":"crud-account",
                 "providerUsername":"external.signer","certificateAlias":"CERT-CRUD","active":true}
                """.formatted(providerId);
        String accountId = create(ROOT + "/signature-accounts", accountBody);
        mockMvc.perform(put(ROOT + "/signature-accounts/" + accountId).with(adminJwt()).contentType(MediaType.APPLICATION_JSON)
                        .content(accountBody.replace("CERT-CRUD", "CERT-UPDATED")))
                .andExpect(status().isOk()).andExpect(jsonPath("$.certificateAlias", equalTo("CERT-UPDATED")));

        String mappingBody = """
                {"facilityCode":"CRUD-FACILITY","facilityName":"CRUD facility",
                 "companyId":"22222222-2222-2222-2222-222222222221","operatingUnit":"UO CRUD",
                 "department":"CRUD department","sourceSystemId":"%s","active":true}
                """.formatted(sourceId);
        String mappingId = create(ROOT + "/fse-facility-mappings", mappingBody);
        mockMvc.perform(put(ROOT + "/fse-facility-mappings/" + mappingId).with(adminJwt()).contentType(MediaType.APPLICATION_JSON)
                        .content(mappingBody.replace("CRUD department", "Updated department")))
                .andExpect(status().isOk()).andExpect(jsonPath("$.department", equalTo("Updated department")));

        mockMvc.perform(delete(ROOT + "/fse-facility-mappings/" + mappingId).with(adminJwt())).andExpect(status().isNoContent());
        mockMvc.perform(delete(ROOT + "/signature-accounts/" + accountId).with(adminJwt())).andExpect(status().isNoContent());
        mockMvc.perform(delete(ROOT + "/signature-providers/" + providerId).with(adminJwt())).andExpect(status().isNoContent());
        mockMvc.perform(delete(ROOT + "/source-systems/" + sourceId).with(adminJwt())).andExpect(status().isNoContent());
        mockMvc.perform(get(ROOT + "/source-systems/" + sourceId).with(adminJwt())).andExpect(status().isNotFound());
    }

    private String create(String path, String body) throws Exception {
        String response = mockMvc.perform(post(path).with(adminJwt()).contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isOk()).andExpect(jsonPath("$.id", not("")))
                .andReturn().getResponse().getContentAsString();
        return response.replaceAll("(?s).*?\"id\":\"([^\"]+)\".*", "$1");
    }

    private String sourceSystemBody(String code, boolean createCda, boolean passthrough) {
        return """
                {"code":"%s","companyId":"22222222-2222-2222-2222-222222222221",
                 "description":"CRUD source","active":true,"cdaType":"CDA2-REF",
                 "pdfA3Conversion":true,"visibleSignature":true,"multipleSignature":false,
                 "sendUnsigned":false,"createCda":%s,"passthrough":%s}
                """.formatted(code, createCda, passthrough);
    }

    private String providerBody(String code) {
        return """
                {"code":"%s","name":"CRUD provider","adapterType":"REST",
                 "baseUrl":"http://provider.test","authenticationMode":"OTP",
                 "credentialReference":"secret://providers/crud","supportsVisibleSignature":true,
                 "supportsMultipleSignature":true,"active":true}
                """.formatted(code);
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
