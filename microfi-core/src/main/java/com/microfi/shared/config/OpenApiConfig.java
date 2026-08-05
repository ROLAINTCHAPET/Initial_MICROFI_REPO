package com.microfi.shared.config;

import io.swagger.v3.oas.models.Components;
import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Info;
import io.swagger.v3.oas.models.security.SecurityRequirement;
import io.swagger.v3.oas.models.security.SecurityScheme;
import org.springdoc.core.utils.SpringDocUtils;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.core.Authentication;
import reactor.core.publisher.Mono;

@Configuration
public class OpenApiConfig {

    static {
        // Controllers take Mono<Authentication> to read the JWT-resolved caller (agent/admin/client)
        // via Spring Security's reactive context — Spring injects this itself, a client never sends
        // it. Without these, springdoc can't map the generic Mono<Authentication> to a known type and
        // renders it as a bogus "authenticationMono: any" request parameter.
        SpringDocUtils.getConfig().addRequestWrapperToIgnore(Mono.class);
        SpringDocUtils.getConfig().addJavaTypeToIgnore(Authentication.class);
    }

    @Bean
    public OpenAPI customOpenAPI() {
        final String securitySchemeName = "bearerAuth";
        return new OpenAPI()
                .info(new Info().title("MICROFI Core API")
                        .description("API Documentation for MICROFI Core Backend Modules.")
                        .version("v1.0.0"))
                .addSecurityItem(new SecurityRequirement().addList(securitySchemeName))
                .components(
                        new Components()
                                .addSecuritySchemes(securitySchemeName,
                                        new SecurityScheme()
                                                .name(securitySchemeName)
                                                .type(SecurityScheme.Type.HTTP)
                                                .scheme("bearer")
                                                .bearerFormat("JWT")
                                )
                );
    }
}
