package com.drs.compute.repository;

import com.drs.compute.model.RescueTaskEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

/**
 * Spring Data JPA repository for {@link RescueTaskEntity}.
 */
@Repository
public interface RescueTaskRepository extends JpaRepository<RescueTaskEntity, String> {

    List<RescueTaskEntity> findByStatus(String status);

    List<RescueTaskEntity> findByClusterId(String clusterId);

    List<RescueTaskEntity> findByAssignedNodeId(String assignedNodeId);

    List<RescueTaskEntity> findByType(String type);
}
