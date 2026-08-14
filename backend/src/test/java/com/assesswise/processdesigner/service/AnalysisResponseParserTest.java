package com.assesswise.processdesigner.service;

import static org.assertj.core.api.Assertions.assertThat;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class AnalysisResponseParserTest {

    private final AnalysisResponseParser parser = new AnalysisResponseParser(new ObjectMapper());

    @Test
    @DisplayName("parses a clean JSON object")
    void parsesCleanJson() {
        String json = """
                {
                  "problems": [{"activity_name": "Grade answers", "description": "Slow", "severity": "HIGH"}],
                  "ai_opportunities": [],
                  "future_activities": [],
                  "ai_interventions": []
                }
                """;

        AnalysisResponseParser.ParseResult result = parser.parse(json);

        assertThat(result.isSuccess()).isTrue();
        assertThat(result.payload().problemsOrEmpty()).hasSize(1);
        assertThat(result.payload().problemsOrEmpty().getFirst().activityName()).isEqualTo("Grade answers");
    }

    @Test
    @DisplayName("strips markdown fences that models add despite being told not to")
    void stripsMarkdownFences() {
        String fenced = """
                ```json
                {"problems": [], "ai_opportunities": [], "future_activities": [], "ai_interventions": []}
                ```
                """;

        assertThat(parser.parse(fenced).isSuccess()).isTrue();
    }

    @Test
    @DisplayName("ignores commentary before and after the JSON object")
    void ignoresSurroundingProse() {
        String chatty = """
                Sure! Here is the analysis you asked for:
                {"problems": [], "ai_opportunities": [], "future_activities": [], "ai_interventions": []}
                Let me know if you would like any changes.
                """;

        assertThat(parser.parse(chatty).isSuccess()).isTrue();
    }

    @Test
    @DisplayName("does not truncate at a brace inside a string value")
    void handlesBracesInsideStrings() {
        String json = """
                {"problems": [{"description": "Template uses {placeholder} syntax", "severity": "LOW"}],
                 "ai_opportunities": [], "future_activities": [], "ai_interventions": []}
                """;

        AnalysisResponseParser.ParseResult result = parser.parse(json);

        assertThat(result.isSuccess()).isTrue();
        assertThat(result.payload().problemsOrEmpty().getFirst().description())
                .isEqualTo("Template uses {placeholder} syntax");
    }

    @Test
    @DisplayName("does not truncate at an escaped quote")
    void handlesEscapedQuotes() {
        String json =
                "{\"problems\": [{\"description\": \"He said \\\"too slow\\\" often\", \"severity\": \"LOW\"}],"
                        + "\"ai_opportunities\": [], \"future_activities\": [], \"ai_interventions\": []}";

        AnalysisResponseParser.ParseResult result = parser.parse(json);

        assertThat(result.isSuccess()).isTrue();
        assertThat(result.payload().problemsOrEmpty().getFirst().description())
                .isEqualTo("He said \"too slow\" often");
    }

    @Test
    @DisplayName("tolerates unknown keys rather than failing the whole run")
    void ignoresUnknownKeys() {
        String json = """
                {"problems": [], "ai_opportunities": [], "future_activities": [], "ai_interventions": [],
                 "commentary": "I added an extra field", "confidence": 0.9}
                """;

        assertThat(parser.parse(json).isSuccess()).isTrue();
    }

    @Test
    @DisplayName("reports a usable reason when there is no JSON at all")
    void reportsMissingJson() {
        AnalysisResponseParser.ParseResult result = parser.parse("I cannot help with that request.");

        assertThat(result.isSuccess()).isFalse();
        assertThat(result.error()).contains("No JSON object");
    }

    @Test
    @DisplayName("reports a usable reason when the JSON is malformed")
    void reportsMalformedJson() {
        AnalysisResponseParser.ParseResult result = parser.parse("{\"problems\": [ {\"severity\": HIGH} ] }");

        assertThat(result.isSuccess()).isFalse();
        assertThat(result.error()).contains("not valid JSON");
    }

    @Test
    @DisplayName("reports an empty response")
    void reportsEmptyResponse() {
        assertThat(parser.parse("   ").error()).contains("empty");
        assertThat(parser.parse(null).error()).contains("empty");
    }
}
