package com.drs.core.model;

import org.springframework.data.jpa.repository.JpaRepository;
import java.util.List;

public interface NodeStatusRepository extends JpaRepository<NodeStatus, String> {

    List<NodeStatus> findByClusterId(String clusterId);

    List<NodeStatus> findByState(String state);
}
