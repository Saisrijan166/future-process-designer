package com.assesswise.processdesigner.service.ai;

import com.assesswise.processdesigner.config.AppProperties;
import com.assesswise.processdesigner.exception.AiNotConfiguredException;
import com.assesswise.processdesigner.exception.AiProviderException;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

/**
 * The single door every pipeline stage goes through to reach a model.
 *
 * <p>A stage says "do this job" — {@code complete(AiTask.DIAGNOSIS, request)} — and this class
 * decides everything else: which model, whether the answer is already known, whether there is
 * budget to ask right now, and what to do when the answer is no. Four concerns, deliberately in
 * one place rather than repeated in ten stages:
 *
 * <ol>
 *   <li><b>Routing.</b> {@link ModelRouter} supplies an ordered candidate list per task.
 *   <li><b>Caching.</b> Every candidate is checked against the persistent cache <em>before</em> any
 *       network call, so an unchanged re-run costs nothing at all.
 *   <li><b>Budgeting.</b> {@link TokenBudgetGovernor} either admits the call, makes it wait for the
 *       token bucket to refill, or refuses — in which case the next candidate model is tried
 *       instead of the run failing. This is what turns a free tier into something a ten-stage
 *       pipeline can actually run on.
 *   <li><b>Honesty.</b> Every substitution, wait and failure is recorded on the completion as a
 *       provider note, and those notes end up in the run trace. A run served by the third-choice
 *       model after two rate-limit refusals says so.
 * </ol>
 */
@Service
public class AiGateway {

    private static final Logger log = LoggerFactory.getLogger(AiGateway.class);

    private final ModelRouter router;
    private final AiProviderRegistry registry;
    private final TokenBudgetGovernor governor;
    private final AiResponseCache cache;
    private final Duration maxRateLimitWait;
    /**
     * Rotates the starting candidate for high-volume tasks, so a run's twenty claim extractions are
     * spread across providers instead of queueing behind one. This is the only throughput multiplier
     * that actually works on a free tier whose token ceiling is organisation-wide: a second provider
     * is a second quota.
     */
    private final java.util.concurrent.atomic.AtomicInteger rotation =
            new java.util.concurrent.atomic.AtomicInteger();

    public AiGateway(
            ModelRouter router,
            AiProviderRegistry registry,
            TokenBudgetGovernor governor,
            AiResponseCache cache,
            AppProperties properties) {
        this.router = router;
        this.registry = registry;
        this.governor = governor;
        this.cache = cache;
        this.maxRateLimitWait = Duration.ofSeconds(Math.max(0, properties.ai().maxRateLimitWaitSeconds()));
    }

    public boolean isConfigured() {
        return registry.anyConfigured();
    }

    public List<TokenBudgetGovernor.Snapshot> budgets() {
        return governor.snapshots();
    }

    public ModelRouter router() {
        return router;
    }

    /**
     * Runs one task, choosing and re-choosing where to run it until something answers.
     *
     * @throws AiNotConfiguredException when no provider has credentials
     * @throws AiProviderException when every candidate refused or failed
     */
    public AiCompletion complete(AiTask task, AiRequest request) {
        List<ModelRouter.Candidate> candidates = router.candidatesFor(task);
        if (candidates.isEmpty()) {
            throw new AiNotConfiguredException(
                    "No AI provider has an API key configured. Set GROQ_API_KEY (free, from "
                            + "https://console.groq.com/keys) or GEMINI_API_KEY on the backend and restart it.");
        }

        AiRequest prepared = applyTaskDefaults(task, request);
        List<String> notes = new ArrayList<>();

        // Pass one: is this answer already known? Checked across every candidate, because a cached
        // answer from the second-choice model is still free and still valid.
        if (prepared.cacheable() && cache.isEnabled()) {
            for (ModelRouter.Candidate candidate : candidates) {
                String cacheKey = cache.keyFor(task, candidate.provider().name(), candidate.model(), prepared);
                Optional<AiCompletion> hit = cache.lookup(cacheKey);
                if (hit.isPresent()) {
                    cache.recordHit(cacheKey);
                    log.info("{}: served from cache ({})", task.id(), candidate.key());
                    return withNotes(hit.get(), notes);
                }
            }
        }

        // Pass two: ask, in order, until one answers.
        List<ModelRouter.Candidate> ordered = task.spreadAcrossProviders()
                ? rotate(candidates, rotation.getAndIncrement())
                : candidates;

        for (int index = 0; index < ordered.size(); index++) {
            ModelRouter.Candidate candidate = ordered.get(index);
            boolean isLast = index == ordered.size() - 1;

            int estimatedTokens = Math.max(
                    task.budgetFloorTokens(),
                    prepared.estimatedPromptTokens()
                            + (prepared.maxOutputTokens() == null
                                    ? task.defaultMaxOutputTokens()
                                    : prepared.maxOutputTokens()));

            // The last candidate is asked to wait as long as it takes: there is nowhere left to
            // route to, so refusing on budget would fail a run that only needed patience.
            Duration wait = isLast ? maxRateLimitWait.plusSeconds(30) : maxRateLimitWait;
            TokenBudgetGovernor.Reservation reservation =
                    governor.reserve(candidate.provider().name(), candidate.model(), estimatedTokens, wait);

            if (!reservation.admitted()) {
                notes.add("%s skipped: %s".formatted(candidate.key(), reservation.reason()));
                log.info("{}: skipping {} — {}", task.id(), candidate.key(), reservation.reason());
                continue;
            }
            if (reservation.waitedMillis() > 250) {
                notes.add("%s waited %dms for its token budget".formatted(candidate.key(), reservation.waitedMillis()));
            }

            try {
                AiCompletion completion = call(candidate, prepared, notes);
                governor.observeUsage(candidate.provider().name(), candidate.model(),
                        completion.totalTokens(), estimatedTokens);

                if (prepared.cacheable() && cache.isEnabled()) {
                    cache.store(
                            cache.keyFor(task, candidate.provider().name(), candidate.model(), prepared),
                            task,
                            completion);
                }
                return withNotes(completion, notes);

            } catch (AiNotConfiguredException e) {
                notes.add("%s skipped: no API key".formatted(candidate.key()));
            } catch (AiProviderException e) {
                notes.add("%s failed: %s".formatted(candidate.key(), e.getMessage()));
                if (e.isRateLimited()) {
                    Duration cooldown = Duration.ofSeconds(e.getRetryAfterSeconds() > 0 ? e.getRetryAfterSeconds() : 20);
                    governor.penalise(candidate.provider().name(), candidate.model(), cooldown);
                }
                log.warn("{}: {} failed — {}", task.id(), candidate.key(), e.getMessage());
            }
        }

        throw new AiProviderException(
                "Every candidate model for '%s' refused or failed. %s".formatted(task.id(), String.join(" | ", notes)),
                false);
    }

