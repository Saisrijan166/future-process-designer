package com.assesswise.processdesigner.service.pipeline;

import com.assesswise.processdesigner.domain.StageStatus;
import com.assesswise.processdesigner.service.ai.AiCompletion;
import java.util.List;

/**
 * What one stage did, in the form the audit trail stores.
 *
 * <p>Every field here becomes a column on {@code analysis_stage}, which is how the application can
 * answer "why is this analysis thin?" with a specific stage rather than a shrug. The token counts
 * and the {@code cached} flag matter for a second reason: on a free tier, knowing which stage spent
 * the quota is operationally useful, not decorative.
 *
 * @param waitedMs time spent waiting for rate-limit budget rather than for the model to think —
 *     separated because the two have completely different remedies
 */
public record StageResult(
        StageStatus status,
        String summary,
        String prompt,
        String response,
        String provider,
        String model,
        Integer promptTokens,
        Integer outputTokens,
        long durationMs,
        long waitedMs,
        boolean cached,
        int attempts,
        List<String> notes,
        String error) {

    public StageResult {
        notes = notes == null ? List.of() : List.copyOf(notes);
    }

    public boolean isUsable() {
        return status == StageStatus.SUCCEEDED || status == StageStatus.DEGRADED;
    }

    public static StageResult succeeded(String summary) {
        return new StageResult(StageStatus.SUCCEEDED, summary, null, null, null, null, null, null,
                0, 0, false, 1, List.of(), null);
    }

    public static StageResult skipped(String summary) {
        return new StageResult(StageStatus.SKIPPED, summary, null, null, null, null, null, null,
                0, 0, false, 0, List.of(), null);
    }

    public static StageResult failed(String summary, String error) {
        return new StageResult(StageStatus.FAILED, summary, null, null, null, null, null, null,
                0, 0, false, 1, List.of(), error);
    }

    /** Attaches the cost and provenance of the model call that produced this stage. */
    public StageResult withCall(String prompt, AiCompletion completion, int attempts) {
        return new StageResult(status, summary, prompt, completion == null ? null : completion.text(),
                completion == null ? null : completion.provider(),
                completion == null ? null : completion.model(),
                completion == null ? null : completion.promptTokens(),
                completion == null ? null : completion.outputTokens(),
                completion == null ? durationMs : completion.durationMs(),
                waitedMs,
                completion != null && completion.cached(),
                attempts,
                completion == null ? notes : merge(notes, completion.providerNotes()),
                error);
    }

    public StageResult withStatus(StageStatus newStatus) {
        return new StageResult(newStatus, summary, prompt, response, provider, model, promptTokens,
                outputTokens, durationMs, waitedMs, cached, attempts, notes, error);
    }

    public StageResult withNotes(List<String> extra) {
        return new StageResult(status, summary, prompt, response, provider, model, promptTokens,
                outputTokens, durationMs, waitedMs, cached, attempts, merge(notes, extra), error);
    }

    public StageResult withSummary(String newSummary) {
        return new StageResult(status, newSummary, prompt, response, provider, model, promptTokens,
                outputTokens, durationMs, waitedMs, cached, attempts, notes, error);
    }

    private static List<String> merge(List<String> first, List<String> second) {
        if (second == null || second.isEmpty()) {
            return first;
        }
        List<String> merged = new java.util.ArrayList<>(first);
        merged.addAll(second);
        return List.copyOf(merged);
    }
}
