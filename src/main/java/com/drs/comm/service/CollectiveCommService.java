package com.drs.comm.service;

import com.drs.core.event.EventPublisher;
import org.springframework.stereotype.Service;

/**
 * Backward compatibility subclass delegating to {@link CommService}.
 */
@Service
public class CollectiveCommService extends CommService {

    public CollectiveCommService(EventPublisher eventPublisher) {
        super(eventPublisher);
    }
}