    /**
     * Makes the call, with one narrow retry.
     *
     * <p>When a provider rejects its own JSON-mode output — which in practice means the response was
     * truncated at the token ceiling — the same model is asked again with JSON mode switched off.
     * That is worth doing rather than moving on, because this application parses and repairs JSON
     * itself and can usually salvage exactly what the provider chose to discard.
     */
    private AiCompletion call(ModelRouter.Candidate candidate, AiRequest request, List<String> notes) {
        try {
            return candidate.provider().complete(request.withModel(candidate.model()));
        } catch (AiProviderException e) {
            if (!e.isJsonModeRejected() || !request.enforceJsonSchema()) {
                throw e;
            }
            notes.add("%s rejected its own JSON-mode output; retried without it".formatted(candidate.key()));
            log.info("{}: retrying without JSON mode after the provider rejected its own output",
                    candidate.key());
            AiRequest relaxed = new AiRequest(
                    request.prompt(), request.systemPrompt(), request.purpose(), false,
                    request.responseSchema(), request.temperature(), request.maxOutputTokens(),
                    candidate.model(), request.reasoningEffort(), false);
            return candidate.provider().complete(relaxed);
        }
    }

    /** Starts the candidate list at a rotating offset, keeping the relative order intact. */
    private List<ModelRouter.Candidate> rotate(List<ModelRouter.Candidate> candidates, int offset) {
        if (candidates.size() < 2) {
            return candidates;
        }
        int start = Math.floorMod(offset, candidates.size());
        List<ModelRouter.Candidate> rotated = new ArrayList<>(candidates.size());
        for (int index = 0; index < candidates.size(); index++) {
            rotated.add(candidates.get((start + index) % candidates.size()));
        }
        return rotated;
    }

    /**
     * Fills in the per-task generation settings a caller did not specify. The output ceiling is the
     * important one: it is reserved against the tokens-per-minute allowance whether or not it gets
     * used, so a task asking for four times what it needs is spending budget it will not use.
     */
    private AiRequest applyTaskDefaults(AiTask task, AiRequest request) {
        Double temperature = request.temperature() == null ? task.defaultTemperature() : request.temperature();
        Integer maxOutputTokens =
                request.maxOutputTokens() == null ? task.defaultMaxOutputTokens() : request.maxOutputTokens();
        AiRequest prepared = request.withLimits(temperature, maxOutputTokens);
        return prepared.purpose() == null || prepared.purpose().isBlank()
                ? new AiRequest(prepared.prompt(), prepared.systemPrompt(), task.id(), prepared.enforceJsonSchema(),
                        prepared.responseSchema(), temperature, maxOutputTokens, prepared.model(),
                        prepared.reasoningEffort(), prepared.cacheable())
                : prepared;
    }

    private AiCompletion withNotes(AiCompletion completion, List<String> notes) {
        if (notes.isEmpty()) {
            return completion;
        }
        List<String> combined = new ArrayList<>(notes);
        combined.addAll(completion.providerNotes());
        return completion.withProviderNotes(combined);
    }
}
