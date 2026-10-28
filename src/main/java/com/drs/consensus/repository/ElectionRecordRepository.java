package com.drs.consensus.repository;

import com.drs.consensus.model.ElectionRecord;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;

/**
 * Spring Data repository for {@link ElectionRecord} (Exp 4 election history).
 */
@Repository
public interface ElectionRecordRepository extends JpaRepository<ElectionRecord, Long> {

    /**
     * Returns the most recent N election records, newest first.
     * Uses a native query with LIMIT for H2 compatibility.
     */
    @Query(value = "SELECT * FROM election_record ORDER BY election_time DESC LIMIT :limit", nativeQuery = true)
    List<ElectionRecord> findTopNByOrderByTimestampDesc(@Param("limit") int limit);
}
