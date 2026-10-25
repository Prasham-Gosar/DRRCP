package com.drs.core.event;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.io.IOException;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;

/**
 * Central event bus used by every module.
 * <p>
 * Usage: {@code eventPublisher.publish("coordinator", "NODE_REGISTERED", someDto);}
 * <p>
 * Each call:
 * <ol>
 *   <li>Serialises the payload to JSON</li>
 *   <li>Persists an {@link EventLog} row</li>
 *   <li>Pushes the event to every active SSE subscriber</li>
 * </ol>
 */
@Service
public class EventPublisher {

    private static final Logger log = LoggerFactory.getLogger(EventPublisher.class);

    private final EventLogRepository repo;
    private final ObjectMapper mapper;
    private final List<SseEmitter> emitters = new CopyOnWriteArrayList<>();

    public EventPublisher(EventLogRepository repo, ObjectMapper mapper) {
        this.repo = repo;
        this.mapper = mapper;
    }

    // ── publish ──

    /**
     * Publish an event: persist it and push it live.
     *
     * @param module  originating module (e.g. "coordinator")
     * @param type    event type (e.g. "NODE_REGISTERED")
     * @param payload any serialisable object — will be converted to JSON
     */
    public void publish(String module, String type, Object payload) {
        try {
            String json = mapper.writeValueAsString(payload);
            EventLog entry = new EventLog(module, type, json);
            repo.save(entry);

            SseEvent sse = new SseEvent(entry.getId(), module, type, json, entry.getTimestamp().toString());
            String sseJson = mapper.writeValueAsString(sse);

            for (SseEmitter emitter : emitters) {
                try {
                    // No .name(...) on purpose: unnamed events arrive in the browser's
                    // EventSource.onmessage. module/type are inside the JSON body.
                    emitter.send(SseEmitter.event().data(sseJson));
                } catch (IOException e) {
                    emitters.remove(emitter);
                }
            }
            log.debug("Published {}.{}", module, type);
        } catch (Exception e) {
            log.error("Failed to publish event {}.{}: {}", module, type, e.getMessage());
        }
    }

    /** Number of browsers currently connected to /api/events. */
    public int subscriberCount() {
        return emitters.size();
    }

    // ── SSE registration ──

    public SseEmitter subscribe() {
        SseEmitter emitter = new SseEmitter(Long.MAX_VALUE);  // keep-alive forever
        emitters.add(emitter);
        try {
            emitter.send(SseEmitter.event().comment("connected"));
        } catch (IOException e) {
            emitters.remove(emitter);
        }
        emitter.onCompletion(() -> emitters.remove(emitter));
        emitter.onTimeout(() -> emitters.remove(emitter));
        emitter.onError(e -> emitters.remove(emitter));
        return emitter;
    }

    // ── history ──

    public List<EventLog> history(int limit) {
        int safeLimit = Math.max(1, Math.min(limit, 500));
        return repo.findRecentEvents(PageRequest.of(0, safeLimit));
    }

    // ── SSE payload DTO ──

    public record SseEvent(Long id, String module, String type, String payload, String timestamp) { }
}
