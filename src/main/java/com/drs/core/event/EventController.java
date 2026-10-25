package com.drs.core.event;

import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.util.List;

/**
 * SSE endpoint for live events + history query.
 */
@RestController
@RequestMapping("/api/events")
public class EventController {

    private final EventPublisher publisher;

    public EventController(EventPublisher publisher) {
        this.publisher = publisher;
    }

    /** SSE stream — the browser keeps this connection open. */
    @GetMapping(produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    public SseEmitter stream() {
        return publisher.subscribe();
    }

    /** Fetch recent events (newest first). Default limit = 50. */
    @GetMapping("/history")
    public List<EventLog> history(@RequestParam(defaultValue = "50") int limit) {
        return publisher.history(limit);
    }
}
