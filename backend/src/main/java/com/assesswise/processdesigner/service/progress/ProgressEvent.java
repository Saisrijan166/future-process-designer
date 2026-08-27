package com.assesswise.processdesigner.service.progress;

import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Something the pipeline wants the browser to know about while it is still working.
 *
 * <p>An analysis takes a minute or more: eleven search connectors, a dozen page fetches, ten model
 * calls, some of them waiting on a token bucket. A spinner for that long is indistinguishable from
 * a hang, and it hides the most interesting part of the application — the fact that it really is
 * searching the web, really is reading the pages, and really is checking the quotes.
 *
 * <p>So every meaningful step emits one of these, and the UI renders them as they arrive. The
 * payload is a loose map on purpose: each event type carries what that step actually learned, and
 * inventing a common schema across "connector answered with 6 results" and "quote verified" would
 * flatten both into something less useful.
 */
public record ProgressEvent(Type type, String stageId, String title, String message, Map<String, Object> data,
        Instant at) {

    public enum Type {
        /** A pipeline stage started. */
        STAGE_STARTED,
        /** A pipeline stage finished, with its summary in the payload. */
        STAGE_FINISHED,
        /** A stage produced usable output but not all of it. */
        STAGE_DEGRADED,
        /** A stage failed; the run may still continue. */
        STAGE_FAILED,
        /** A search query was planned. */
        QUERY_PLANNED,
        /** One connector answered one query. */
        SEARCH_RESULT,
        /** A page was fetched, or refused. */
        SOURCE_FETCHED,
        /** Claims were extracted from a source and their quotes checked. */
        CLAIMS_EXTRACTED,
        /** A model call happened, with its cost. */
        MODEL_CALL,
        /** The whole run finished. */
        RUN_FINISHED,
        /** A note worth showing that is not tied to a stage boundary. */
        NOTE
    }

    public static ProgressEvent of(Type type, String stageId, String title, String message) {
        return new ProgressEvent(type, stageId, title, message, Map.of(), Instant.now());
    }

    public static ProgressEvent of(
            Type type, String stageId, String title, String message, Map<String, Object> data) {
        return new ProgressEvent(type, stageId, title, message,
                data == null ? Map.of() : new LinkedHashMap<>(data), Instant.now());
    }
}
