package com.assesswise.processdesigner.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class PromptTemplateRendererTest {

    private final PromptTemplateRenderer renderer = new PromptTemplateRenderer();

    @Test
    @DisplayName("substitutes scalar placeholders")
    void substitutesScalars() {
        String rendered = renderer.render("PROCESS: {{name}} ({{ industry }})",
                Map.of("name", "Grading", "industry", "Education"));

        assertThat(rendered).isEqualTo("PROCESS: Grading (Education)");
    }

    @Test
    @DisplayName("renders a missing value as empty rather than leaving the placeholder in the prompt")
    void rendersMissingValuesAsEmpty() {
        assertThat(renderer.render("[{{absent}}]", Map.of())).isEqualTo("[]");
    }

    @Test
    @DisplayName("renders each blocks one item per line")
    void rendersEachBlocks() {
        String template = """
                ACTIVITIES:
                {{#each activities}}
                - {{order}}. {{name}}
                {{/each}}
                END""";

        String rendered = renderer.render(template, Map.of("activities", List.of(
                Map.of("order", 1, "name", "Author questions"),
                Map.of("order", 2, "name", "Review paper"))));

        assertThat(rendered).isEqualTo("""
                ACTIVITIES:
                - 1. Author questions
                - 2. Review paper
                END""");
    }

    @Test
    @DisplayName("marks an empty list explicitly instead of leaving a blank section")
    void marksEmptyLists() {
        String rendered = renderer.render("PROBLEMS:\n{{#each problems}}\n- {{description}}\n{{/each}}",
                Map.of("problems", List.of()));

        assertThat(rendered).isEqualTo("PROBLEMS:\n(none)");
    }

    @Test
    @DisplayName("item values win over outer context of the same name")
    void itemValuesShadowOuterContext() {
        String rendered = renderer.render("{{#each rows}}\n{{name}}\n{{/each}}",
                Map.of("name", "outer", "rows", List.of(Map.of("name", "inner"))));

        assertThat(rendered).isEqualTo("inner");
    }

    @Test
    @DisplayName("fails loudly on an unclosed block instead of sending a broken prompt")
    void rejectsUnclosedBlock() {
        assertThatThrownBy(() -> renderer.render("{{#each rows}}\n- {{x}}\n", Map.of("rows", List.of())))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Unclosed");
    }

    @Test
    @DisplayName("fails when an each target is not a list")
    void rejectsNonListEachTarget() {
        assertThatThrownBy(() -> renderer.render("{{#each rows}}x{{/each}}", Map.of("rows", "not a list")))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("not a list");
    }
}
