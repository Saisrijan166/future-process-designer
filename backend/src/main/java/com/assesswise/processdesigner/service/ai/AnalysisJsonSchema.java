package com.assesswise.processdesigner.service.ai;

import com.assesswise.processdesigner.domain.AutomationPotential;
import com.assesswise.processdesigner.domain.InterventionType;
import com.assesswise.processdesigner.domain.ResponsibilityType;
import com.assesswise.processdesigner.domain.Severity;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.JsonNodeFactory;
import com.fasterxml.jackson.databind.node.ObjectNode;
import java.util.List;
import java.util.stream.Stream;

/**
 * Builds the response schema handed to the provider, derived from the domain enums so the
 * allowed values can never drift away from what the database will accept.
 */
public final class AnalysisJsonSchema {

    private static final JsonNodeFactory NODES = JsonNodeFactory.instance;

    private AnalysisJsonSchema() {}

    public static JsonNode build() {
        ObjectNode root = object("problems", "ai_opportunities", "future_activities", "ai_interventions");
        ObjectNode properties = (ObjectNode) root.get("properties");

        properties.set("problems", array(problemSchema()));
        properties.set("ai_opportunities", array(opportunitySchema()));
        properties.set("future_activities", array(futureActivitySchema()));
        properties.set("ai_interventions", array(interventionSchema()));

        root.set("required", strings("problems", "ai_opportunities", "future_activities", "ai_interventions"));
        return root;
    }

    private static ObjectNode problemSchema() {
        ObjectNode node = object("activity_name", "description", "severity");
        ObjectNode props = (ObjectNode) node.get("properties");
        props.set("activity_name", string("Exact name of the current activity this problem belongs to, or an empty string if it spans the whole process."));
        props.set("description", string("The pain point, stated concretely."));
        props.set("severity", enumeration(names(Severity.values())));
        node.set("required", strings("description", "severity"));
        return node;
    }

    private static ObjectNode opportunitySchema() {
        ObjectNode node = object(
                "activity_name",
                "description",
                "ai_capability",
                "automation_potential",
                "business_benefit",
                "risk",
                "reasoning_note",
                "supporting_snippet_titles");
        ObjectNode props = (ObjectNode) node.get("properties");
        props.set("activity_name", string("Exact name of the current activity this opportunity targets, or an empty string."));
        props.set("description", string("What AI would do here."));
        props.set("ai_capability", string("The concrete capability, e.g. 'item difficulty prediction' or 'retrieval-augmented answer drafting'."));
        props.set("automation_potential", enumeration(names(AutomationPotential.values())));
        props.set("business_benefit", string("The measurable benefit to the organisation."));
        props.set("risk", string("The main risk or failure mode of applying AI here."));
        props.set("reasoning_note", string("Why this follows from the stated activities and problems."));
        props.set("supporting_snippet_titles", array(string("Exact title of a supplied research snippet that supports this opportunity.")));
        node.set("required", strings("description", "ai_capability", "automation_potential"));
        return node;
    }

    private static ObjectNode futureActivitySchema() {
        ObjectNode node = object(
                "sequence_order", "name", "description", "human_responsibility", "ai_responsibility", "responsibility_type");
        ObjectNode props = (ObjectNode) node.get("properties");
        props.set("sequence_order", integer("1-based position in the redesigned process."));
        props.set("name", string("Name of the future-state step."));
        props.set("description", string("What happens in this step."));
        props.set("human_responsibility", string("What a person is accountable for in this step."));
        props.set("ai_responsibility", string("What the AI does in this step; empty if none."));
        props.set("responsibility_type", enumeration(names(ResponsibilityType.values())));
        node.set("required", strings("sequence_order", "name", "responsibility_type"));
        return node;
    }

    private static ObjectNode interventionSchema() {
        ObjectNode node = object(
                "future_activity_name", "related_ai_opportunity_description", "intervention_type", "description");
        ObjectNode props = (ObjectNode) node.get("properties");
        props.set("future_activity_name", string("Exact name of the future activity this intervention changes."));
        props.set("related_ai_opportunity_description", string("Exact description of the AI opportunity that justifies it."));
        props.set("intervention_type", enumeration(names(InterventionType.values())));
        props.set("description", string("What specifically changed relative to the current process."));
        node.set("required", strings("future_activity_name", "intervention_type", "description"));
        return node;
    }

    private static ObjectNode object(String... propertyOrder) {
        ObjectNode node = NODES.objectNode();
        node.put("type", "OBJECT");
        node.set("properties", NODES.objectNode());
        node.set("propertyOrdering", strings(propertyOrder));
        return node;
    }

    private static ObjectNode array(JsonNode items) {
        ObjectNode node = NODES.objectNode();
        node.put("type", "ARRAY");
        node.set("items", items);
        return node;
    }

    private static ObjectNode string(String description) {
        ObjectNode node = NODES.objectNode();
        node.put("type", "STRING");
        node.put("description", description);
        return node;
    }

    private static ObjectNode integer(String description) {
        ObjectNode node = NODES.objectNode();
        node.put("type", "INTEGER");
        node.put("description", description);
        return node;
    }

    private static ObjectNode enumeration(List<String> values) {
        ObjectNode node = NODES.objectNode();
        node.put("type", "STRING");
        node.set("enum", strings(values.toArray(String[]::new)));
        return node;
    }

    private static ArrayNode strings(String... values) {
        ArrayNode array = NODES.arrayNode();
        Stream.of(values).forEach(array::add);
        return array;
    }

    private static List<String> names(Enum<?>[] values) {
        return Stream.of(values).map(Enum::name).toList();
    }
}
