package com.shr.cogniflow.config;

import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Contact;
import io.swagger.v3.oas.models.info.Info;
import io.swagger.v3.oas.models.info.License;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class OpenApiConfig {

    @Bean
    public OpenAPI cogniflowOpenAPI() {
        return new OpenAPI()
                .info(new Info()
                        .title("CogniFlow API")
                        .description("Intelligent market analysis and ingestion engine powered by Google Gemini AI and Weaviate.")
                        .version("v0.0.1")
                        .contact(new Contact()
                                .name("CogniFlow Team")
                                .url("https://github.com/AndDevil/CogniFlow"))
                        .license(new License()
                                .name("Internal Use")
                                .url("#")));
    }
}
