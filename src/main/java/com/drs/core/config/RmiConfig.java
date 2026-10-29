package com.drs.core.config;

import com.drs.core.rmi.NodeService;
import com.drs.core.rmi.NodeServiceImpl;
import com.drs.core.service.CoordinatorService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.rmi.registry.LocateRegistry;
import java.rmi.registry.Registry;

@Configuration
@ConditionalOnProperty(name = "drs.rmi.enabled", havingValue = "true", matchIfMissing = true)
public class RmiConfig {

    private static final Logger log = LoggerFactory.getLogger(RmiConfig.class);
    private static final int RMI_PORT = 1099;

    @Bean
    public Registry rmiRegistry(CoordinatorService coordinatorService) throws Exception {
        Registry registry;
        try {
            registry = LocateRegistry.createRegistry(RMI_PORT);
            log.info("RMI registry created on port {}", RMI_PORT);
        } catch (Exception e) {
            registry = LocateRegistry.getRegistry(RMI_PORT);
            log.info("RMI registry already running on port {}, reusing it", RMI_PORT);
        }

        NodeService impl = new NodeServiceImpl(coordinatorService);
        registry.rebind("Coordinator", impl);
        log.info("RMI service 'Coordinator' bound on port {}", RMI_PORT);

        return registry;
    }
}