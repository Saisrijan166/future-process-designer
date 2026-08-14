package com.assesswise.processdesigner.config;

import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Contact;
import io.swagger.v3.oas.models.info.Info;
import io.swagger.v3.oas.models.info.License;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class OpenApiConfig {

    @Bean
    public OpenAPI processDesignerOpenApi() {
        return new OpenAPI()
                .info(new Info()
                        .title("AI Future Process Designer API")
                        .version("1.0.0")
                        .description("""
                                Analyses a current-state business process and designs an AI-enabled future \
                                state, stored as structured, queryable rows rather than prose.

                                The same pipeline runs for every process — seed data and processes created \
                                live at demo time take an identical code path.""")
                        .contact(new Contact().name("AssessWise — Modus ETI Enterprise AI Build Challenge"))
                        .license(new License().name("MIT")));
    }
}
