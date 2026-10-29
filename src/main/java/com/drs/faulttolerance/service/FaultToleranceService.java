package com.drs.faulttolerance.service;

import com.drs.core.event.EventPublisher;
import org.springframework.stereotype.Service;

/**
 * Primary-Backup Fault Tolerance Service.
 * <p>
 * Extends {@link ReplicationService} to preserve compatibility across existing controllers
 * and test harnesses while holding {@code PrimaryCoordinator} and {@code BackupReplica}.
 */
@Service
public class FaultToleranceService extends ReplicationService {

    public FaultToleranceService(EventPublisher eventPublisher) {
        super(eventPublisher);
    }
}
