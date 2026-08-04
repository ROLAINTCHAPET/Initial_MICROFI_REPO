package com.microfi.mw.infrastructure;

import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Info;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class OpenApiConfig {

    @Bean
    public OpenAPI customOpenAPI() {
        return new OpenAPI()
                .info(new Info().title("MICROFI CBS Middleware API")
                        .description("Internal API exposed to the Core Backend only. Vendor-specific CBS mapping and outbound calls.")
                        .version("v1.0.0"));
    }
}
