package com.drs.compute.repository;

import com.drs.compute.model.AnalyticsRun;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

/**
 * Spring Data JPA repository for {@link AnalyticsRun}.
 */
@Repository
public interface AnalyticsRunRepository extends JpaRepository<AnalyticsRun, Long> {

    /**
     * Retrieve all historical runs ordered with the most recent first.
     */
    List<AnalyticsRun> findAllByOrderByTimestampDesc();

    /**
     * Retrieve the most recent analytics run.
     */
    Optional<AnalyticsRun> findTopByOrderByTimestampDesc();
}
