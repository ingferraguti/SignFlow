package it.signflow.configuration;

import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Info;
import it.signflow.audit.AuditWebInterceptor;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.config.annotation.CorsRegistry;
import org.springframework.web.servlet.config.annotation.InterceptorRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

@Configuration
@EnableConfigurationProperties({SignFlowProperties.class, SystemInfoProperties.class})
public class ApplicationConfiguration {
    @Bean
    OpenAPI signFlowOpenApi(SystemInfoProperties properties) {
        return new OpenAPI().info(new Info().title("SignFlow API").version(properties.version()).description("Technical foundation API for SignFlow."));
    }

    @Bean
    WebMvcConfigurer corsConfigurer(SignFlowProperties properties, ObjectProvider<AuditWebInterceptor> auditInterceptor) {
        return new WebMvcConfigurer() {
            @Override
            public void addCorsMappings(CorsRegistry registry) {
                registry.addMapping("/api/**")
                        .allowedOrigins(properties.corsAllowedOrigins().toArray(String[]::new))
                        .allowedMethods("GET", "POST", "PUT", "PATCH", "DELETE", "OPTIONS")
                        .allowedHeaders("*");
            }

            @Override
            public void addInterceptors(InterceptorRegistry registry) {
                auditInterceptor.ifAvailable(interceptor -> registry.addInterceptor(interceptor).addPathPatterns("/api/**"));
            }
        };
    }
}
