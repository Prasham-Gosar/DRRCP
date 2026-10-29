package com.drs.consensus.repository;

import com.drs.consensus.model.UpdateRecord;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

/**
 * Spring Data repository for {@link UpdateRecord}.
 */
@Repository
public interface UpdateRecordRepository extends JpaRepository<UpdateRecord, String> {

    List<UpdateRecord> findByResourceKey(String resourceKey);

    List<UpdateRecord> findByClusterId(String clusterId);

    List<UpdateRecord> findAllByOrderByRecordIdAsc();
}
