package com.drs.core.model;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface NodeEntityRepository extends JpaRepository<NodeEntity, String> {
    List<NodeEntity> findByClusterId(String clusterId);
}

