package com.assesswise.processdesigner.service.ai;

/**
 * One call to a language model.
 *
 * @param prompt the fully rendered prompt text
 * @param purpose short label used for logging and the run audit trail (e.g. "analyze", "repair")
 * @param enforceJsonSchema whether the provider should constrain the response to the analysis
 *     schema server-side. This is an optimisation only — the response is validated locally either
 *     way, because a provider that silently ignores the hint must not corrupt the database.
 */
public record AiRequest(String prompt, String purpose, boolean enforceJsonSchema) {

    public static AiRequest of(String prompt, String purpose) {
        return new AiRequest(prompt, purpose, true);
    }
}
