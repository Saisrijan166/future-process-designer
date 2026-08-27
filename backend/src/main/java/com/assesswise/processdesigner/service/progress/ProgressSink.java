package com.assesswise.processdesigner.service.progress;

import java.util.Map;

/**
 * Where progress events go.
 *
 * <p>One method, and a no-op default, so that the pipeline can be instrumented without becoming
 * dependent on anyone listening. The same code path runs whether a browser is watching a live
 * stream, a test is running it headless, or a future scheduled job invokes it with nobody there —
 * the alternative, branching on whether progress reporting is wanted, would mean the streamed run
 * and the plain run are not the same run.
 */
public interface ProgressSink {

    ProgressSink NONE = event -> {};

    void emit(ProgressEvent event);

    default void note(String stageId, String message) {
        emit(ProgressEvent.of(ProgressEvent.Type.NOTE, stageId, null, message));
    }

    default void emit(ProgressEvent.Type type, String stageId, String title, String message) {
        emit(ProgressEvent.of(type, stageId, title, message));
    }

    default void emit(
            ProgressEvent.Type type, String stageId, String title, String message, Map<String, Object> data) {
        emit(ProgressEvent.of(type, stageId, title, message, data));
    }
}
