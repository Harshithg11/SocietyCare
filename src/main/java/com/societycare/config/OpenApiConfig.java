package com.societycare.config;

import io.swagger.v3.oas.models.Components;
import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Info;
import io.swagger.v3.oas.models.security.SecurityScheme;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class OpenApiConfig {

    public static final String BEARER_SCHEME = "bearerAuth";

    @Bean
    public OpenAPI societyCareOpenApi() {
        return new OpenAPI()
                .info(new Info()
                        .title("SocietyCare API")
                        .version("1.0.0")
                        .description("Gated-community home-service group booking platform. Residents join campaigns; "
                                + "when the minimum participant count is reached the campaign is automatically "
                                + "CONFIRMED. Log in with /api/auth/login, click 'Authorize' and paste the token."))
                .components(new Components().addSecuritySchemes(BEARER_SCHEME,
                        new SecurityScheme()
                                .name(BEARER_SCHEME)
                                .type(SecurityScheme.Type.HTTP)
                                .scheme("bearer")
                                .bearerFormat("JWT")));
    }
}
