package com.drs.core.config;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.EnableScheduling;

/**
 * Turns on @Scheduled (the Exp 2 silent-node monitor).
 * Tests set {@code drs.scheduling.enabled=false} so the background monitor cannot
 * race with assertions.
 */
@Configuration
@EnableScheduling
@ConditionalOnProperty(name = "drs.scheduling.enabled", havingValue = "true", matchIfMissing = true)
public class SchedulingConfig {
}
