package com.assesswise.processdesigner;

import static org.assertj.core.api.Assertions.assertThat;

import com.assesswise.processdesigner.service.AnalysisService;
import com.assesswise.processdesigner.service.PromptBuilder;
import com.assesswise.processdesigner.support.AbstractIntegrationTest;
import javax.sql.DataSource;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.jdbc.core.JdbcTemplate;

/**
 * Boot-level guarantees. This is the test that catches schema drift: {@code ddl-auto: validate}
 * means the context only starts if every JPA mapping matches the tables Flyway created.
 */
class ApplicationContextTest extends AbstractIntegrationTest {

    @Autowired
    private DataSource dataSource;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Autowired
    private TestRestTemplate restTemplate;

    @Autowired
    private AnalysisService analysisService;

    @Autowired
    private PromptBuilder promptBuilder;

    @Test
    @DisplayName("the context starts with the JPA mappings validated against the migrated schema")
    void contextLoads() {
        assertThat(dataSource).isNotNull();
        assertThat(analysisService).isNotNull();
        assertThat(promptBuilder).isNotNull();
    }

    @Test
    @DisplayName("all migrations applied successfully")
    void migrationsApplied() {
        var rows = jdbcTemplate.queryForList(
                "select version, description, success from flyway_schema_history order by installed_rank");

        assertThat(rows).hasSizeGreaterThanOrEqualTo(2);
        assertThat(rows).allSatisfy(row -> assertThat(row.get("success")).isEqualTo(true));
        assertThat(rows).extracting(row -> row.get("version")).contains("1", "2");
    }

    @Test
    @DisplayName("the health endpoint reports the database as up")
    void healthEndpointIsUp() {
        String health = restTemplate.getForObject("/actuator/health", String.class);

        assertThat(health).contains("\"status\":\"UP\"");
    }

    @Test
    @DisplayName("the OpenAPI document lists the documented endpoints")
    void openApiIsPublished() {
        String openApi = restTemplate.getForObject("/v3/api-docs", String.class);

        assertThat(openApi)
                .contains("/api/processes")
                .contains("/api/processes/{id}/analyze")
                .contains("/api/processes/{id}/comparison")
                .contains("/api/knowledge-snippets");
    }
}
