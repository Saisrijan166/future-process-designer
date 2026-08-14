package com.assesswise.processdesigner.service;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * A deliberately tiny template engine supporting {@code {{value}}} substitution and
 * {@code {{#each list}}…{{/each}}} blocks.
 *
 * <p>Prompts live in {@code src/main/resources/prompts/} as plain text so they can be reviewed and
 * changed without touching Java, which is why a renderer is needed at all. A full templating
 * dependency would be more capable than this project needs; the trade-off is that the supported
 * syntax is exactly these two constructs, and anything unsupported fails loudly at render time
 * rather than silently producing a malformed prompt.
 */
public class PromptTemplateRenderer {

    private static final Pattern EACH_BLOCK =
            Pattern.compile("\\{\\{#each\\s+([a-zA-Z0-9_]+)\\s*}}(.*?)\\{\\{/each}}", Pattern.DOTALL);
    private static final Pattern PLACEHOLDER = Pattern.compile("\\{\\{\\s*([a-zA-Z0-9_]+)\\s*}}");
    private static final Pattern UNRESOLVED_BLOCK = Pattern.compile("\\{\\{[#/]");
    private static final String EMPTY_LIST_MARKER = "(none)";

    /**
     * Renders {@code template} against {@code context}.
     *
     * @param context values keyed by placeholder name. A value used by an {@code #each} block must
     *     be a {@code List<Map<String, ?>>}; everything else is rendered with {@code toString()}.
     * @throws IllegalArgumentException if a block is malformed or an {@code #each} target is not a list
     */
    public String render(String template, Map<String, Object> context) {
        if (template == null) {
            throw new IllegalArgumentException("template must not be null");
        }
        String withBlocks = renderBlocks(template, context);
        String rendered = renderPlaceholders(withBlocks, context);

        Matcher leftover = UNRESOLVED_BLOCK.matcher(rendered);
        if (leftover.find()) {
            throw new IllegalArgumentException(
                    "Unclosed or unsupported template block near: " + snippetAround(rendered, leftover.start()));
        }
        return rendered;
    }

    private String renderBlocks(String template, Map<String, Object> context) {
        Matcher matcher = EACH_BLOCK.matcher(template);
        StringBuilder output = new StringBuilder();
        while (matcher.find()) {
            String listName = matcher.group(1);
            String body = trimBlockEdges(matcher.group(2));
            Object value = context.get(listName);

            if (value != null && !(value instanceof List<?>)) {
                throw new IllegalArgumentException(
                        "'" + listName + "' is used in an #each block but is not a list");
            }
            List<?> list = value == null ? List.of() : (List<?>) value;

            List<String> renderedItems = new ArrayList<>(list.size());
            for (Object item : list) {
                if (!(item instanceof Map<?, ?> itemMap)) {
                    throw new IllegalArgumentException(
                            "Items of '" + listName + "' must be maps, got " + item.getClass().getSimpleName());
                }
                renderedItems.add(renderPlaceholders(body, mergedContext(itemMap, context)));
            }
            // An empty list renders as an explicit marker: a silent blank section reads to the
            // model as a formatting glitch, whereas "(none)" is information.
            String replacement = renderedItems.isEmpty() ? EMPTY_LIST_MARKER : String.join("\n", renderedItems);
            matcher.appendReplacement(output, Matcher.quoteReplacement(replacement));
        }
        matcher.appendTail(output);
        return output.toString();
    }

    private String renderPlaceholders(String text, Map<String, ?> context) {
        Matcher matcher = PLACEHOLDER.matcher(text);
        StringBuilder output = new StringBuilder();
        while (matcher.find()) {
            Object value = context.get(matcher.group(1));
            matcher.appendReplacement(output, Matcher.quoteReplacement(value == null ? "" : value.toString()));
        }
        matcher.appendTail(output);
        return output.toString();
    }

    @SuppressWarnings("unchecked")
    private Map<String, Object> mergedContext(Map<?, ?> item, Map<String, Object> outer) {
        Map<String, Object> merged = new HashMap<>(outer);
        merged.putAll((Map<String, Object>) item);
        return merged;
    }

    /** Each-block bodies normally start and end with a newline; keep the list tidy. */
    private String trimBlockEdges(String rendered) {
        String result = rendered;
        if (result.startsWith("\n")) {
            result = result.substring(1);
        }
        if (result.endsWith("\n")) {
            result = result.substring(0, result.length() - 1);
        }
        return result;
    }

    private String snippetAround(String text, int index) {
        int start = Math.max(0, index - 30);
        int end = Math.min(text.length(), index + 30);
        return text.substring(start, end).replace("\n", "\\n");
    }
}
