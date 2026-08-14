package com.assesswise.processdesigner.service.ai;

import static org.assertj.core.api.Assertions.assertThat;

import com.assesswise.processdesigner.domain.AutomationPotential;
import com.assesswise.processdesigner.domain.InterventionType;
import com.assesswise.processdesigner.domain.ResponsibilityType;
import com.assesswise.processdesigner.domain.Severity;
import com.fasterxml.jackson.databind.JsonNode;
import java.util.List;
import java.util.stream.StreamSupport;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class AnalysisJsonSchemaTest {

    private final JsonNode schema = AnalysisJsonSchema.build();

    private static List<String> values(JsonNode array) {
        return StreamSupport.stream(array.spliterator(), false).map(JsonNode::asText).toList();
    }

    @Test
    @DisplayName("declares the four top-level collections the pipeline persists")
    void declaresTopLevelShape() {
        assertThat(schema.path("type").asText()).isEqualTo("OBJECT");
        assertThat(values(schema.path("required")))
                .containsExactly("problems", "ai_opportunities", "future_activities", "ai_interventions");
        assertThat(schema.path("properties").path("problems").path("type").asText()).isEqualTo("ARRAY");
    }

    @Test
    @DisplayName("enum values are derived from the domain, so the schema cannot drift from the database")
    void enumsMatchTheDomain() {
        assertThat(values(schema.at("/properties/problems/items/properties/severity/enum")))
                .containsExactlyElementsOf(java.util.Arrays.stream(Severity.values()).map(Enum::name).toList());
        assertThat(values(schema.at("/properties/ai_opportunities/items/properties/automation_potential/enum")))
                .containsExactlyElementsOf(
                        java.util.Arrays.stream(AutomationPotential.values()).map(Enum::name).toList());
        assertThat(values(schema.at("/properties/future_activities/items/properties/responsibility_type/enum")))
                .containsExactlyElementsOf(
                        java.util.Arrays.stream(ResponsibilityType.values()).map(Enum::name).toList());
        assertThat(values(schema.at("/properties/ai_interventions/items/properties/intervention_type/enum")))
                .containsExactlyElementsOf(
                        java.util.Arrays.stream(InterventionType.values()).map(Enum::name).toList());
    }

    @Test
    @DisplayName("supporting_snippet_titles is an array of strings so citations can be resolved")
    void citationsAreAnArray() {
        JsonNode titles = schema.at("/properties/ai_opportunities/items/properties/supporting_snippet_titles");

        assertThat(titles.path("type").asText()).isEqualTo("ARRAY");
        assertThat(titles.at("/items/type").asText()).isEqualTo("STRING");
    }
}
