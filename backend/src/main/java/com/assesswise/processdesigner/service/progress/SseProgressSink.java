package com.assesswise.processdesigner.service.progress;

import com.fasterxml.jackson.databind.ObjectMapper;
import java.io.IOException;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.concurrent.atomic.AtomicBoolean;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

/**
 * Pushes progress events to one browser over Server-Sent Events.
 *
 * <p>SSE rather than WebSockets because the traffic is entirely one-way and the free hosting tier
 * handles a plain HTTP response better than a socket upgrade. The browser side reads it with
 * {@code fetch} rather than {@code EventSource}, for a security reason worth stating: EventSource
 * cannot send an Authorization header, and the workaround — putting the session token in the query
 * string — puts it in every access log between here and the user.
 *
 * <p>The sink goes silent the moment the client disconnects. A user who closes the tab mid-analysis
 * should not cause a failure in the pipeline: the analysis continues to completion and is saved,
 * because the work has already been paid for out of the free-tier quota and throwing it away would
 * be the worst possible response to somebody navigating away.
 */
public class SseProgressSink implements ProgressSink {

    private static final Logger log = LoggerFactory.getLogger(SseProgressSink.class);

    private final SseEmitter emitter;
    private final ObjectMapper objectMapper;
    private final AtomicBoolean open = new AtomicBoolean(true);

    public SseProgressSink(SseEmitter emitter, ObjectMapper objectMapper) {
        this.emitter = emitter;
        this.objectMapper = objectMapper;
        emitter.onCompletion(() -> open.set(false));
        emitter.onTimeout(() -> open.set(false));
        emitter.onError(throwable -> open.set(false));
    }

    @Override
    public void emit(ProgressEvent event) {
        if (!open.get()) {
            return;
        }
        try {
            Map<String, Object> payload = new LinkedHashMap<>();
            payload.put("type", event.type().name());
            payload.put("stageId", event.stageId());
            payload.put("title", event.title());
            payload.put("message", event.message());
            payload.put("at", event.at().toString());
            payload.put("data", event.data());

            emitter.send(SseEmitter.event()
                    .name("progress")
                    .data(objectMapper.writeValueAsString(payload), org.springframework.http.MediaType.APPLICATION_JSON));

        } catch (IOException | IllegalStateException e) {
            // The client went away. Expected, and not the pipeline's problem.
            open.set(false);
            log.debug("Progress stream closed by the client: {}", e.getMessage());
        } catch (RuntimeException e) {
            open.set(false);
            log.warn("Could not write to the progress stream: {}", e.getMessage());
        }
    }

    /** Sends the terminal event and closes the stream. */
    public void complete(String eventName, Object payload) {
        if (!open.get()) {
            return;
        }
        try {
            emitter.send(SseEmitter.event()
                    .name(eventName)
                    .data(objectMapper.writeValueAsString(payload),
                            org.springframework.http.MediaType.APPLICATION_JSON));
            emitter.complete();
        } catch (Exception e) {
            log.debug("Could not send the final progress event: {}", e.getMessage());
        } finally {
            open.set(false);
        }
    }

    public void completeExceptionally(Throwable throwable) {
        if (!open.get()) {
            return;
        }
        try {
            emitter.completeWithError(throwable);
        } catch (RuntimeException e) {
            log.debug("Could not close the progress stream after a failure: {}", e.getMessage());
        } finally {
            open.set(false);
        }
    }

    public boolean isOpen() {
        return open.get();
    }
}
