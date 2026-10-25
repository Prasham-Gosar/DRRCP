package com.drs.core.event;

import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

import java.util.List;

public interface EventLogRepository extends JpaRepository<EventLog, Long> {

    /** Most-recent-first, limited by Pageable. */
    @Query("SELECT e FROM EventLog e ORDER BY e.id DESC")
    List<EventLog> findRecentEvents(Pageable pageable);
}
