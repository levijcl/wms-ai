package com.wms.ai.outbound.internal;

import org.springframework.data.jpa.repository.JpaRepository;

/**
 * Spring Data repository for {@link PickingTaskEntity}. Package-private — hidden
 * behind {@code OutboundService} so an H2 → Postgres swap needs no caller changes.
 */
interface PickingTaskRepository extends JpaRepository<PickingTaskEntity, String> {}
