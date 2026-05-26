package com.wms.ai.outbound.internal;

import com.wms.ai.outbound.WorkerStatus;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;

/**
 * Spring Data repository for {@link WorkerEntity}. Package-private — hidden behind
 * {@code OutboundService} so an H2 → Postgres swap needs no caller changes.
 */
interface WorkerRepository extends JpaRepository<WorkerEntity, String> {

    /** All workers currently in the given status (derived query). */
    List<WorkerEntity> findByStatus(WorkerStatus status);
}
