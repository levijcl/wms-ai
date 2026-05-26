package com.wms.ai.outbound.internal;

import com.wms.ai.outbound.WorkerStatus;
import java.util.List;
import org.springframework.boot.CommandLineRunner;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;

/**
 * Seeds a sample worker pool so dispatch experiments have labour to assign —
 * several {@code IDLE} workers with <strong>multiple in {@code ZONE-1}</strong> (so the
 * README §8 zone-affinity criterion has a real choice), plus a {@code BUSY} and an
 * {@code OFFLINE} worker so the IDLE filter has something to exclude. The
 * {@code picking_task} table starts empty — tasks are created at runtime by dispatch.
 *
 * <p>Scoped to the {@code dev} profile and idempotent — it never runs under the
 * default test profile and never duplicates rows on restart.
 */
@Component
@Profile("dev")
class OutboundSeedData implements CommandLineRunner {

    private final WorkerRepository repository;

    OutboundSeedData(WorkerRepository repository) {
        this.repository = repository;
    }

    @Override
    public void run(String... args) {
        if (repository.count() > 0) {
            return; // already seeded — keep restarts idempotent
        }
        repository.saveAll(List.of(
                new WorkerEntity("WK-1", "Alice", "ZONE-1", WorkerStatus.IDLE),
                new WorkerEntity("WK-2", "Bob", "ZONE-2", WorkerStatus.IDLE),
                new WorkerEntity("WK-3", "Carol", "ZONE-1", WorkerStatus.IDLE), // 2nd ZONE-1 idle: affinity choice
                new WorkerEntity("WK-4", "Dave", "ZONE-3", WorkerStatus.IDLE),
                new WorkerEntity("WK-5", "Erin", "ZONE-2", WorkerStatus.BUSY), // excluded by the IDLE filter
                new WorkerEntity("WK-6", "Frank", "ZONE-3", WorkerStatus.OFFLINE)));
    }
}
