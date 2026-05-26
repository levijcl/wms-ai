package com.wms.ai.outbound.internal;

import static org.assertj.core.api.Assertions.assertThat;

import com.wms.ai.outbound.OutboundService;
import com.wms.ai.outbound.Worker;
import com.wms.ai.outbound.WorkerStatus;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;

/**
 * Verifies the dev-profile {@code CommandLineRunner} seeds a sample worker pool on
 * startup. Runs in the {@code dev} profile with its own isolated in-memory DB so it
 * neither depends on nor pollutes the default-profile tests' shared database.
 */
@SpringBootTest(properties = "spring.datasource.url=jdbc:h2:mem:outboundseedtest;DB_CLOSE_DELAY=-1")
@ActiveProfiles("dev")
class OutboundSeedDataTest {

    @Autowired
    OutboundService service;

    @Autowired
    WorkerRepository repository;

    @Autowired
    OutboundSeedData seedData;

    @Test
    void seedsIdleWorkersWithMultipleInZoneOneForAffinity() {
        var idle = service.listWorkersByStatus(WorkerStatus.IDLE);

        assertThat(idle).isNotEmpty();
        assertThat(idle).filteredOn(w -> w.currentZone().equals("ZONE-1"))
                .extracting(Worker::id)
                .hasSizeGreaterThanOrEqualTo(2); // gives zone affinity a real choice
    }

    @Test
    void seedsAtLeastOneNonIdleWorkerToExerciseTheFilter() {
        assertThat(service.listWorkersByStatus(WorkerStatus.BUSY))
                .isNotEmpty();
        assertThat(service.listWorkersByStatus(WorkerStatus.OFFLINE))
                .isNotEmpty();
    }

    @Test
    void reRunningTheSeederDoesNotDuplicate() {
        long afterStartup = repository.count();

        seedData.run(); // simulate a restart

        assertThat(repository.count()).isEqualTo(afterStartup);
    }
}
